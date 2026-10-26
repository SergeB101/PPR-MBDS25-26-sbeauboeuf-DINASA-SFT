import type { ReactNode } from "react"
import { Navigate, useLocation } from "react-router"
import { useSession } from "@/auth/session"
import type { Permission } from "@/lib/types"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"

export function RouteProtegee({ children }: { children: ReactNode }) {
    const { profil, chargement } = useSession()
    const location = useLocation()
    if (chargement) return <p className="p-8 text-muted-foreground">Chargement…</p>
    if (!profil) return <Navigate to="/connexion" replace state={{ depuis: location.pathname }} />
    return <>{children}</>
}

export function Autorisation({ permission, children }: { permission: Permission; children: ReactNode }) {
    const { peut } = useSession()
    if (!peut(permission)) {
        return (
            <Alert variant="destructive" className="max-w-xl">
                <AlertTitle>Accès refusé</AlertTitle>
                <AlertDescription>Votre groupe n'a pas le droit d'ouvrir cet écran.</AlertDescription>
            </Alert>
        )
    }
    return <>{children}</>
}