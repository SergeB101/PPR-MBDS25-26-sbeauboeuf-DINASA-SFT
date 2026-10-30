import { useState } from "react"
import type { ReactNode } from "react"
import {
    useCamions, useClients, useListeTransporteurs, usePoints, usePrixTarif, useTarifs, useTypesCamion,
} from "./api"
import { useDebounce } from "@/lib/useDebounce"
import { messageErreur } from "@/lib/api"
import { date, nombre } from "@/lib/format"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"

type Onglet = "transporteurs" | "camions" | "clients" | "points" | "types" | "tarifs"

const ONGLETS: [Onglet, string][] = [
    ["transporteurs", "Transporteurs"], ["camions", "Camions"], ["clients", "Clients"],
    ["points", "Points de livraison"], ["types", "Types de camion"], ["tarifs", "Tarifs"],
]

function Etat({ requete, vide, children }: {
    requete: { isLoading: boolean; error: unknown; data?: unknown[] }; vide: string; children: ReactNode
}) {
    if (requete.isLoading) return <p className="text-sm text-muted-foreground">Chargement…</p>
    if (requete.error) return <Alert variant="destructive"><AlertDescription>{messageErreur(requete.error)}</AlertDescription></Alert>
    if (!requete.data?.length) return <p className="text-sm text-muted-foreground">{vide}</p>
    return <>{children}</>
}

function Recherche({ valeur, onChange, aide }: { valeur: string; onChange: (v: string) => void; aide: string }) {
    return <Input className="max-w-sm" placeholder={aide} value={valeur} onChange={(e) => onChange(e.target.value)} />
}

function Transporteurs({ onVoirCamions }: { onVoirCamions: (id: number, nom: string) => void }) {
    const [texte, setTexte] = useState("")
    const liste = useListeTransporteurs(useDebounce(texte))
    return (
        <div className="grid gap-3">
            <Recherche valeur={texte} onChange={setTexte} aide="Nom, prénom ou code…" />
            <Etat requete={liste} vide="Aucun transporteur.">
                <Table>
                    <TableHeader><TableRow>
                        <TableHead>Code</TableHead><TableHead>Nom</TableHead><TableHead>Type</TableHead>
                        <TableHead className="text-right">Camions</TableHead><TableHead />
                    </TableRow></TableHeader>
                    <TableBody>
                        {liste.data?.map((t) => (
                            <TableRow key={t.id}>
                                <TableCell>{t.id}</TableCell>
                                <TableCell>{t.nom} {t.prenom}</TableCell>
                                <TableCell>{t.type}</TableCell>
                                <TableCell className="text-right">{t.camions}</TableCell>
                                <TableCell className="text-right">
                                    <Button variant="ghost" size="sm" onClick={() => onVoirCamions(t.id, `${t.nom} ${t.prenom ?? ""}`)}>Voir ses camions</Button>
                                </TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </Etat>
        </div>
    )
}

function Camions({ transporteur, onRetirerFiltre }: {
    transporteur: { id: number; nom: string } | null; onRetirerFiltre: () => void
}) {
    const [texte, setTexte] = useState("")
    const liste = useCamions(useDebounce(texte), transporteur?.id ?? null)
    return (
        <div className="grid gap-3">
            <div className="flex flex-wrap items-center gap-3">
                <Recherche valeur={texte} onChange={setTexte} aide="Code camion ou immatriculation…" />
                {transporteur && (
                    <Badge variant="secondary" className="gap-2">
                        Transporteur : {transporteur.id} – {transporteur.nom}
                        <button type="button" onClick={onRetirerFiltre} aria-label="Retirer le filtre">✕</button>
                    </Badge>
                )}
            </div>
            <Etat requete={liste} vide="Aucun camion.">
                <Table>
                    <TableHeader><TableRow>
                        <TableHead>Code</TableHead><TableHead>Immatriculation</TableHead><TableHead>Marque / modèle</TableHead>
                        <TableHead>Année</TableHead><TableHead className="text-right">Capacité</TableHead><TableHead>Type</TableHead>
                        <TableHead>Méthode</TableHead><TableHead>Statut</TableHead><TableHead>Transporteur</TableHead>
                    </TableRow></TableHeader>
                    <TableBody>
                        {liste.data?.map((c) => (
                            <TableRow key={c.id}>
                                <TableCell className="font-mono">
                                    {c.code} {c.fichesMemeCode > 1 && <Badge variant="destructive" title="Ce code est porté par plusieurs fiches">×{c.fichesMemeCode}</Badge>}
                                </TableCell>
                                <TableCell>{c.immatriculation}</TableCell>
                                <TableCell>{[c.marque, c.modele].filter(Boolean).join(" ")}</TableCell>
                                <TableCell>{c.annee ?? ""}</TableCell>
                                <TableCell className="text-right">{nombre(c.capacite)}</TableCell>
                                <TableCell>{c.type ?? <span className="text-destructive">non défini</span>}</TableCell>
                                <TableCell>{c.methodeTarification}</TableCell>
                                <TableCell>{c.statut}</TableCell>
                                <TableCell>{c.idTransporteur ? `${c.idTransporteur} – ${c.transporteur}` : <span className="text-destructive">aucun</span>}</TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </Etat>
        </div>
    )
}

function Clients() {
    const [texte, setTexte] = useState("")
    const liste = useClients(useDebounce(texte))
    return (
        <div className="grid gap-3">
            <Recherche valeur={texte} onChange={setTexte} aide="Code ou nom du client…" />
            <Etat requete={liste} vide="Aucun client.">
                <Table>
                    <TableHeader><TableRow>
                        <TableHead>Code</TableHead><TableHead>Nom</TableHead><TableHead>Point de livraison</TableHead>
                        <TableHead className="text-right">Distance (km)</TableHead><TableHead>Téléphone</TableHead><TableHead>Adresse</TableHead>
                    </TableRow></TableHeader>
                    <TableBody>
                        {liste.data?.map((c) => (
                            <TableRow key={c.id}>
                                <TableCell className="font-mono">
                                    {c.code} {c.fichesMemeCode > 1 && <Badge variant="outline" title="Même code sur plusieurs fiches (un point de livraison par fiche)">×{c.fichesMemeCode}</Badge>}
                                </TableCell>
                                <TableCell className="whitespace-normal">{c.nom}</TableCell>
                                <TableCell>{c.pointLivraison ?? <span className="text-destructive">aucun</span>}</TableCell>
                                <TableCell className="text-right">{nombre(c.distance)}</TableCell>
                                <TableCell>{c.telephone}</TableCell>
                                <TableCell className="whitespace-normal">{c.adresse}</TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </Etat>
        </div>
    )
}

function Points() {
    const liste = usePoints()
    return (
        <Etat requete={liste} vide="Aucun point de livraison.">
            <Table>
                <TableHeader><TableRow>
                    <TableHead>Point de livraison</TableHead><TableHead className="text-right">Distance (km)</TableHead>
                    <TableHead className="text-right">Zone</TableHead><TableHead className="text-right">Clients</TableHead>
                </TableRow></TableHeader>
                <TableBody>
                    {liste.data?.map((p) => (
                        <TableRow key={p.id}>
                            <TableCell>{p.libelle}</TableCell>
                            <TableCell className="text-right">{nombre(p.distance)}</TableCell>
                            <TableCell className="text-right">{p.zone}</TableCell>
                            <TableCell className="text-right">{p.clients}</TableCell>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
        </Etat>
    )
}

function Types() {
    const liste = useTypesCamion()
    return (
        <Etat requete={liste} vide="Aucun type de camion.">
            <Table>
                <TableHeader><TableRow>
                    <TableHead>Type</TableHead><TableHead>Années</TableHead><TableHead className="text-right">Camions</TableHead>
                    <TableHead className="text-right">Tarifs actifs</TableHead><TableHead>Alerte</TableHead>
                </TableRow></TableHeader>
                <TableBody>
                    {liste.data?.map((t) => (
                        <TableRow key={t.id}>
                            <TableCell>{t.id} – {t.libelle}</TableCell>
                            <TableCell>{t.anneeMin ?? "…"} – {t.anneeMax ?? "…"}</TableCell>
                            <TableCell className="text-right">{t.camions}</TableCell>
                            <TableCell className="text-right">{t.tarifsActifs}</TableCell>
                            <TableCell className="whitespace-normal">{t.alerte && <span className="text-destructive">{t.alerte}</span>}</TableCell>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
        </Etat>
    )
}

function Tarifs() {
    const [actifs, setActifs] = useState(true)
    const [choisi, setChoisi] = useState<number | null>(null)
    const liste = useTarifs(actifs)
    const prix = usePrixTarif(choisi)
    const tarif = liste.data?.find((t) => t.id === choisi)

    return (
        <div className="grid gap-4">
            <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" className="size-4 accent-primary" checked={actifs}
                       onChange={(e) => { setActifs(e.target.checked); setChoisi(null) }} />
                Grilles actives seulement
            </label>
            <Etat requete={liste} vide="Aucune grille de tarifs.">
                <Table>
                    <TableHeader><TableRow>
                        <TableHead>Grille</TableHead><TableHead>Type de camion</TableHead><TableHead>Devise</TableHead>
                        <TableHead>Début</TableHead><TableHead>Fin</TableHead><TableHead className="text-right">Prix</TableHead><TableHead>État</TableHead>
                    </TableRow></TableHeader>
                    <TableBody>
                        {liste.data?.map((t) => (
                            <TableRow key={t.id} className={`cursor-pointer ${t.id === choisi ? "bg-muted" : ""}`} onClick={() => setChoisi(t.id)}>
                                <TableCell>{t.id} – {t.libelle}</TableCell>
                                <TableCell>{t.typeCamion}</TableCell>
                                <TableCell>{t.devise ?? "—"}</TableCell>
                                <TableCell>{date(t.debut)}</TableCell>
                                <TableCell>{date(t.fin)}</TableCell>
                                <TableCell className="text-right">{t.prix}</TableCell>
                                <TableCell>{t.actif ? <Badge>Active</Badge> : <Badge variant="outline">Inactive</Badge>}</TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </Etat>
            <p className="text-xs text-muted-foreground">
                Seule la case « active » compte pour la facturation : les dates des grilles ne sont pas fiables.
            </p>

            {choisi !== null && (
                <Card>
                    <CardHeader><CardTitle>Prix au gallon — {tarif?.libelle}</CardTitle></CardHeader>
                    <CardContent>
                        <Etat requete={prix} vide="Cette grille n'a aucun prix.">
                            <Table>
                                <TableHeader><TableRow>
                                    <TableHead>Point de livraison</TableHead><TableHead className="text-right">Distance (km)</TableHead>
                                    <TableHead className="text-right">Prix / gallon</TableHead>
                                </TableRow></TableHeader>
                                <TableBody>
                                    {prix.data?.map((p) => (
                                        <TableRow key={p.idPointLivraison}>
                                            <TableCell>{p.pointLivraison}</TableCell>
                                            <TableCell className="text-right">{nombre(p.distance)}</TableCell>
                                            <TableCell className="text-right">{nombre(p.prixAuGallon, 4)}</TableCell>
                                        </TableRow>
                                    ))}
                                </TableBody>
                            </Table>
                        </Etat>
                    </CardContent>
                </Card>
            )}
        </div>
    )
}

export default function PageReferentiel() {
    const [onglet, setOnglet] = useState<Onglet>("transporteurs")
    const [transporteur, setTransporteur] = useState<{ id: number; nom: string } | null>(null)

    return (
        <div className="grid gap-4">
            <h1 className="text-2xl font-semibold">Référentiel</h1>
            <div className="flex flex-wrap gap-1 border-b">
                {ONGLETS.map(([cle, libelle]) => (
                    <button key={cle} type="button" onClick={() => setOnglet(cle)}
                            className={`-mb-px border-b-2 px-3 py-2 text-sm ${onglet === cle
                                ? "border-primary font-medium" : "border-transparent text-muted-foreground hover:text-foreground"}`}>
                        {libelle}
                    </button>
                ))}
            </div>
            {onglet === "transporteurs" && (
                <Transporteurs onVoirCamions={(id, nom) => { setTransporteur({ id, nom }); setOnglet("camions") }} />
            )}
            {onglet === "camions" && <Camions transporteur={transporteur} onRetirerFiltre={() => setTransporteur(null)} />}
            {onglet === "clients" && <Clients />}
            {onglet === "points" && <Points />}
            {onglet === "types" && <Types />}
            {onglet === "tarifs" && <Tarifs />}
        </div>
    )
}