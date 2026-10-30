import { useState } from "react"
import type { ReactNode } from "react"
import { messageErreur } from "@/lib/api"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import {
    Dialog, DialogClose, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from "@/components/ui/dialog"

/** Fenêtre de confirmation : valide, affiche l'erreur du serveur, se ferme si tout va bien. */
export function DialogueAction({ declencheur, titre, description, libelle, destructif = false, peutValider = true,
                                   onValider, onOuvrir, children }: {
    declencheur: ReactNode; titre: string; description: string; libelle: string; destructif?: boolean
    peutValider?: boolean; onValider: () => Promise<void>; onOuvrir?: () => void; children?: ReactNode
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
        <Dialog open={ouvert} onOpenChange={(o) => { setOuvert(o); setErreur(null); if (o) onOuvrir?.() }}>
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