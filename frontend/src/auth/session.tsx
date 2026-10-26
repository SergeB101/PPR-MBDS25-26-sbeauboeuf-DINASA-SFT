import { createContext, useCallback, useContext, useEffect, useState } from "react"
import type { ReactNode } from "react"
import { api, enregistrerJeton, lireJeton, quandSessionExpiree } from "@/lib/api"
import type { Permission, Profil, ResultatConnexion } from "@/lib/types"

interface Session {
    profil: Profil | null
    chargement: boolean
    connecter: (login: string, motDePasse: string) => Promise<ResultatConnexion>
    changerMotDePasse: (login: string, ancien: string, nouveau: string) => Promise<void>
    deconnecter: () => void
    peut: (permission: Permission) => boolean
}

const ContexteSession = createContext<Session | null>(null)

export function FournisseurSession({ children }: { children: ReactNode }) {
    const [profil, setProfil] = useState<Profil | null>(null)
    const [chargement, setChargement] = useState(true)

    const chargerProfil = useCallback(async () => {
        try {
            setProfil(await api<Profil>("/api/v1/auth/moi"))
        } catch {
            enregistrerJeton(null)
            setProfil(null)
        }
    }, [])

    useEffect(() => {
        quandSessionExpiree(() => setProfil(null))
        if (lireJeton()) chargerProfil().finally(() => setChargement(false))
        else setChargement(false)
    }, [chargerProfil])

    const connecter = async (login: string, motDePasse: string) => {
        const r = await api<ResultatConnexion>("/api/v1/auth/connexion", {
            method: "POST",
            body: JSON.stringify({ login, motDePasse }),
        })
        if (r.jeton) {
            enregistrerJeton(r.jeton)
            await chargerProfil()
        }
        return r
    }

    const changerMotDePasse = async (login: string, ancien: string, nouveau: string) => {
        const r = await api<ResultatConnexion>("/api/v1/auth/changement-mot-de-passe", {
            method: "POST",
            body: JSON.stringify({ login, ancienMotDePasse: ancien, nouveauMotDePasse: nouveau }),
        })
        enregistrerJeton(r.jeton)
        await chargerProfil()
    }

    const deconnecter = () => {
        enregistrerJeton(null)
        setProfil(null)
    }

    const peut = (permission: Permission) => profil?.permissions.includes(permission) ?? false

    return (
        <ContexteSession.Provider value={{ profil, chargement, connecter, changerMotDePasse, deconnecter, peut }}>
            {children}
        </ContexteSession.Provider>
    )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useSession(): Session {
    const s = useContext(ContexteSession)
    if (!s) throw new Error("useSession doit être utilisé dans FournisseurSession")
    return s
}