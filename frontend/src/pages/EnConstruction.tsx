import { useSession } from "@/auth/session"

export default function EnConstruction({ titre }: { titre: string }) {
    const { profil } = useSession()
    return (
        <div className="grid gap-2">
            <h1 className="text-2xl font-semibold">{titre}</h1>
            <p className="text-muted-foreground">Écran prévu dans une prochaine étape.</p>
            <p className="text-sm text-muted-foreground">
                Connecté : {profil?.login} — droits : {profil?.permissions.join(", ") || "aucun"}
            </p>
        </div>
    )
}