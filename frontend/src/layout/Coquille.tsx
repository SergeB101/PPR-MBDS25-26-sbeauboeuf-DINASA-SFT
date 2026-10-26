import { NavLink, Outlet } from "react-router"
import { useSession } from "@/auth/session"
import type { Permission } from "@/lib/types"
import { Button } from "@/components/ui/button"

const MENU: { chemin: string; libelle: string; permission: Permission }[] = [
    { chemin: "/", libelle: "Tableau de bord", permission: "TABLEAU_DE_BORD_CONSULTER" },
    { chemin: "/factures", libelle: "Factures", permission: "FACTURES_CONSULTER" },
    { chemin: "/generation", libelle: "Génération", permission: "FACTURES_GENERER" },
    { chemin: "/accpac", libelle: "ACCPAC", permission: "ACCPAC_CONSULTER" },
    { chemin: "/referentiel", libelle: "Référentiel", permission: "REFERENTIEL_CONSULTER" },
    { chemin: "/acces", libelle: "Gestion des accès", permission: "SECURITE_ADMINISTRER" },
]

export default function Coquille() {
    const { profil, peut, deconnecter } = useSession()

    return (
        <div className="flex min-h-svh">
            <aside className="w-60 shrink-0 border-r bg-muted/40 p-4">
                <div className="mb-6 px-2 text-lg font-semibold">DINASA Fact</div>
                <nav className="grid gap-1">
                    {MENU.filter((m) => peut(m.permission)).map((m) => (
                        <NavLink key={m.chemin} to={m.chemin} end={m.chemin === "/"}
                                 className={({ isActive }) =>
                                     `rounded-md px-3 py-2 text-sm ${isActive ? "bg-primary text-primary-foreground" : "hover:bg-muted"}`}>
                            {m.libelle}
                        </NavLink>
                    ))}
                </nav>
            </aside>
            <div className="flex flex-1 flex-col">
                <header className="flex items-center justify-end gap-4 border-b px-6 py-3">
                    <span className="text-sm text-muted-foreground">{profil?.nom || profil?.login}</span>
                    <Button variant="outline" size="sm" onClick={deconnecter}>Se déconnecter</Button>
                </header>
                <main className="flex-1 p-6">
                    <Outlet />
                </main>
            </div>
        </div>
    )
}