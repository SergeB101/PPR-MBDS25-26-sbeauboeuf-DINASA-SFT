import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"

export type MotifRejet =
    | "CLIENT_SANS_POINT_LIVRAISON"
    | "CAMION_PAS_AU_GALLON"
    | "AUCUN_TARIF_ACTIF"
    | "PAS_DE_PRIX_POUR_CE_POINT"

export interface LigneCalculee {
    idMouvement: number
    numeroBon: string
    codeCamion: string
    quantiteCommandee: number
    prixAuGallon: number
    montant: { valeur: number; devise: string }
}

export interface FactureCalculee {
    numero: string
    codeTransporteur: number
    dateGeneration: string
    lignes: LigneCalculee[]
}

export interface Rejet {
    idMouvement: number
    numeroBon: string
    motif: MotifRejet
}

export interface ResultatCalcul {
    factures: FactureCalculee[]
    rejets: Rejet[]
}

export const MOTIFS: Record<MotifRejet, { libelle: string; quoiFaire: string }> = {
    CLIENT_SANS_POINT_LIVRAISON: {
        libelle: "Le client n'a pas de point de livraison",
        quoiFaire: "Renseigner le point de livraison sur la fiche du client.",
    },
    CAMION_PAS_AU_GALLON: {
        libelle: "Camion facturé en fixe + variable",
        quoiFaire: "Ces bons sont facturés hors de cette application (méthode fixe + variable).",
    },
    AUCUN_TARIF_ACTIF: {
        libelle: "Aucun tarif actif pour le type du camion",
        quoiFaire: "Activer une grille de tarifs pour ce type de camion, ou corriger le type du camion.",
    },
    PAS_DE_PRIX_POUR_CE_POINT: {
        libelle: "Pas de prix pour ce point de livraison",
        quoiFaire: "Ajouter le prix de ce point de livraison dans la grille de tarifs active.",
    },
}

export function totalFacture(f: FactureCalculee) {
    return {
        bons: f.lignes.length,
        gallons: f.lignes.reduce((s, l) => s + l.quantiteCommandee, 0),
        montant: f.lignes.reduce((s, l) => s + l.montant.valeur, 0),
        devise: f.lignes[0]?.montant.devise ?? "USD",
    }
}

export function useApercu(annee: number, mois: number) {
    return useQuery({
        queryKey: ["apercu", annee, mois],
        queryFn: () => api<ResultatCalcul>(`/api/v1/facturation/apercu?annee=${annee}&mois=${mois}`),
    })
}

/** idsBons absent = tous les bons du mois. */
export function generer(annee: number, mois: number, idsBons?: number[]) {
    return api<ResultatCalcul>(`/api/v1/facturation/generation?annee=${annee}&mois=${mois}`, {
        method: "POST",
        body: idsBons ? JSON.stringify(idsBons) : undefined,
    })
}