const nombres = new Map<number, Intl.NumberFormat>()

/** 1 234 567,89 — format français. */
export function nombre(valeur: number | null | undefined, decimales = 0): string {
    if (valeur === null || valeur === undefined) return "—"
    let f = nombres.get(decimales)
    if (!f) {
        f = new Intl.NumberFormat("fr-FR", { minimumFractionDigits: decimales, maximumFractionDigits: decimales })
        nombres.set(decimales, f)
    }
    return f.format(valeur)
}

export function montant(valeur: number | null | undefined, devise = "USD"): string {
    return valeur === null || valeur === undefined ? "—" : `${nombre(valeur, 2)} ${devise}`
}

/** "2026-04-22" ou "2026-04-22T10:15:00" → 22/04/2026 */
export function date(iso: string | null | undefined): string {
    if (!iso) return "—"
    const [a, m, j] = iso.slice(0, 10).split("-")
    return `${j}/${m}/${a}`
}

export function heure(iso: string | null | undefined): string {
    return iso ? iso.slice(11, 16) : "—"
}

export const NOMS_MOIS = Array.from({ length: 12 }, (_, i) =>
    new Date(2000, i, 1).toLocaleString("fr-FR", { month: "long" }),
)

export function moisCourt(mois: number): string {
    return new Date(2000, mois - 1, 1).toLocaleString("fr-FR", { month: "short" })
}