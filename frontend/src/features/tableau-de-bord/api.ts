import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"

export interface Synthese {
    mois: string
    livraisons: { bons: number; volumeLivre: number; bonsFactures: number; bonsAFacturer: number; bonsExclus: number }
    coutTransport: { quantiteFacturee: number; montant: number; prixMoyenGallon: number | null }
    attentePosting: {
        factures: number
        incoherentes: number
        plusAncienne: string | null
        parDevise: { devise: string; factures: number; montant: number }[]
    }
    accpac: {
        lignesARecuperer: number
        lignesOT: number
        lignesSansCamion: number
        lignesCamionInconnu: number
        codesCamionEnDouble: number
        codesCamionTypesDifferents: number
    }
    calculeLe: string
}

export interface VolumeMois {
    mois: number
    bons: number
    volumeLivre: number
    quantiteFacturee: number
    montant: number
}

export interface CoutTransporteur {
    idTransporteur: number
    transporteur: string
    bons: number
    quantite: number
    montant: number
    prixMoyenGallon: number | null
}

export interface FactureEnAttente {
    id: number
    numero: string
    idTransporteur: number
    transporteur: string
    generee: string
    joursAttente: number
    devise: string
    montant: number
    sommeLignes: number
    incoherente: boolean
}

const BASE = "/api/v1/tableaux-de-bord"
const UNE_MINUTE = 60_000

/** Toutes les clés commencent par "tableau-de-bord" : un seul geste pour tout rafraîchir. */
export const CLE = "tableau-de-bord"

export function useSynthese(annee: number, mois: number) {
    return useQuery({
        queryKey: [CLE, "synthese", annee, mois],
        queryFn: () => api<Synthese>(`${BASE}/synthese?annee=${annee}&mois=${mois}`),
        refetchInterval: UNE_MINUTE,
    })
}

export function useVolumes(annee: number) {
    return useQuery({
        queryKey: [CLE, "volumes", annee],
        queryFn: () => api<VolumeMois[]>(`${BASE}/volumes-par-mois?annee=${annee}`),
        refetchInterval: UNE_MINUTE,
    })
}

export function useCoutsTransporteurs(annee: number, mois: number) {
    return useQuery({
        queryKey: [CLE, "couts", annee, mois],
        queryFn: () => api<CoutTransporteur[]>(`${BASE}/couts-par-transporteur?annee=${annee}&mois=${mois}`),
        refetchInterval: UNE_MINUTE,
    })
}

export function useFacturesEnAttente(limite: number) {
    return useQuery({
        queryKey: [CLE, "attente", limite],
        queryFn: () => api<FactureEnAttente[]>(`${BASE}/factures-en-attente?limite=${limite}`),
        refetchInterval: UNE_MINUTE,
    })
}