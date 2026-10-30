import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"

export interface Transporteur { id: number; nom: string; prenom: string | null; type: string | null; camions: number }
export interface Camion {
    id: number; code: string; immatriculation: string | null; marque: string | null; modele: string | null
    annee: number | null; capacite: number | null; idType: number | null; type: string | null; statut: string | null
    methodeTarification: string | null; idTransporteur: number | null; transporteur: string | null; fichesMemeCode: number
}
export interface Client {
    id: number; code: string | null; nom: string; idPointLivraison: number | null; pointLivraison: string | null
    distance: number | null; telephone: string | null; adresse: string | null; fichesMemeCode: number
}
export interface PointLivraison { id: number; libelle: string; distance: number | null; zone: number; clients: number }
export interface TypeCamion {
    id: number; libelle: string; anneeMin: number | null; anneeMax: number | null
    camions: number; tarifsActifs: number; alerte: string | null
}
export interface Tarif {
    id: number; libelle: string; idTypeCamion: number | null; typeCamion: string | null
    debut: string | null; fin: string | null; actif: boolean; devise: string | null; prix: number
}
export interface PrixTarif { idPointLivraison: number; pointLivraison: string | null; distance: number | null; prixAuGallon: number | null }

const BASE = "/api/v1/referentiel"
const CINQ_MINUTES = 5 * 60_000
const q = (r: string) => encodeURIComponent(r.trim())

export const useListeTransporteurs = (recherche: string) => useQuery({
    queryKey: ["referentiel", "transporteurs", recherche],
    queryFn: () => api<Transporteur[]>(`${BASE}/transporteurs?recherche=${q(recherche)}`), staleTime: CINQ_MINUTES,
})

export const useCamions = (recherche: string, idTransporteur: number | null) => useQuery({
    queryKey: ["referentiel", "camions", recherche, idTransporteur],
    queryFn: () => api<Camion[]>(`${BASE}/camions?recherche=${q(recherche)}${idTransporteur ? `&idTransporteur=${idTransporteur}` : ""}`),
    staleTime: CINQ_MINUTES,
})

export const useClients = (recherche: string) => useQuery({
    queryKey: ["referentiel", "clients", recherche],
    queryFn: () => api<Client[]>(`${BASE}/clients?recherche=${q(recherche)}`), staleTime: CINQ_MINUTES,
})

export const usePoints = () => useQuery({
    queryKey: ["referentiel", "points"], queryFn: () => api<PointLivraison[]>(`${BASE}/points-livraison`), staleTime: CINQ_MINUTES,
})

export const useTypesCamion = () => useQuery({
    queryKey: ["referentiel", "types"], queryFn: () => api<TypeCamion[]>(`${BASE}/types-camion`), staleTime: CINQ_MINUTES,
})

export const useTarifs = (actifsSeulement: boolean) => useQuery({
    queryKey: ["referentiel", "tarifs", actifsSeulement],
    queryFn: () => api<Tarif[]>(`${BASE}/tarifs?actifsSeulement=${actifsSeulement}`), staleTime: CINQ_MINUTES,
})

export const usePrixTarif = (id: number | null) => useQuery({
    queryKey: ["referentiel", "prix", id],
    queryFn: () => api<PrixTarif[]>(`${BASE}/tarifs/${id}/prix`), enabled: id !== null, staleTime: CINQ_MINUTES,
})