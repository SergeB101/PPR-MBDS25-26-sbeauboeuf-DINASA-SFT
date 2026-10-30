import { Fragment, useState } from "react"
import { useNavigate } from "react-router"
import { useQueryClient } from "@tanstack/react-query"
import { useSession } from "@/auth/session"
import { generer, MOTIFS, totalFacture, useApercu } from "./api"
import type { MotifRejet, ResultatCalcul } from "./api"
import { useTransporteurs } from "@/features/factures/api"
import { messageErreur } from "@/lib/api"
import { montant, nombre, NOMS_MOIS } from "@/lib/format"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { NativeSelect } from "@/components/ui/native-select"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import {
    Dialog, DialogClose, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from "@/components/ui/dialog"

function moisPrecedent() {
    const d = new Date()
    d.setDate(1)
    d.setMonth(d.getMonth() - 1)
    return { annee: d.getFullYear(), mois: d.getMonth() + 1 }
}

export default function PageGeneration() {
    const depart = moisPrecedent()
    const [annee, setAnnee] = useState(depart.annee)
    const [mois, setMois] = useState(depart.mois)
    const [exclus, setExclus] = useState<Set<number>>(new Set())      // transporteurs décochés
    const [ouverts, setOuverts] = useState<Set<number>>(new Set())    // détails dépliés
    const [confirmation, setConfirmation] = useState(false)
    const [envoi, setEnvoi] = useState(false)
    const [erreur, setErreur] = useState<string | null>(null)
    const [resultat, setResultat] = useState<ResultatCalcul | null>(null)

    const navigate = useNavigate()
    const client = useQueryClient()
    const { peut } = useSession()
    const apercu = useApercu(annee, mois)
    const transporteurs = useTransporteurs(peut("REFERENTIEL_CONSULTER"))
    const noms = new Map((transporteurs.data ?? []).map((t) => [t.id, `${t.nom} ${t.prenom ?? ""}`.trim()]))

    const factures = apercu.data?.factures ?? []
    const rejets = apercu.data?.rejets ?? []
    const choisies = factures.filter((f) => !exclus.has(f.codeTransporteur))
    const totaux = choisies.map(totalFacture)
    const totalMontant = totaux.reduce((s, t) => s + t.montant, 0)
    const totalBons = totaux.reduce((s, t) => s + t.bons, 0)
    const totalGallons = totaux.reduce((s, t) => s + t.gallons, 0)

    const rejetsParMotif = new Map<MotifRejet, number>()
    rejets.forEach((r) => rejetsParMotif.set(r.motif, (rejetsParMotif.get(r.motif) ?? 0) + 1))

    const annees = Array.from({ length: new Date().getFullYear() - 2019 }, (_, i) => new Date().getFullYear() - i)

    const changerPeriode = (a: number, m: number) => {
        setAnnee(a)
        setMois(m)
        setExclus(new Set())
        setOuverts(new Set())
        setResultat(null)
    }

    const basculer = (ensemble: Set<number>, cle: number, maj: (s: Set<number>) => void) => {
        const s = new Set(ensemble)
        if (s.has(cle)) s.delete(cle)
        else s.add(cle)
        maj(s)
    }

    const lancer = async () => {
        setErreur(null)
        setEnvoi(true)
        try {
            // Tous cochés : on laisse le serveur prendre tous les bons du mois. Sinon : seulement les bons choisis.
            const ids = exclus.size === 0 ? undefined : choisies.flatMap((f) => f.lignes.map((l) => l.idMouvement))
            const r = await generer(annee, mois, ids)
            setResultat(r)
            setConfirmation(false)
            setExclus(new Set())
            client.invalidateQueries({ queryKey: ["apercu"] })
            client.invalidateQueries({ queryKey: ["factures"] })
            client.invalidateQueries({ queryKey: ["tableau-de-bord"] })
        } catch (e) {
            setErreur(messageErreur(e))
        } finally {
            setEnvoi(false)
        }
    }

    const aujourdhui = new Date().toISOString().slice(0, 10)

    return (
        <div className="grid gap-4">
            <div className="flex flex-wrap items-center justify-between gap-4">
                <h1 className="text-2xl font-semibold">Génération des factures</h1>
                <div className="flex items-center gap-2">
                    <NativeSelect value={mois} aria-label="Mois" onChange={(e) => changerPeriode(annee, Number(e.target.value))}>
                        {NOMS_MOIS.map((nom, i) => <option key={nom} value={i + 1}>{nom}</option>)}
                    </NativeSelect>
                    <NativeSelect value={annee} aria-label="Année" onChange={(e) => changerPeriode(Number(e.target.value), mois)}>
                        {annees.map((a) => <option key={a} value={a}>{a}</option>)}
                    </NativeSelect>
                    <Button variant="outline" onClick={() => apercu.refetch()} disabled={apercu.isFetching}>
                        {apercu.isFetching ? "Calcul…" : "Recalculer"}
                    </Button>
                </div>
            </div>

            {/* Résultat d'une génération */}
            {resultat && (
                <Alert>
                    <AlertTitle>{resultat.factures.length} facture(s) créée(s)</AlertTitle>
                    <AlertDescription className="flex flex-wrap items-center gap-3">
                        <span>{nombre(resultat.factures.reduce((s, f) => s + f.lignes.length, 0))} bons facturés.</span>
                        <Button size="sm" variant="outline" onClick={() => navigate(`/factures?du=${aujourdhui}&au=${aujourdhui}`)}>
                            Voir les factures du jour
                        </Button>
                    </AlertDescription>
                </Alert>
            )}

            {apercu.error && <Alert variant="destructive"><AlertDescription>{messageErreur(apercu.error)}</AlertDescription></Alert>}
            {apercu.isLoading && <p className="text-muted-foreground">Calcul de l'aperçu…</p>}

            {apercu.data && (
                <>
                    {/* Synthèse + bouton de génération */}
                    <Card>
                        <CardHeader>
                            <CardTitle>Aperçu — {NOMS_MOIS[mois - 1]} {annee}</CardTitle>
                            <CardDescription>Rien n'est enregistré tant que vous n'avez pas cliqué sur « Générer ».</CardDescription>
                        </CardHeader>
                        <CardContent className="flex flex-wrap items-center gap-6">
                            <div><div className="text-2xl font-semibold">{choisies.length}</div><div className="text-xs text-muted-foreground">facture(s) sur {factures.length}</div></div>
                            <div><div className="text-2xl font-semibold">{nombre(totalBons)}</div><div className="text-xs text-muted-foreground">bons</div></div>
                            <div><div className="text-2xl font-semibold">{nombre(totalGallons)}</div><div className="text-xs text-muted-foreground">gallons</div></div>
                            <div><div className="text-2xl font-semibold">{montant(totalMontant)}</div><div className="text-xs text-muted-foreground">montant total</div></div>
                            <div><div className={`text-2xl font-semibold ${rejets.length ? "text-destructive" : ""}`}>{nombre(rejets.length)}</div><div className="text-xs text-muted-foreground">bons rejetés</div></div>

                            <div className="ml-auto">
                                <Dialog open={confirmation} onOpenChange={(o) => { setConfirmation(o); if (!o) setErreur(null) }}>
                                    <DialogTrigger asChild>
                                        <Button disabled={choisies.length === 0}>Générer {choisies.length} facture(s)</Button>
                                    </DialogTrigger>
                                    <DialogContent>
                                        <DialogHeader>
                                            <DialogTitle>Générer les factures de {NOMS_MOIS[mois - 1]} {annee} ?</DialogTitle>
                                            <DialogDescription>
                                                Les factures seront enregistrées et les bons marqués comme facturés.
                                                Pour revenir en arrière, il faudra supprimer les lignes une par une.
                                            </DialogDescription>
                                        </DialogHeader>
                                        <ul className="grid gap-1 text-sm">
                                            <li><strong>{choisies.length}</strong> facture(s), <strong>{nombre(totalBons)}</strong> bons</li>
                                            <li>Montant total : <strong>{montant(totalMontant)}</strong></li>
                                            {exclus.size > 0 && <li>{exclus.size} transporteur(s) décoché(s) : non facturés pour l'instant</li>}
                                        </ul>
                                        {erreur && <Alert variant="destructive"><AlertDescription>{erreur}</AlertDescription></Alert>}
                                        <DialogFooter>
                                            <DialogClose asChild><Button variant="outline">Annuler</Button></DialogClose>
                                            <Button onClick={lancer} disabled={envoi}>{envoi ? "Génération…" : "Générer"}</Button>
                                        </DialogFooter>
                                    </DialogContent>
                                </Dialog>
                            </div>
                        </CardContent>
                    </Card>

                    {/* Factures prévues */}
                    <Card>
                        <CardHeader>
                            <CardTitle>Factures prévues ({factures.length})</CardTitle>
                            <CardDescription>Une facture par transporteur. Décochez ceux à ne pas facturer maintenant ; cliquez sur une ligne pour voir ses bons.</CardDescription>
                        </CardHeader>
                        <CardContent>
                            <Table>
                                <TableHeader>
                                    <TableRow>
                                        <TableHead className="w-10">
                                            <input type="checkbox" className="size-4 accent-primary" aria-label="Tout cocher"
                                                   checked={exclus.size === 0}
                                                   onChange={() => setExclus(exclus.size === 0 ? new Set(factures.map((f) => f.codeTransporteur)) : new Set())} />
                                        </TableHead>
                                        <TableHead>Transporteur</TableHead>
                                        <TableHead className="text-right">Bons</TableHead>
                                        <TableHead className="text-right">Gallons</TableHead>
                                        <TableHead className="text-right">Montant</TableHead>
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {factures.map((f) => {
                                        const t = totalFacture(f)
                                        const ouvert = ouverts.has(f.codeTransporteur)
                                        return (
                                            <Fragment key={f.codeTransporteur}>
                                                <TableRow className={`cursor-pointer ${exclus.has(f.codeTransporteur) ? "opacity-50" : ""}`}
                                                          onClick={() => basculer(ouverts, f.codeTransporteur, setOuverts)}>
                                                    <TableCell onClick={(e) => e.stopPropagation()}>
                                                        <input type="checkbox" className="size-4 accent-primary" aria-label="Facturer ce transporteur"
                                                               checked={!exclus.has(f.codeTransporteur)}
                                                               onChange={() => basculer(exclus, f.codeTransporteur, setExclus)} />
                                                    </TableCell>
                                                    <TableCell>{ouvert ? "▾" : "▸"} {f.codeTransporteur} – {noms.get(f.codeTransporteur) ?? ""}</TableCell>
                                                    <TableCell className="text-right">{nombre(t.bons)}</TableCell>
                                                    <TableCell className="text-right">{nombre(t.gallons)}</TableCell>
                                                    <TableCell className="text-right font-medium">{montant(t.montant, t.devise)}</TableCell>
                                                </TableRow>
                                                {ouvert && (
                                                    <TableRow className="bg-muted/30 hover:bg-muted/30">
                                                        <TableCell />
                                                        <TableCell colSpan={4}>
                                                            <Table>
                                                                <TableHeader>
                                                                    <TableRow>
                                                                        <TableHead>Bon</TableHead>
                                                                        <TableHead>Camion</TableHead>
                                                                        <TableHead className="text-right">Gallons</TableHead>
                                                                        <TableHead className="text-right">Prix/gal</TableHead>
                                                                        <TableHead className="text-right">Montant</TableHead>
                                                                    </TableRow>
                                                                </TableHeader>
                                                                <TableBody>
                                                                    {f.lignes.map((l) => (
                                                                        <TableRow key={l.idMouvement}>
                                                                            <TableCell className="font-mono text-xs">{l.numeroBon}</TableCell>
                                                                            <TableCell>{l.codeCamion}</TableCell>
                                                                            <TableCell className="text-right">{nombre(l.quantiteCommandee)}</TableCell>
                                                                            <TableCell className="text-right">{nombre(l.prixAuGallon, 4)}</TableCell>
                                                                            <TableCell className="text-right">{nombre(l.montant.valeur, 2)}</TableCell>
                                                                        </TableRow>
                                                                    ))}
                                                                </TableBody>
                                                            </Table>
                                                        </TableCell>
                                                    </TableRow>
                                                )}
                                            </Fragment>
                                        )
                                    })}
                                    {factures.length === 0 && (
                                        <TableRow><TableCell colSpan={5} className="text-muted-foreground">
                                            Aucun bon à facturer pour ce mois (tout est déjà facturé, ou aucune livraison récupérée).
                                        </TableCell></TableRow>
                                    )}
                                </TableBody>
                            </Table>
                        </CardContent>
                    </Card>

                    {/* Rejets */}
                    {rejets.length > 0 && (
                        <Card>
                            <CardHeader>
                                <CardTitle>Bons rejetés ({rejets.length})</CardTitle>
                                <CardDescription>Ces bons ne seront pas facturés tant que la cause n'est pas corrigée.</CardDescription>
                            </CardHeader>
                            <CardContent className="grid gap-4">
                                <div className="grid gap-2">
                                    {[...rejetsParMotif.entries()].map(([motif, nb]) => (
                                        <div key={motif} className="flex flex-wrap items-baseline gap-2 text-sm">
                                            <Badge variant={motif === "CAMION_PAS_AU_GALLON" ? "secondary" : "destructive"}>{nb}</Badge>
                                            <strong>{MOTIFS[motif].libelle}</strong>
                                            <span className="text-muted-foreground">— {MOTIFS[motif].quoiFaire}</span>
                                        </div>
                                    ))}
                                </div>
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHead>Bon</TableHead>
                                            <TableHead>Raison</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {rejets.map((r) => (
                                            <TableRow key={r.idMouvement}>
                                                <TableCell className="font-mono text-xs">{r.numeroBon}</TableCell>
                                                <TableCell>{MOTIFS[r.motif].libelle}</TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            </CardContent>
                        </Card>
                    )}
                </>
            )}
        </div>
    )
}