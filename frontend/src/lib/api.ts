const CLE_JETON = "dinasa.jeton"

/** Le jeton est gardé pour l'onglet en cours seulement : il disparaît à la fermeture du navigateur. */
export function lireJeton(): string | null {
    return sessionStorage.getItem(CLE_JETON)
}

export function enregistrerJeton(jeton: string | null): void {
    if (jeton) sessionStorage.setItem(CLE_JETON, jeton)
    else sessionStorage.removeItem(CLE_JETON)
}

export class ErreurApi extends Error {
    readonly statut: number
    readonly code?: string

    constructor(statut: number, message: string, code?: string) {
        super(message)
        this.statut = statut
        this.code = code
    }
}

let surSessionExpiree: () => void = () => {}

export function quandSessionExpiree(action: () => void): void {
    surSessionExpiree = action
}

async function appeler(chemin: string, options: RequestInit = {}): Promise<Response> {
    const entetes = new Headers(options.headers)
    const jeton = lireJeton()
    if (jeton) entetes.set("Authorization", `Bearer ${jeton}`)
    if (options.body && !entetes.has("Content-Type")) entetes.set("Content-Type", "application/json")

    let reponse: Response
    try {
        reponse = await fetch(chemin, { ...options, headers: entetes })
    } catch {
        throw new ErreurApi(0, "Le serveur ne répond pas. Vérifiez que le backend est démarré.")
    }

    if (reponse.status === 401 && jeton) {
        enregistrerJeton(null)
        surSessionExpiree()
        throw new ErreurApi(401, "Votre session a expiré : reconnectez-vous.")
    }
    if (!reponse.ok) {
        let message = `Erreur ${reponse.status}`
        let code: string | undefined
        try {
            const probleme = await reponse.json()
            message = probleme.detail ?? probleme.title ?? message
            code = probleme.code
        } catch {
            /* réponse sans contenu */
        }
        if (reponse.status === 403) message = "Vous n'avez pas le droit de faire cette opération."
        throw new ErreurApi(reponse.status, message, code)
    }
    return reponse
}

export async function api<T>(chemin: string, options: RequestInit = {}): Promise<T> {
    const texte = await (await appeler(chemin, options)).text()
    return (texte ? JSON.parse(texte) : undefined) as T
}

/** Télécharge un fichier (PDF, Excel) en envoyant le jeton, avec le nom donné par le serveur. */
export async function telecharger(chemin: string): Promise<void> {
    const reponse = await appeler(chemin)
    const dispo = reponse.headers.get("Content-Disposition") ?? ""
    const nomEncode = /filename\*=UTF-8''([^;]+)/i.exec(dispo)?.[1]
    const nom = nomEncode ? decodeURIComponent(nomEncode) : /filename="?([^";]+)"?/i.exec(dispo)?.[1] ?? "export"

    const url = URL.createObjectURL(await reponse.blob())
    const lien = document.createElement("a")
    lien.href = url
    lien.download = nom
    document.body.appendChild(lien)
    lien.click()
    lien.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function messageErreur(e: unknown): string {
    return e instanceof Error ? e.message : "Erreur inattendue."
}