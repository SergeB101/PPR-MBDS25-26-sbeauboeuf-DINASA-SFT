import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"
import type { Permission } from "@/lib/types"

export interface Utilisateur {
    id: number; login: string; nom: string; prenom: string | null; idGroupe: number; groupe: string | null
    actif: boolean; compteActive: boolean; doitChangerMotDePasse: boolean
    bloqueJusqua: string | null; derniereConnexion: string | null
}
export interface Groupe { id: number; libelle: string; utilisateursActifs: number; permissions: Permission[] }
export interface PermissionVue { code: Permission; libelle: string }
export interface MotDePasseTemporaire { idUtilisateur: number; login: string; motDePasse: string }

const BASE = "/api/v1/securite"
export const CLE_ACCES = "acces"

export const useUtilisateurs = () =>
    useQuery({ queryKey: [CLE_ACCES, "utilisateurs"], queryFn: () => api<Utilisateur[]>(`${BASE}/utilisateurs`) })
export const useGroupes = () =>
    useQuery({ queryKey: [CLE_ACCES, "groupes"], queryFn: () => api<Groupe[]>(`${BASE}/groupes`) })
export const usePermissions = () =>
    useQuery({ queryKey: [CLE_ACCES, "permissions"], queryFn: () => api<PermissionVue[]>(`${BASE}/permissions`), staleTime: Infinity })

export const actionsAcces = {
    creer: (d: { login: string; nom: string; prenom: string; idGroupe: number }) =>
        api<MotDePasseTemporaire>(`${BASE}/utilisateurs`, { method: "POST", body: JSON.stringify(d) }),
    modifier: (id: number, idGroupe: number, actif: boolean) =>
        api<void>(`${BASE}/utilisateurs/${id}`, { method: "PATCH", body: JSON.stringify({ idGroupe, actif }) }),
    motDePasseTemporaire: (id: number) =>
        api<MotDePasseTemporaire>(`${BASE}/utilisateurs/${id}/mot-de-passe-temporaire`, { method: "POST" }),
    deconnecter: (id: number) => api<void>(`${BASE}/utilisateurs/${id}/deconnexion`, { method: "POST" }),
    droits: (idGroupe: number, permissions: Permission[], motif: string) =>
        api<Groupe>(`${BASE}/groupes/${idGroupe}/permissions`, { method: "PUT", body: JSON.stringify({ permissions, motif }) }),
}