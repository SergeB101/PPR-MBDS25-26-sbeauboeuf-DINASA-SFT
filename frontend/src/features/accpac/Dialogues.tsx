import { useState } from "react"
import { useQueryClient } from "@tanstack/react-query"
import { actionsAccpac, CLE_ACCPAC, correctionsPossibles } from "./api"
import type { Bilan, LigneAnomalie } from "./api"
import { DialogueAction } from "@/components/DialogueAction"
import { date, nombre, NOMS_MOIS } from "@/lib/format"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { NativeSelect } from "@/components/ui/native-select"
import { Textarea } from "@/components/ui/textarea"

function useRafraichir() {
    const client = useQueryClient()
    return () => {
        client.invalidateQueries({ queryKey: [CLE_ACCPAC] })
        client.invalidateQueries({ queryKey: ["apercu"] })
        client.invalidateQueries({ queryKey: ["tableau-de-bord"] })
    }
}

function ChampMotif({ valeur, onChange }: { valeur: string; onChange: (v: string) => void }) {
    return (
        <div className="grid gap-1.5">
            <Label htmlFor="motif">Motif (obligatoire)</Label>
            <Textarea id="motif" maxLength={500} value={valeur} onChange={(e) => onChange(e.target.value)}
                      placeholder="Pourquoi ? Ce texte sera gardé dans le journal." />
        </div>
    )
}

export function DialogueCorrectionCode({ ligne, onCorrige }: { ligne: LigneAnomalie; onCorrige: (n: number) => void }) {
    const rafraichir = useRafraichir()
    const possibles = correctionsPossibles(ligne)
    const [type, setType] = useState<"CAMION" | "CLIENT">(possibles[0] ?? "CAMION")
    const [code, setCode] = useState("")
    const [motif, setMotif] = useState("")
    const ancien = type === "CAMION" ? ligne.codeCamion : ligne.codeClient

    return (
        <DialogueAction
            declencheur={<Button variant="ghost" size="sm">Corriger</Button>}
            onOuvrir={() => { setCode(""); setMotif("") }}
            titre={`Corriger la ligne ACCPAC du ${date(ligne.date)}`}
            description={`Facture ACCPAC ${ligne.noFacture ?? "—"}`}
            libelle="Enregistrer la correction"
            peutValider={code.trim().length > 0 && motif.trim().length > 0}
            onValider={async () => {
                const r = await actionsAccpac.corriger(ligne.idLigne, type, code.trim(), motif.trim())
                rafraichir()
                onCorrige(r.lignesCorrigees)
            }}
        >
            {possibles.length > 1 && (
                <div className="grid gap-1.5">
                    <Label htmlFor="type">Code à corriger</Label>
                    <NativeSelect id="type" value={type} onChange={(e) => setType(e.target.value as "CAMION" | "CLIENT")}>
                        <option value="CAMION">Code camion</option>
                        <option value="CLIENT">Code client</option>
                    </NativeSelect>
                </div>
            )}
            <p className="text-sm">
                Code {type === "CAMION" ? "camion" : "client"} actuel : <strong>{ancien?.trim() || "(vide)"}</strong>
            </p>
            <div className="grid gap-1.5">
                <Label htmlFor="code">Nouveau code {type === "CAMION" ? "camion" : "client"}</Label>
                <Input id="code" value={code} onChange={(e) => setCode(e.target.value)} autoFocus
                       placeholder={type === "CAMION" ? "ex. LCM14" : "code client tel qu'il existe dans la base"} />
            </div>
            {type === "CAMION" && ancien?.trim() && (
                <Alert>
                    <AlertDescription>
                        Toutes les lignes <strong>non récupérées</strong> du mois portant le code « {ancien.trim()} » seront corrigées en même temps.
                    </AlertDescription>
                </Alert>
            )}
            <ChampMotif valeur={motif} onChange={setMotif} />
        </DialogueAction>
    )
}

export function DialogueRecuperation({ annee, mois, inclurePrecedent, bonsPrevus, bloquantes, onTermine }: {
    annee: number; mois: number; inclurePrecedent: boolean; bonsPrevus: number; bloquantes: number
    onTermine: (b: Bilan) => void
}) {
    const rafraichir = useRafraichir()
    return (
        <DialogueAction
            declencheur={<Button disabled={bonsPrevus === 0}>Récupérer {nombre(bonsPrevus)} bon(s)</Button>}
            titre={`Récupérer les bons de ${NOMS_MOIS[mois - 1]} ${annee} ?`}
            description="Les bons de livraison seront créés à partir des lignes ACCPAC, puis deviendront facturables."
            libelle="Récupérer"
            onValider={async () => {
                const b = await actionsAccpac.recuperer(annee, mois, inclurePrecedent)
                rafraichir()
                onTermine(b)
            }}
        >
            <p className="text-sm"><strong>{nombre(bonsPrevus)}</strong> bon(s) prévu(s){inclurePrecedent ? ", mois précédent compris" : ""}.</p>
            {bloquantes > 0 && (
                <Alert variant="destructive">
                    <AlertDescription>
                        {nombre(bloquantes)} ligne(s) ont une anomalie bloquante : elles ne seront <strong>pas</strong> récupérées.
                        Vous pourrez les corriger puis relancer la récupération.
                    </AlertDescription>
                </Alert>
            )}
        </DialogueAction>
    )
}

export function DialogueAnnulation({ annee, mois, onTermine }: {
    annee: number; mois: number; onTermine: (message: string) => void
}) {
    const rafraichir = useRafraichir()
    const [motif, setMotif] = useState("")
    return (
        <DialogueAction
            declencheur={<Button variant="outline" className="text-destructive">Annuler la récupération</Button>}
            onOuvrir={() => setMotif("")}
            titre={`Annuler la récupération de ${NOMS_MOIS[mois - 1]} ${annee} ?`}
            description="Supprime les bons créés par la récupération de ce mois et remet les lignes ACCPAC « à récupérer ». Refusé si un seul bon du mois est déjà facturé."
            libelle="Annuler la récupération"
            destructif
            peutValider={motif.trim().length > 0}
            onValider={async () => {
                const b = await actionsAccpac.annuler(annee, mois, motif.trim())
                rafraichir()
                onTermine(`${nombre(b.bonsSupprimes)} bon(s) supprimé(s), ${nombre(b.lignesReinitialisees)} ligne(s) ACCPAC remises à récupérer.`)
            }}
        >
            <ChampMotif valeur={motif} onChange={setMotif} />
        </DialogueAction>
    )
}