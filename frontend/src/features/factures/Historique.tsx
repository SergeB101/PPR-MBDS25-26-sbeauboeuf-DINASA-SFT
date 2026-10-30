import { useHistorique } from "./api"
import { date, heure } from "@/lib/format"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"

const LIBELLES: Record<string, string> = {
    FACTURE_GENEREE: "Facture générée",
    FACTURE_POSTEE: "Facture postée",
    SERVICE_AJOUTE: "Service ajouté",
    SERVICE_INCORPORE: "Services repris",
    FACTURE_LIGNE_CORRIGEE: "Quantité corrigée",
    FACTURE_LIGNE_SUPPRIMEE: "Ligne supprimée",
}

/** Le journal garde « avant » et « après » en JSON : on les rend lisibles. */
function resume(json: string | null): string {
    if (!json) return ""
    try {
        return Object.entries(JSON.parse(json) as Record<string, unknown>)
            .map(([cle, valeur]) => `${cle} : ${typeof valeur === "object" ? JSON.stringify(valeur) : String(valeur)}`)
            .join(" · ")
    } catch {
        return json
    }
}

export function Historique({ idFacture }: { idFacture: number }) {
    const historique = useHistorique(idFacture)
    const entrees = historique.data ?? []

    if (historique.isLoading) return <p className="text-sm text-muted-foreground">Chargement…</p>
    if (entrees.length === 0) {
        return <p className="text-sm text-muted-foreground">
            Aucune opération enregistrée par la nouvelle application (les modifications faites dans l'ancienne n'y figurent pas).
        </p>
    }
    return (
        <Table>
            <TableHeader>
                <TableRow>
                    <TableHead>Date</TableHead>
                    <TableHead>Utilisateur</TableHead>
                    <TableHead>Opération</TableHead>
                    <TableHead>Motif</TableHead>
                    <TableHead>Avant</TableHead>
                    <TableHead>Après</TableHead>
                </TableRow>
            </TableHeader>
            <TableBody>
                {entrees.map((e, i) => (
                    <TableRow key={i}>
                        <TableCell>{date(e.date)} {heure(e.date)}</TableCell>
                        <TableCell>{e.utilisateur}</TableCell>
                        <TableCell>{LIBELLES[e.action] ?? e.action}</TableCell>
                        <TableCell className="max-w-64 whitespace-normal">{e.motif ?? ""}</TableCell>
                        <TableCell className="max-w-64 whitespace-normal text-xs">{resume(e.avant)}</TableCell>
                        <TableCell className="max-w-64 whitespace-normal text-xs">{resume(e.apres)}</TableCell>
                    </TableRow>
                ))}
            </TableBody>
        </Table>
    )
}