import { useState } from "react"
import type { ReactNode } from "react"
import { useQueryClient } from "@tanstack/react-query"
import { CLE, useCoutsTransporteurs, useFacturesEnAttente, useSynthese, useVolumes } from "./api"
import { GraphiqueCouts, GraphiqueVolumes } from "./Graphiques"
import { date, heure, montant, nombre, NOMS_MOIS } from "@/lib/format"
import { messageErreur } from "@/lib/api"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"

const CLASSE_SELECT =
    "h-9 rounded-md border border-input bg-transparent px-3 text-sm shadow-xs outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"

function Indicateur({ titre, valeur, children, alerte = false }: {
    titre: string; valeur: string; children?: ReactNode; alerte?: boolean
}) {
    return (
        <Card className="gap-2 py-4">
            <CardHeader className="px-4">
                <CardDescription>{titre}</CardDescription>
                <CardTitle className={`text-2xl ${alerte ? "text-destructive" : ""}`}>{valeur}</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-0.5 px-4 text-xs text-muted-foreground">{children}</CardContent>
        </Card>
    )
}

function Erreur({ erreur }: { erreur: unknown }) {
    return <Alert variant="destructive"><AlertDescription>{messageErreur(erreur)}</AlertDescription></Alert>
}

export default function TableauDeBord() {
    const aujourdhui = new Date()
    const [annee, setAnnee] = useState(aujourdhui.getFullYear())
    const [mois, setMois] = useState(aujourdhui.getMonth() + 1)
    const client = useQueryClient()

    const synthese = useSynthese(annee, mois)
    const volumes = useVolumes(annee)
    const couts = useCoutsTransporteurs(annee, mois)
    const attente = useFacturesEnAttente(15)

    const annees = Array.from({ length: aujourdhui.getFullYear() - 2019 }, (_, i) => aujourdhui.getFullYear() - i)
    const s = synthese.data

    return (
        <div className="grid gap-6">
            {/* En-tête : choix du mois */}
            <div className="flex flex-wrap items-center justify-between gap-4">
                <h1 className="text-2xl font-semibold">Tableau de bord</h1>
                <div className="flex flex-wrap items-center gap-2">
                    <select className={CLASSE_SELECT} value={mois} onChange={(e) => setMois(Number(e.target.value))}
                            aria-label="Mois">
                        {NOMS_MOIS.map((nom, i) => <option key={nom} value={i + 1}>{nom}</option>)}
                    </select>
                    <select className={CLASSE_SELECT} value={annee} onChange={(e) => setAnnee(Number(e.target.value))}
                            aria-label="Année">
                        {annees.map((a) => <option key={a} value={a}>{a}</option>)}
                    </select>
                    <Button variant="outline" onClick={() => client.invalidateQueries({ queryKey: [CLE] })}>
                        Actualiser
                    </Button>
                    {s && <span className="text-xs text-muted-foreground">Mis à jour à {heure(s.calculeLe)}</span>}
                </div>
            </div>

            {/* Indicateurs du mois */}
            {synthese.error && <Erreur erreur={synthese.error} />}
            {synthese.isLoading && <p className="text-muted-foreground">Chargement…</p>}
            {s && (
                <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
                    <Indicateur titre="Bons livrés" valeur={nombre(s.livraisons.bons)}>
                        <span>{nombre(s.livraisons.volumeLivre)} gal livrés</span>
                        <span>{nombre(s.livraisons.bonsFactures)} déjà facturés</span>
                    </Indicateur>
                    <Indicateur titre="Bons à facturer" valeur={nombre(s.livraisons.bonsAFacturer)}
                                alerte={s.livraisons.bonsAFacturer > 0}>
                        <span>{nombre(s.livraisons.bonsExclus)} exclus (camions OT, OC…)</span>
                    </Indicateur>
                    <Indicateur titre="Coût du transport" valeur={montant(s.coutTransport.montant)}>
                        <span>{nombre(s.coutTransport.quantiteFacturee)} gal facturés</span>
                        <span>Prix moyen : {s.coutTransport.prixMoyenGallon === null
                            ? "—" : nombre(s.coutTransport.prixMoyenGallon, 4) + " USD/gal"}</span>
                    </Indicateur>
                    <Indicateur titre="Factures en attente de posting" valeur={nombre(s.attentePosting.factures)}
                                alerte={s.attentePosting.incoherentes > 0}>
                        {s.attentePosting.parDevise.map((d) => (
                            <span key={d.devise}>{montant(d.montant, d.devise)} ({d.factures})</span>
                        ))}
                        <span>{nombre(s.attentePosting.incoherentes)} incohérente(s) — plus ancienne : {date(s.attentePosting.plusAncienne)}</span>
                    </Indicateur>
                    <Indicateur titre="ACCPAC : lignes à récupérer" valeur={nombre(s.accpac.lignesARecuperer)}
                                alerte={s.accpac.lignesSansCamion + s.accpac.lignesCamionInconnu > 0}>
                        <span>{nombre(s.accpac.lignesSansCamion)} sans camion, {nombre(s.accpac.lignesCamionInconnu)} camion inconnu</span>
                        <span>{nombre(s.accpac.codesCamionEnDouble)} codes camion en double
              ({nombre(s.accpac.codesCamionTypesDifferents)} avec types différents)</span>
                    </Indicateur>
                </div>
            )}

            {/* Graphiques de l'année */}
            <div className="grid gap-4 xl:grid-cols-2">
                <Card>
                    <CardHeader>
                        <CardTitle>Volumes {annee}</CardTitle>
                        <CardDescription>Gallons livrés et gallons facturés, par mois de livraison</CardDescription>
                    </CardHeader>
                    <CardContent>
                        {volumes.error ? <Erreur erreur={volumes.error} /> : <GraphiqueVolumes donnees={volumes.data ?? []} />}
                    </CardContent>
                </Card>
                <Card>
                    <CardHeader>
                        <CardTitle>Coût du transport {annee}</CardTitle>
                        <CardDescription>Montant facturé (USD), par mois de livraison</CardDescription>
                    </CardHeader>
                    <CardContent>
                        {volumes.error ? <Erreur erreur={volumes.error} /> : <GraphiqueCouts donnees={volumes.data ?? []} />}
                    </CardContent>
                </Card>
            </div>

            {/* Tableaux */}
            <div className="grid gap-4 xl:grid-cols-2">
                <Card>
                    <CardHeader>
                        <CardTitle>Coût par transporteur</CardTitle>
                        <CardDescription>{NOMS_MOIS[mois - 1]} {annee} — les 10 plus importants</CardDescription>
                    </CardHeader>
                    <CardContent>
                        {couts.error && <Erreur erreur={couts.error} />}
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead>Transporteur</TableHead>
                                    <TableHead className="text-right">Bons</TableHead>
                                    <TableHead className="text-right">Gallons</TableHead>
                                    <TableHead className="text-right">Montant (USD)</TableHead>
                                    <TableHead className="text-right">USD/gal</TableHead>
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {(couts.data ?? []).slice(0, 10).map((c) => (
                                    <TableRow key={c.idTransporteur}>
                                        <TableCell>{c.idTransporteur} – {c.transporteur}</TableCell>
                                        <TableCell className="text-right">{nombre(c.bons)}</TableCell>
                                        <TableCell className="text-right">{nombre(c.quantite)}</TableCell>
                                        <TableCell className="text-right">{nombre(c.montant, 2)}</TableCell>
                                        <TableCell className="text-right">{nombre(c.prixMoyenGallon, 4)}</TableCell>
                                    </TableRow>
                                ))}
                                {couts.data?.length === 0 && (
                                    <TableRow><TableCell colSpan={5} className="text-muted-foreground">Aucune facture pour ce mois.</TableCell></TableRow>
                                )}
                            </TableBody>
                        </Table>
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader>
                        <CardTitle>Factures en attente de posting</CardTitle>
                        <CardDescription>Les 15 plus anciennes</CardDescription>
                    </CardHeader>
                    <CardContent>
                        {attente.error && <Erreur erreur={attente.error} />}
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead>N° facture</TableHead>
                                    <TableHead>Transporteur</TableHead>
                                    <TableHead>Générée le</TableHead>
                                    <TableHead className="text-right">Attente</TableHead>
                                    <TableHead className="text-right">Montant</TableHead>
                                    <TableHead />
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {(attente.data ?? []).map((f) => (
                                    <TableRow key={f.id}>
                                        <TableCell className="font-mono text-xs">{f.numero}</TableCell>
                                        <TableCell>{f.transporteur}</TableCell>
                                        <TableCell>{date(f.generee)}</TableCell>
                                        <TableCell className="text-right">{nombre(f.joursAttente)} j</TableCell>
                                        <TableCell className="text-right">{montant(f.montant, f.devise)}</TableCell>
                                        <TableCell>{f.incoherente && <Badge variant="destructive">Incohérente</Badge>}</TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </CardContent>
                </Card>
            </div>
        </div>
    )
}