export type Permission =
    | "FACTURES_CONSULTER" | "FACTURES_GENERER" | "FACTURES_MODIFIER" | "FACTURES_POSTER"
    | "ACCPAC_CONSULTER" | "ACCPAC_RECUPERER" | "ACCPAC_CORRIGER" | "ACCPAC_ANNULER"
    | "REFERENTIEL_CONSULTER" | "TABLEAU_DE_BORD_CONSULTER" | "SECURITE_ADMINISTRER"

export interface ResultatConnexion {
    jeton: string | null
    expireA: string | null
    doitChangerMotDePasse: boolean
    login: string
    nom: string
    permissions: Permission[]
}

export interface Profil {
    login: string
    nom: string
    idGroupe: number
    permissions: Permission[]
}