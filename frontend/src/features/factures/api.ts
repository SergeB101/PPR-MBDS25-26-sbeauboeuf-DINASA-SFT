import { keepPreviousData, useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"

export interface LigneFacture {
    id: number
    type: "TRANSPORT" | "SERVICE"
    idMouvement: number | null
    numeroBon: string | null
    codeCamion: string | null
    dateLivraison: string | null
    client: string | null
    quantite: number | null
    prixAuGallon: number | null
    montant: number
    description: string | null
    modifieeManuellement: boolean
}

export interface FicheFacture {
    id: number
    numero: string
    codeTransporteur: number
    nomTransporteur: string
    dateGeneration: string
    devise: string
    montant: number
    montantTransport: number
    montantServices: number
    postee: boolean
    nombreLignes: number
    lignes: LigneFacture[]
}

export interface TypeService {
    id: number
    libelle: string
    signe: number
}

export interface EntreeJournal {
    date: string
    utilisateur: string
    action: string
    objetType: string
    objetId: string
    motif: string | null
    avant: string | null
    apres: string | null
}

export interface ReponseSuppression {
    factureSupprimee: boolean
    bonRedevenuFacturable: boolean
    facture: FicheFacture | null
}

export interface Transporteur {
    id: number
    nom: string
    prenom: string | null
}

export interface Criteres {
    du?: string
    au?: string
    transporteur?: number
    postee?: boolean
    page: number
    taille: number
}

export const CLE_FACTURES = "factures"

/** Écart entre le montant de l'en-tête et la somme des lignes (facture à vérifier). */
export function aUnEcart(f: FicheFacture): boolean {
    return Math.abs(f.montant - (f.montantTransport + f.montantServices)) >= 0.01
}

function requete(valeurs: Record<string, string | number | boolean | undefined>): string {
    const p = new URLSearchParams()
    for (const [cle, valeur] of Object.entries(valeurs)) {
        if (valeur !== undefined && valeur !== "") p.set(cle, String(valeur))
    }
    return p.toString()
}

export function useFactures(c: Criteres) {
    return useQuery({
        queryKey: [CLE_FACTURES, "liste", c],
        queryFn: () => api<FicheFacture[]>(`/api/v1/factures?${requete({ ...c })}`),
        placeholderData: keepPreviousData,
    })
}

export function useFacture(id: number) {
    return useQuery({
        queryKey: [CLE_FACTURES, "fiche", id],
        queryFn: () => api<FicheFacture>(`/api/v1/factures/${id}`),
    })
}

export function useHistorique(id: number) {
    return useQuery({
        queryKey: [CLE_FACTURES, "historique", id],
        queryFn: () => api<EntreeJournal[]>(`/api/v1/factures/${id}/historique`),
    })
}

export function useTypesServices(actif: boolean) {
    return useQuery({
        queryKey: ["types-services"],
        queryFn: () => api<TypeService[]>("/api/v1/services/types"),
        enabled: actif,
        staleTime: Infinity,
    })
}

/** La liste des transporteurs vient du référentiel : seulement si l'utilisateur a ce droit. */
export function useTransporteurs(actif: boolean) {
    return useQuery({
        queryKey: ["transporteurs"],
        queryFn: () => api<Transporteur[]>("/api/v1/referentiel/transporteurs"),
        enabled: actif,
        staleTime: 5 * 60_000,
    })
}

export function cheminExportListe(c: Criteres): string {
    return `/api/v1/exports/factures/excel?${requete({ du: c.du, au: c.au, idTransporteur: c.transporteur, postee: c.postee })}`
}

export const actions = {
    poster: (id: number) =>
        api<FicheFacture>(`/api/v1/factures/${id}/poster`, { method: "POST" }),

    corrigerQuantite: (idFacture: number, idLigne: number, quantite: number, motif: string) =>
        api<FicheFacture>(`/api/v1/factures/${idFacture}/lignes/${idLigne}`, {
            method: "PATCH",
            // ⚠ « quantite » : vérifier le nom du champ dans CorrectionLigneController (record DemandeCorrection…)
            body: JSON.stringify({ quantite, motif }),
        }),

    supprimerLigne: (idFacture: number, idLigne: number, motif: string) =>
        api<ReponseSuppression>(`/api/v1/factures/${idFacture}/lignes/${idLigne}?motif=${encodeURIComponent(motif)}`, {
            method: "DELETE",
        }),

    ajouterService: (id: number, demande: { idType: number; montant: number; dateService: string; note?: string }) =>
        api<FicheFacture>(`/api/v1/factures/${id}/services`, { method: "POST", body: JSON.stringify(demande) }),

    incorporerEnAttente: (id: number) =>
        api<{ servicesIncorpores: number; facture: FicheFacture }>(
            `/api/v1/factures/${id}/services/incorporer-en-attente`, { method: "POST" }),
}