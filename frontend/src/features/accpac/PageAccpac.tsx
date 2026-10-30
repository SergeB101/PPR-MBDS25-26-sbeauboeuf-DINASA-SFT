import { useState } from "react"
import { useSession } from "@/auth/session"
import { ANOMALIES, correctionsPossibles, useTableauAnomalies } from "./api"
import type { Bilan, CodeEnDouble, TypeAnomalie } from "./api"
import { DialogueAnnulation, DialogueCorrectionCode, DialogueRecuperation } from "./Dialogues"
import { messageErreur } from "@/lib/api"
import { date, nombre, NOMS_MOIS } from "@/lib/format"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { NativeSelect } from "@/components/ui/native-select"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"

function moisPrecedent() {
    const d = new Date()
    d.setDate(1)
    d.setMonth(d.getMonth() - 1)
    return { annee: d.getFullYear(), mois: d.getMonth() + 1 }
}

function TableDoubles({ titre, description, codes, libelleDifferent }: {
    titre: string; description: string; codes: CodeEnDouble[]; libelleDifferent: string
}) {
    return (
        <Card>
            <CardHeader>
                <CardTitle>{titre} ({codes.length})</CardTitle>
                <CardDescription>{description}</CardDescription>
            </CardHeader>
            <CardContent>
                {codes.length === 0 ? <p className="text-sm text-muted-foreground">Aucun code en double.</p> : (
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHead>Code</TableHead>
                                <TableHead className="text-right">Fiches</TableHead>
                                <TableHead>Détail des fiches</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {[...codes].sort((a, b) => Number(b.valeursDifferentes) - Number(a.valeursDifferentes)).map((c) => (
                                <TableRow key={c.code}>
                                    <TableCell className="font-mono">
                                        {c.code} {c.valeursDifferentes && <Badge variant="destructive">{libelleDifferent}</Badge>}
                                    </TableCell>
                                    <TableCell className="text-right">{c.fiches}</TableCell>
                                    <TableCell className="whitespace-normal text-xs">{c.detail}</TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                )}
            </CardContent>
        </Card>
    )
}

export default function PageAccpac() {
    const depart = moisPrecedent()
    const [annee, setAnnee] = useState(depart.annee)
    const [mois, setMois] = useState(depart.mois)
    const [inclurePrecedent, setInclurePrecedent] = useState(false)
    const [filtre, setFiltre] = useState<TypeAnomalie | "">("")
    const [bilan, setBilan] = useState<Bilan | null>(null)
    const [message, setMessage] = useState<string | null>(null)

    const { peut } = useSession()
    const tableau = useTableauAnomalies(annee, mois, inclurePrecedent)
    const t = tableau.data
    const annees = Array.from({ length: new Date().getFullYear() - 2019 }, (_, i) => new Date().getFullYear() - i)

    const lignes = (t?.lignes ?? []).filter((l) => !filtre || l.anomalies.includes(filtre))
    const bloquantes = (t?.lignes ?? []).filter((l) => l.anomalies.some((a) => ANOMALIES[a].bloquante)).length

    const changerPeriode = (a: number, m: number) => {
        setAnnee(a)
        setMois(m)
        setFiltre("")
        setBilan(null)
        setMessage(null)
    }

    return (
        <div className="grid gap-4">
            {/* En-tête */}
            <div className="flex flex-wrap items-center justify-between gap-4">
                <h1 className="text-2xl font-semibold">ACCPAC — récupération des bons</h1>
                <div className="flex flex-wrap items-center gap-2">
                    <NativeSelect value={mois} aria-label="Mois" onChange={(e) => changerPeriode(annee, Number(e.target.value))}>
                        {NOMS_MOIS.map((nom, i) => <option key={nom} value={i + 1}>{nom}</option>)}
                    </NativeSelect>
                    <NativeSelect value={annee} aria-label="Année" onChange={(e) => changerPeriode(Number(e.target.value), mois)}>
                        {annees.map((a) => <option key={a} value={a}>{a}</option>)}
                    </NativeSelect>
                    <label className="flex items-center gap-2 text-sm">
                        <input type="checkbox" className="size-4 accent-primary" checked={inclurePrecedent}
                               onChange={(e) => setInclurePrecedent(e.target.checked)} />
                        Inclure le mois précédent
                    </label>
                    <Button variant="outline" onClick={() => tableau.refetch()} disabled={tableau.isFetching}>
                        {tableau.isFetching ? "Contrôle…" : "Recontrôler"}
                    </Button>
                </div>
            </div>

            {/* Messages après une action */}
            {bilan && (
                <Alert>
                    <AlertTitle>Récupération terminée : {nombre(bilan.bonsCrees)} bon(s) créé(s)</AlertTitle>
                    <AlertDescription>
            <span>
              Dont {nombre(bilan.bonsOT)} bon(s) OT · {nombre(bilan.lignesMarquees)} ligne(s) ACCPAC traitée(s) ·
                {" "}{nombre(bilan.bonsAvecClientFacture)} bon(s) avec client facturé (chargement partagé).
            </span>
                        {Object.keys(bilan.anomaliesRestantes).length > 0 && (
                            <span>Restent en anomalie : {Object.entries(bilan.anomaliesRestantes)
                                .map(([type, nb]) => `${ANOMALIES[type as TypeAnomalie]?.libelle ?? type} (${nb})`).join(", ")}.</span>
                        )}
                    </AlertDescription>
                </Alert>
            )}
            {message && <Alert><AlertDescription>{message}</AlertDescription></Alert>}

            {tableau.error && <Alert variant="destructive"><AlertDescription>{messageErreur(tableau.error)}</AlertDescription></Alert>}
            {tableau.isLoading && <p className="text-muted-foreground">Contrôle des lignes ACCPAC…</p>}

            {t && (
                <>
                    {/* Synthèse + actions */}
                    <Card>
                        <CardHeader>
                            <CardTitle>Contrôle du {date(t.debut)} au {date(t.fin)}</CardTitle>
                            <CardDescription>Ce contrôle ne modifie rien.</CardDescription>
                        </CardHeader>
                        <CardContent className="flex flex-wrap items-center gap-6">
                            <div><div className="text-2xl font-semibold">{nombre(t.lignesARecuperer)}</div>
                                <div className="text-xs text-muted-foreground">lignes ACCPAC à récupérer</div></div>
                            <div><div className="text-2xl font-semibold">{nombre(t.bonsPrevus)}</div>
                                <div className="text-xs text-muted-foreground">bons prévus</div></div>
                            <div><div className={`text-2xl font-semibold ${bloquantes ? "text-destructive" : ""}`}>{nombre(bloquantes)}</div>
                                <div className="text-xs text-muted-foreground">lignes bloquées</div></div>

                            <div className="ml-auto flex flex-wrap gap-2">
                                {peut("ACCPAC_ANNULER") && (
                                    <DialogueAnnulation annee={annee} mois={mois}
                                                        onTermine={(m) => { setBilan(null); setMessage(m) }} />
                                )}
                                {peut("ACCPAC_RECUPERER") && (
                                    <DialogueRecuperation annee={annee} mois={mois} inclurePrecedent={inclurePrecedent}
                                                          bonsPrevus={t.bonsPrevus} bloquantes={bloquantes}
                                                          onTermine={(b) => { setMessage(null); setBilan(b) }} />
                                )}
                            </div>
                        </CardContent>
                    </Card>

                    {/* Anomalies */}
                    <Card>
                        <CardHeader>
                            <CardTitle>Lignes en anomalie ({t.lignes.length})</CardTitle>
                            <CardDescription className="flex flex-wrap gap-2 pt-1">
                                {Object.entries(t.anomaliesParType).map(([type, nb]) => {
                                    const a = ANOMALIES[type as TypeAnomalie]
                                    const actif = filtre === type
                                    return (
                                        <button key={type} type="button" onClick={() => setFiltre(actif ? "" : type as TypeAnomalie)}>
                                            <Badge variant={actif ? "default" : a?.bloquante ? "destructive" : "secondary"}>
                                                {a?.libelle ?? type} : {nb}
                                            </Badge>
                                        </button>
                                    )
                                })}
                                {Object.keys(t.anomaliesParType).length > 0 &&
                                    <span className="text-xs">Cliquez sur un type pour filtrer.</span>}
                            </CardDescription>
                        </CardHeader>
                        <CardContent>
                            {t.lignes.length === 0 ? <p className="text-sm text-muted-foreground">Aucune anomalie : tout peut être récupéré.</p> : (
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHead>Date</TableHead>
                                            <TableHead>Facture ACCPAC</TableHead>
                                            <TableHead>Camion</TableHead>
                                            <TableHead>Client</TableHead>
                                            <TableHead>Adresse (ShipTo)</TableHead>
                                            <TableHead className="text-right">Volume</TableHead>
                                            <TableHead>Anomalies</TableHead>
                                            {peut("ACCPAC_CORRIGER") && <TableHead />}
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {lignes.map((l) => (
                                            <TableRow key={l.idLigne}>
                                                <TableCell>{date(l.date)}</TableCell>
                                                <TableCell className="font-mono text-xs">{l.noFacture ?? "—"}</TableCell>
                                                <TableCell className="font-mono">{l.codeCamion?.trim() || "—"}</TableCell>
                                                <TableCell className="font-mono">{l.codeClient?.trim() || "—"}</TableCell>
                                                <TableCell>{l.shipTo?.trim() || ""}</TableCell>
                                                <TableCell className="text-right">{nombre(l.volume)}</TableCell>
                                                <TableCell className="flex flex-wrap gap-1">
                                                    {l.anomalies.map((a) => (
                                                        <Badge key={a} variant={ANOMALIES[a].bloquante ? "destructive" : "secondary"}>
                                                            {ANOMALIES[a].libelle}
                                                        </Badge>
                                                    ))}
                                                </TableCell>
                                                {peut("ACCPAC_CORRIGER") && (
                                                    <TableCell className="text-right">
                                                        {correctionsPossibles(l).length > 0 && (
                                                            <DialogueCorrectionCode ligne={l}
                                                                                    onCorrige={(n) => { setBilan(null); setMessage(`${nombre(n)} ligne(s) ACCPAC corrigée(s).`) }} />
                                                        )}
                                                    </TableCell>
                                                )}
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            )}
                        </CardContent>
                    </Card>

                    {/* Codes en double */}
                    <div className="grid gap-4 xl:grid-cols-2">
                        <TableDoubles
                            titre="Codes camion en double"
                            description="Plusieurs fiches pour un même code : la base choisit l'une d'elles au hasard (tarif et transporteur peuvent changer)."
                            codes={t.camionsEnDouble}
                            libelleDifferent="types différents" />
                        <TableDoubles
                            titre="Codes client en double"
                            description="Lignes sans adresse ACCPAC : la base choisit une fiche au hasard (le point de livraison peut changer)."
                            codes={t.clientsEnDouble}
                            libelleDifferent="points différents" />
                    </div>
                </>
            )}
        </div>
    )
}