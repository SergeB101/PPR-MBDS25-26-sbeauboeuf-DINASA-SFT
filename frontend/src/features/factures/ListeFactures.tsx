import { useLocation, useNavigate, useSearchParams } from "react-router"
import { useSession } from "@/auth/session"
import { aUnEcart, cheminExportListe, useFactures, useTransporteurs } from "./api"
import type { Criteres } from "./api"
import { date, montant, nombre } from "@/lib/format"
import { messageErreur } from "@/lib/api"
import { BoutonTelechargement } from "@/components/BoutonTelechargement"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { NativeSelect } from "@/components/ui/native-select"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"

const TAILLE = 50

export default function ListeFactures() {
    const [params, setParams] = useSearchParams()
    const navigate = useNavigate()
    const location = useLocation()
    const { peut } = useSession()
    const message = (location.state as { message?: string } | null)?.message

    const criteres: Criteres = {
        du: params.get("du") ?? undefined,
        au: params.get("au") ?? undefined,
        transporteur: params.get("transporteur") ? Number(params.get("transporteur")) : undefined,
        postee: params.get("postee") === null ? undefined : params.get("postee") === "true",
        page: Number(params.get("page") ?? 0),
        taille: TAILLE,
    }

    const changer = (cle: string, valeur: string) => {
        const p = new URLSearchParams(params)
        if (valeur) p.set(cle, valeur)
        else p.delete(cle)
        if (cle !== "page") p.delete("page")
        setParams(p, { replace: true })
    }

    const factures = useFactures(criteres)
    const transporteurs = useTransporteurs(peut("REFERENTIEL_CONSULTER"))
    const lignes = factures.data ?? []

    return (
        <div className="grid gap-4">
            <h1 className="text-2xl font-semibold">Factures</h1>
            {message && <Alert><AlertDescription>{message}</AlertDescription></Alert>}

            {/* Filtres */}
            <Card className="py-4">
                <CardContent className="flex flex-wrap items-end gap-4 px-4">
                    <div className="grid gap-1.5">
                        <Label htmlFor="du">Du</Label>
                        <Input id="du" type="date" className="w-40" value={criteres.du ?? ""}
                               onChange={(e) => changer("du", e.target.value)} />
                    </div>
                    <div className="grid gap-1.5">
                        <Label htmlFor="au">Au</Label>
                        <Input id="au" type="date" className="w-40" value={criteres.au ?? ""}
                               onChange={(e) => changer("au", e.target.value)} />
                    </div>
                    <div className="grid gap-1.5">
                        <Label htmlFor="transporteur">Transporteur</Label>
                        {peut("REFERENTIEL_CONSULTER") ? (
                            <NativeSelect id="transporteur" className="w-64" value={criteres.transporteur ?? ""}
                                          onChange={(e) => changer("transporteur", e.target.value)}>
                                <option value="">Tous</option>
                                {(transporteurs.data ?? []).map((t) => (
                                    <option key={t.id} value={t.id}>{t.id} – {t.nom} {t.prenom ?? ""}</option>
                                ))}
                            </NativeSelect>
                        ) : (
                            <Input id="transporteur" type="number" placeholder="Code" className="w-32"
                                   value={criteres.transporteur ?? ""} onChange={(e) => changer("transporteur", e.target.value)} />
                        )}
                    </div>
                    <div className="grid gap-1.5">
                        <Label htmlFor="statut">Statut</Label>
                        <NativeSelect id="statut" value={params.get("postee") ?? ""}
                                      onChange={(e) => changer("postee", e.target.value)}>
                            <option value="">Toutes</option>
                            <option value="false">En attente de posting</option>
                            <option value="true">Postées</option>
                        </NativeSelect>
                    </div>
                    <Button variant="ghost" onClick={() => setParams({}, { replace: true })}>Effacer</Button>
                    <div className="ml-auto">
                        <BoutonTelechargement chemin={cheminExportListe(criteres)} libelle="Exporter en Excel"
                                              desactive={!criteres.du || !criteres.au}
                                              titre={!criteres.du || !criteres.au ? "Choisissez d'abord une période (du / au)" : undefined} />
                    </div>
                </CardContent>
            </Card>

            {factures.error && <Alert variant="destructive"><AlertDescription>{messageErreur(factures.error)}</AlertDescription></Alert>}

            {/* Résultats */}
            <Card className="py-2">
                <CardContent className="px-2">
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHead>N° facture</TableHead>
                                <TableHead>Date</TableHead>
                                <TableHead>Transporteur</TableHead>
                                <TableHead className="text-right">Lignes</TableHead>
                                <TableHead className="text-right">Transport</TableHead>
                                <TableHead className="text-right">Services</TableHead>
                                <TableHead className="text-right">Total</TableHead>
                                <TableHead>Statut</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {lignes.map((f) => (
                                <TableRow key={f.id} className="cursor-pointer" onClick={() => navigate(`/factures/${f.id}`)}>
                                    <TableCell className="font-mono text-xs">{f.numero}</TableCell>
                                    <TableCell>{date(f.dateGeneration)}</TableCell>
                                    <TableCell>{f.codeTransporteur} – {f.nomTransporteur}</TableCell>
                                    <TableCell className="text-right">{nombre(f.nombreLignes)}</TableCell>
                                    <TableCell className="text-right">{nombre(f.montantTransport, 2)}</TableCell>
                                    <TableCell className="text-right">{nombre(f.montantServices, 2)}</TableCell>
                                    <TableCell className="text-right font-medium">{montant(f.montant, f.devise)}</TableCell>
                                    <TableCell className="flex gap-1">
                                        {f.postee ? <Badge variant="secondary">Postée</Badge> : <Badge variant="outline">En attente</Badge>}
                                        {aUnEcart(f) && <Badge variant="destructive">Écart</Badge>}
                                    </TableCell>
                                </TableRow>
                            ))}
                            {!factures.isLoading && lignes.length === 0 && (
                                <TableRow><TableCell colSpan={8} className="text-muted-foreground">Aucune facture pour ces critères.</TableCell></TableRow>
                            )}
                            {factures.isLoading && (
                                <TableRow><TableCell colSpan={8} className="text-muted-foreground">Chargement…</TableCell></TableRow>
                            )}
                        </TableBody>
                    </Table>
                </CardContent>
            </Card>

            {/* Pages */}
            <div className="flex items-center justify-end gap-2">
                <span className="text-sm text-muted-foreground">Page {criteres.page + 1}</span>
                <Button variant="outline" size="sm" disabled={criteres.page === 0}
                        onClick={() => changer("page", String(criteres.page - 1))}>Précédente</Button>
                <Button variant="outline" size="sm" disabled={lignes.length < TAILLE}
                        onClick={() => changer("page", String(criteres.page + 1))}>Suivante</Button>
            </div>
        </div>
    )
}