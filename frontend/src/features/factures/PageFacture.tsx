import { useNavigate, useParams } from "react-router"
import { useSession } from "@/auth/session"
import { aUnEcart, useFacture } from "./api"
import { DialogueCorrection, DialogueIncorporation, DialoguePoster, DialogueService, DialogueSuppression } from "./Dialogues"
import { Historique } from "./Historique"
import { date, montant, nombre } from "@/lib/format"
import { messageErreur } from "@/lib/api"
import { BoutonTelechargement } from "@/components/BoutonTelechargement"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"

export default function PageFacture() {
    const { id } = useParams()
    const idFacture = Number(id)
    const navigate = useNavigate()
    const { peut } = useSession()
    const facture = useFacture(idFacture)

    if (facture.isLoading) return <p className="text-muted-foreground">Chargement…</p>
    if (facture.error || !facture.data) {
        return (
            <div className="grid gap-4">
                <Alert variant="destructive"><AlertDescription>{messageErreur(facture.error)}</AlertDescription></Alert>
                <Button variant="outline" className="w-fit" onClick={() => navigate("/factures")}>Retour aux factures</Button>
            </div>
        )
    }

    const f = facture.data
    const modifiable = !f.postee && peut("FACTURES_MODIFIER")
    const transport = f.lignes.filter((l) => l.type === "TRANSPORT")
    const services = f.lignes.filter((l) => l.type === "SERVICE")
    const quantiteTotale = transport.reduce((s, l) => s + (l.quantite ?? 0), 0)
    const quitter = () => navigate("/factures", { state: { message: `La facture ${f.numero} n'avait plus de ligne : elle a été supprimée.` } })

    return (
        <div className="grid gap-4">
            {/* En-tête */}
            <div className="flex flex-wrap items-start justify-between gap-4">
                <div className="grid gap-1">
                    <Button variant="link" className="h-auto w-fit p-0" onClick={() => navigate(-1)}>← Retour</Button>
                    <h1 className="flex items-center gap-3 text-2xl font-semibold">
                        <span className="font-mono">{f.numero}</span>
                        {f.postee ? <Badge variant="secondary">Postée</Badge> : <Badge variant="outline">En attente de posting</Badge>}
                    </h1>
                    <p className="text-muted-foreground">
                        {f.codeTransporteur} – {f.nomTransporteur} · générée le {date(f.dateGeneration)} · {f.devise}
                    </p>
                </div>
                <div className="flex flex-wrap items-start gap-2">
                    <BoutonTelechargement chemin={`/api/v1/exports/factures/${f.id}/pdf`} libelle="PDF" />
                    <BoutonTelechargement chemin={`/api/v1/exports/factures/${f.id}/excel`} libelle="Excel" />
                    {modifiable && <DialogueService facture={f} />}
                    {modifiable && <DialogueIncorporation facture={f} />}
                    {!f.postee && peut("FACTURES_POSTER") && <DialoguePoster facture={f} />}
                </div>
            </div>

            {/* Montants */}
            <div className="grid gap-4 sm:grid-cols-4">
                <Card className="gap-1 py-4"><CardHeader className="px-4"><CardDescription>Transport</CardDescription>
                    <CardTitle>{montant(f.montantTransport, f.devise)}</CardTitle></CardHeader>
                    <CardContent className="px-4 text-xs text-muted-foreground">{transport.length} bon(s) · {nombre(quantiteTotale)} gal</CardContent></Card>
                <Card className="gap-1 py-4"><CardHeader className="px-4"><CardDescription>Services</CardDescription>
                    <CardTitle>{montant(f.montantServices, f.devise)}</CardTitle></CardHeader>
                    <CardContent className="px-4 text-xs text-muted-foreground">{services.length} ligne(s)</CardContent></Card>
                <Card className="gap-1 py-4 sm:col-span-2"><CardHeader className="px-4"><CardDescription>Total de la facture</CardDescription>
                    <CardTitle className="text-2xl">{montant(f.montant, f.devise)}</CardTitle></CardHeader></Card>
            </div>

            {aUnEcart(f) && (
                <Alert variant="destructive">
                    <AlertTitle>Écart entre l'en-tête et les lignes</AlertTitle>
                    <AlertDescription>
                        Total de l'en-tête : {montant(f.montant, f.devise)} — somme des lignes : {montant(f.montantTransport + f.montantServices, f.devise)}.
                        Cette facture ne pourra pas être postée tant que l'écart existe.
                    </AlertDescription>
                </Alert>
            )}

            {/* Lignes de transport */}
            <Card>
                <CardHeader><CardTitle>Transport ({transport.length} bons)</CardTitle></CardHeader>
                <CardContent>
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHead>Date</TableHead>
                                <TableHead>Bon</TableHead>
                                <TableHead>Camion</TableHead>
                                <TableHead>Client facturé</TableHead>
                                <TableHead className="text-right">Qté (gal)</TableHead>
                                <TableHead className="text-right">Prix/gal</TableHead>
                                <TableHead className="text-right">Montant</TableHead>
                                {modifiable && <TableHead />}
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {transport.map((l) => (
                                <TableRow key={l.id}>
                                    <TableCell>{date(l.dateLivraison)}</TableCell>
                                    <TableCell className="font-mono text-xs">{l.numeroBon}</TableCell>
                                    <TableCell>{l.codeCamion}</TableCell>
                                    <TableCell>{l.client}</TableCell>
                                    <TableCell className="text-right">
                                        {nombre(l.quantite)} {l.modifieeManuellement && <Badge variant="outline" title="Ligne modifiée à la main">modifiée</Badge>}
                                    </TableCell>
                                    <TableCell className="text-right">{nombre(l.prixAuGallon, 4)}</TableCell>
                                    <TableCell className="text-right">{nombre(l.montant, 2)}</TableCell>
                                    {modifiable && (
                                        <TableCell className="text-right">
                                            <DialogueCorrection facture={f} ligne={l} />
                                            <DialogueSuppression facture={f} ligne={l} onFactureSupprimee={quitter} />
                                        </TableCell>
                                    )}
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </CardContent>
            </Card>

            {/* Services */}
            <Card>
                <CardHeader><CardTitle>Services ({services.length})</CardTitle></CardHeader>
                <CardContent>
                    {services.length === 0 ? <p className="text-sm text-muted-foreground">Aucun service sur cette facture.</p> : (
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead>Description</TableHead>
                                    <TableHead className="text-right">Montant</TableHead>
                                    {modifiable && <TableHead />}
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {services.map((l) => (
                                    <TableRow key={l.id}>
                                        <TableCell className="whitespace-normal">{l.description ?? "Service"}</TableCell>
                                        <TableCell className="text-right">{nombre(l.montant, 2)}</TableCell>
                                        {modifiable && (
                                            <TableCell className="text-right">
                                                <DialogueSuppression facture={f} ligne={l} onFactureSupprimee={quitter} />
                                            </TableCell>
                                        )}
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    )}
                </CardContent>
            </Card>

            {/* Historique */}
            <Card>
                <CardHeader>
                    <CardTitle>Historique</CardTitle>
                    <CardDescription>Qui a fait quoi sur cette facture, quand et pourquoi</CardDescription>
                </CardHeader>
                <CardContent><Historique idFacture={f.id} /></CardContent>
            </Card>
        </div>
    )
}