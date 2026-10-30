import { useState } from "react"
import { telecharger, messageErreur } from "@/lib/api"
import { Button } from "@/components/ui/button"

export function BoutonTelechargement({ chemin, libelle, desactive = false, titre }: {
    chemin: string; libelle: string; desactive?: boolean; titre?: string
}) {
    const [envoi, setEnvoi] = useState(false)
    const [erreur, setErreur] = useState<string | null>(null)

    const lancer = async () => {
        setErreur(null)
        setEnvoi(true)
        try {
            await telecharger(chemin)
        } catch (e) {
            setErreur(messageErreur(e))
        } finally {
            setEnvoi(false)
        }
    }

    return (
        <span className="inline-flex flex-col items-start gap-1">
      <Button variant="outline" size="sm" onClick={lancer} disabled={envoi || desactive} title={titre}>
        {envoi ? "Préparation…" : libelle}
      </Button>
            {erreur && <span className="text-xs text-destructive">{erreur}</span>}
    </span>
    )
}