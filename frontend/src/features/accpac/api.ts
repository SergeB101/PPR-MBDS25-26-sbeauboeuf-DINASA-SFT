import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"

export type TypeAnomalie =
    | "NUMERO_FACTURE_ABSENT" | "CAMION_NON_PRECISE" | "CAMION_INCONNU"
    | "CLIENT_INCONNU" | "TRANSPORTEUR_INCONNU" | "PRODUIT_INCONNU"

export const ANOMALIES: Record<TypeAnomalie, { libelle: string; bloquante: boolean }> = {
    NUMERO_FACTURE_ABSENT: { libelle: "N° de facture absent dans ACCPAC", bloquante: true },
    CAMION_NON_PRECISE: { libelle: "Camion non précisé dans ACCPAC", bloquante: true },
    CAMION_INCONNU: { libelle: "Camion inconnu dans la base", bloquante: true },
    CLIENT_INCONNU: { libelle: "Client inconnu dans la base", bloquante: true },
    TRANSPORTEUR_INCONNU: { libelle: "Camion sans transporteur connu", bloquante: true },
    PRODUIT_INCONNU: { libelle: "Produit inconnu (non bloquant)", bloquante: false },
}

export interface LigneAnomalie {
    idLigne: number
    noFacture: string | null
    date: string
    codeCamion: string | null
    codeClient: string | null
    shipTo: string | null
    codeProduit: string | null
    volume: number | null
    anomalies: TypeAnomalie[]
}

export interface CodeEnDouble {
    code: string
    fiches: number
    valeursDifferentes: boolean
    detail: string
}

export interface TableauAnomalies {
    debut: string
    fin: string
    lignesARecuperer: number
    bonsPrevus: number
    anomaliesParType: Partial<Record<TypeAnomalie, number>>
    lignes: LigneAnomalie[]
    camionsEnDouble: CodeEnDouble[]
    clientsEnDouble: CodeEnDouble[]
}

export interface Bilan {
    periode: { debut: string; fin: string }
    bonsCrees: number
    bonsOT: number
    lignesDetail: number
    lignesMarquees: number
    bonsAvecClientFacture: number
    anomaliesRestantes: Partial<Record<TypeAnomalie, number>>
}

export interface BilanAnnulation {
    bonsSupprimes: number
    detailsSupprimes: number
    lignesReinitialisees: number
}

export const CLE_ACCPAC = "accpac"

export function useTableauAnomalies(annee: number, mois: number, inclurePrecedent: boolean) {
    return useQuery({
        queryKey: [CLE_ACCPAC, annee, mois, inclurePrecedent],
        queryFn: () => api<TableauAnomalies>(
            `/api/v1/accpac/anomalies?annee=${annee}&mois=${mois}&inclureMoisPrecedent=${inclurePrecedent}`),
    })
}

export const actionsAccpac = {
    recuperer: (annee: number, mois: number, inclurePrecedent: boolean) =>
        api<Bilan>(`/api/v1/accpac/recuperation?annee=${annee}&mois=${mois}&inclureMoisPrecedent=${inclurePrecedent}`,
            { method: "POST" }),

    corriger: (idLigne: number, type: "CAMION" | "CLIENT", nouveauCode: string, motif: string) =>
        api<{ lignesCorrigees: number }>(`/api/v1/accpac/anomalies/${idLigne}/correction`, {
            method: "POST",
            body: JSON.stringify({ type, nouveauCode, motif }),
        }),

    annuler: (annee: number, mois: number, motif: string) =>
        api<BilanAnnulation>(`/api/v1/accpac/annulation?annee=${annee}&mois=${mois}&motif=${encodeURIComponent(motif)}`,
            { method: "POST" }),
}

/** Quel(s) code(s) peut-on corriger sur cette ligne ? */
export function correctionsPossibles(l: LigneAnomalie): ("CAMION" | "CLIENT")[] {
    const types: ("CAMION" | "CLIENT")[] = []
    if (l.anomalies.some((a) => a === "CAMION_INCONNU" || a === "CAMION_NON_PRECISE" || a === "TRANSPORTEUR_INCONNU")) {
        types.push("CAMION")
    }
    if (l.anomalies.includes("CLIENT_INCONNU")) types.push("CLIENT")
    return types
}