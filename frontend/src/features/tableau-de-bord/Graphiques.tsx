import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts"
import type { VolumeMois } from "./api"
import { moisCourt, nombre } from "@/lib/format"

export function GraphiqueVolumes({ donnees }: { donnees: VolumeMois[] }) {
    const lignes = donnees.map((d) => ({ ...d, libelle: moisCourt(d.mois) }))
    return (
        <ResponsiveContainer width="100%" height={280}>
            <BarChart data={lignes} margin={{ top: 8, right: 8, left: 8, bottom: 0 }}>
                <CartesianGrid vertical={false} strokeDasharray="3 3" />
                <XAxis dataKey="libelle" tickLine={false} axisLine={false} />
                <YAxis tickLine={false} axisLine={false} width={70}
                       tickFormatter={(v) => nombre(Number(v) / 1000) + " k"} />
                <Tooltip formatter={(v) => nombre(Number(v)) + " gal"} />
                <Legend />
                <Bar dataKey="volumeLivre" name="Volume livré" fill="var(--color-chart-2)" radius={[4, 4, 0, 0]} />
                <Bar dataKey="quantiteFacturee" name="Quantité facturée" fill="var(--color-chart-1)" radius={[4, 4, 0, 0]} />
            </BarChart>
        </ResponsiveContainer>
    )
}

export function GraphiqueCouts({ donnees }: { donnees: VolumeMois[] }) {
    const lignes = donnees.map((d) => ({ ...d, libelle: moisCourt(d.mois) }))
    return (
        <ResponsiveContainer width="100%" height={280}>
            <BarChart data={lignes} margin={{ top: 8, right: 8, left: 8, bottom: 0 }}>
                <CartesianGrid vertical={false} strokeDasharray="3 3" />
                <XAxis dataKey="libelle" tickLine={false} axisLine={false} />
                <YAxis tickLine={false} axisLine={false} width={70}
                       tickFormatter={(v) => nombre(Number(v) / 1000) + " k"} />
                <Tooltip formatter={(v) => nombre(Number(v), 2) + " USD"} />
                <Bar dataKey="montant" name="Coût du transport" fill="var(--color-chart-3)" radius={[4, 4, 0, 0]} />
            </BarChart>
        </ResponsiveContainer>
    )
}