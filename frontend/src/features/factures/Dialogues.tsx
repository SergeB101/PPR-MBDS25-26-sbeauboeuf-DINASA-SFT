import { useState } from "react"
import type { ReactNode } from "react"
import { useQueryClient } from "@tanstack/react-query"
import { actions, aUnEcart, CLE_FACTURES, useTypesServices } from "./api"
import type { FicheFacture, LigneFacture } from "./api"
import { messageErreur } from "@/lib/api"
import { montant, nombre } from "@/lib/format"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { NativeSelect } from "@/components/ui/native-select"
import { Textarea } from "@/components/ui/textarea"
import {
    Dialog, DialogClose, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from "@/components/ui/dialog"

/** Après toute action : on recharge les factures et le tableau de bord. */
function useRafraichir() {
    const client = useQueryClient()
    return () => {
        client.invalidateQueries({ queryKey: [CLE_FACTURES] })
        client.invalidateQueries({ queryKey: ["tableau-de-bord"] })
    }
}

/** Fenêtre générique : ouvre, valide, affiche l'erreur éventuelle, se ferme si tout va bien. */
function DialogueAction({ declencheur, titre, description, libelle, destructif = false, peutValider = true,
                            onValider, children }: {
    declencheur: ReactNode; titre: string; description: string; libelle: string; destructif?: boolean
    peutValider?: boolean; onValider: () => Promise<void>; children?: ReactNode
}) {
    const [ouvert, setOuvert] = useState(false)
    const [erreur, setErreur] = useState<string | null>(null)
    const [envoi, setEnvoi] = useState(false)

    const valider = async () => {
        setErreur(null)
        setEnvoi(true)
        try {
            await onValider()
            setOuvert(false)
        } catch (e) {
            setErreur(messageErreur(e))
        } finally {
            setEnvoi(false)
        }
    }

    return (
        <Dialog open={ouvert} onOpenChange={(o) => { setOuvert(o); if (!o) setErreur(null) }}>
            <DialogTrigger asChild>{declencheur}</DialogTrigger>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle>{titre}</DialogTitle>
                    <DialogDescription>{description}</DialogDescription>
                </DialogHeader>
                {children && <div className="grid gap-4">{children}</div>}
                {erreur && <Alert variant="destructive"><AlertDescription>{erreur}</AlertDescription></Alert>}
                <DialogFooter>
                    <DialogClose asChild><Button variant="outline">Annuler</Button></DialogClose>
                    <Button variant={destructif ? "destructive" : "default"} disabled={envoi || !peutValider} onClick={valider}>
                        {envoi ? "Enregistrement…" : libelle}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    )
}

function ChampMotif({ valeur, onChange }: { valeur: string; onChange: (v: string) => void }) {
    return (
        <div className="grid gap-1.5">
            <Label htmlFor="motif">Motif (obligatoire)</Label>
            <Textarea id="motif" maxLength={500} value={valeur} onChange={(e) => onChange(e.target.value)}
                      placeholder="Pourquoi cette opération ? Ce texte sera gardé dans l'historique." />
        </div>
    )
}

export function DialoguePoster({ facture }: { facture: FicheFacture }) {
    const rafraichir = useRafraichir()
    return (
        <DialogueAction
            declencheur={<Button size="sm">Poster</Button>}
            titre={`Poster la facture ${facture.numero} ?`}
            description="Une facture postée est validée et verrouillée : elle ne pourra plus être modifiée."
            libelle="Poster la facture"
            onValider={async () => { await actions.poster(facture.id); rafraichir() }}
        >
            <p className="text-sm">Montant : <strong>{montant(facture.montant, facture.devise)}</strong></p>
            {aUnEcart(facture) && (
                <Alert variant="destructive">
                    <AlertDescription>
                        Le montant de l'en-tête diffère de la somme des lignes : le posting sera refusé tant que l'écart existe.
                    </AlertDescription>
                </Alert>
            )}
        </DialogueAction>
    )
}

export function DialogueCorrection({ facture, ligne }: { facture: FicheFacture; ligne: LigneFacture }) {
    const rafraichir = useRafraichir()
    const [quantite, setQuantite] = useState(String(ligne.quantite ?? ""))
    const [motif, setMotif] = useState("")
    const q = Number(quantite)
    const apercu = Number.isFinite(q) && ligne.prixAuGallon !== null
        ? Math.floor(q * ligne.prixAuGallon * 100) / 100 : null // centimes tronqués, comme le calcul

    return (
        <DialogueAction
            declencheur={<Button variant="ghost" size="sm">Corriger</Button>}
            titre={`Corriger la quantité — bon ${ligne.numeroBon ?? ""}`}
            description={`Quantité actuelle : ${nombre(ligne.quantite)} gal à ${nombre(ligne.prixAuGallon, 4)} ${facture.devise}/gal.`}
            libelle="Enregistrer la correction"
            peutValider={quantite !== "" && q >= 0 && motif.trim().length > 0}
            onValider={async () => {
                await actions.corrigerQuantite(facture.id, ligne.id, q, motif.trim())
                setMotif("")
                rafraichir()
            }}
        >
            <div className="grid gap-1.5">
                <Label htmlFor="quantite">Nouvelle quantité (gallons)</Label>
                <Input id="quantite" type="number" min={0} step="1" value={quantite} onChange={(e) => setQuantite(e.target.value)} />
                {apercu !== null && <span className="text-xs text-muted-foreground">Nouveau montant : {montant(apercu, facture.devise)}</span>}
            </div>
            <ChampMotif valeur={motif} onChange={setMotif} />
        </DialogueAction>
    )
}

export function DialogueSuppression({ facture, ligne, onFactureSupprimee }: {
    facture: FicheFacture; ligne: LigneFacture; onFactureSupprimee: () => void
}) {
    const rafraichir = useRafraichir()
    const [motif, setMotif] = useState("")
    const libelleLigne = ligne.type === "TRANSPORT" ? `le bon ${ligne.numeroBon ?? ""}` : `le service « ${ligne.description ?? ""} »`

    return (
        <DialogueAction
            declencheur={<Button variant="ghost" size="sm" className="text-destructive">Supprimer</Button>}
            titre={`Supprimer ${libelleLigne} ?`}
            description={ligne.type === "TRANSPORT"
                ? "Le bon redeviendra facturable. Si c'est la dernière ligne, la facture entière sera supprimée."
                : "Le service sera retiré de la facture."}
            libelle="Supprimer la ligne"
            destructif
            peutValider={motif.trim().length > 0}
            onValider={async () => {
                const r = await actions.supprimerLigne(facture.id, ligne.id, motif.trim())
                setMotif("")
                rafraichir()
                if (r.factureSupprimee) onFactureSupprimee()
            }}
        >
            <p className="text-sm">Montant de la ligne : <strong>{montant(ligne.montant, facture.devise)}</strong></p>
            <ChampMotif valeur={motif} onChange={setMotif} />
        </DialogueAction>
    )
}

export function DialogueService({ facture }: { facture: FicheFacture }) {
    const rafraichir = useRafraichir()
    const types = useTypesServices(true)
    const [idType, setIdType] = useState("")
    const [valeur, setValeur] = useState("")
    const [dateService, setDateService] = useState(new Date().toISOString().slice(0, 10))
    const [note, setNote] = useState("")
    const type = types.data?.find((t) => String(t.id) === idType)

    return (
        <DialogueAction
            declencheur={<Button variant="outline" size="sm">Ajouter un service</Button>}
            titre="Ajouter un service à la facture"
            description="Saisissez un montant positif : le type choisi indique s'il s'agit d'une retenue ou d'un ajout."
            libelle="Ajouter le service"
            peutValider={idType !== "" && Number(valeur) > 0 && dateService !== ""}
            onValider={async () => {
                await actions.ajouterService(facture.id, {
                    idType: Number(idType), montant: Number(valeur), dateService, note: note.trim() || undefined,
                })
                setValeur("")
                setNote("")
                rafraichir()
            }}
        >
            <div className="grid gap-1.5">
                <Label htmlFor="type">Type de service</Label>
                <NativeSelect id="type" value={idType} onChange={(e) => setIdType(e.target.value)}>
                    <option value="">Choisir…</option>
                    {(types.data ?? []).map((t) => (
                        <option key={t.id} value={t.id}>{t.libelle} ({t.signe < 0 ? "retenue" : "ajout"})</option>
                    ))}
                </NativeSelect>
            </div>
            <div className="grid grid-cols-2 gap-4">
                <div className="grid gap-1.5">
                    <Label htmlFor="montant">Montant ({facture.devise})</Label>
                    <Input id="montant" type="number" min={0} step="0.01" value={valeur} onChange={(e) => setValeur(e.target.value)} />
                </div>
                <div className="grid gap-1.5">
                    <Label htmlFor="dateService">Date du service</Label>
                    <Input id="dateService" type="date" value={dateService} onChange={(e) => setDateService(e.target.value)} />
                </div>
            </div>
            {type && Number(valeur) > 0 && (
                <span className="text-sm">Effet sur la facture : <strong>{type.signe < 0 ? "−" : "+"} {montant(Number(valeur), facture.devise)}</strong></span>
            )}
            <div className="grid gap-1.5">
                <Label htmlFor="note">Note (facultative)</Label>
                <Textarea id="note" maxLength={250} value={note} onChange={(e) => setNote(e.target.value)} />
            </div>
        </DialogueAction>
    )
}

export function DialogueIncorporation({ facture }: { facture: FicheFacture }) {
    const rafraichir = useRafraichir()
    const [resultat, setResultat] = useState<number | null>(null)

    return (
        <DialogueAction
            declencheur={<Button variant="outline" size="sm" onClick={() => setResultat(null)}>Reprendre les services en attente</Button>}
            titre="Reprendre les services en attente"
            description="Ajoute à cette facture les services en attente du transporteur pour le mois de la facture."
            libelle="Reprendre les services"
            onValider={async () => {
                const r = await actions.incorporerEnAttente(facture.id)
                setResultat(r.servicesIncorpores)
                rafraichir()
                if (r.servicesIncorpores === 0) throw new Error("Aucun service en attente pour ce transporteur et ce mois.")
            }}
        >
            {resultat !== null && resultat > 0 && <p className="text-sm">{resultat} service(s) repris.</p>}
        </DialogueAction>
    )
}