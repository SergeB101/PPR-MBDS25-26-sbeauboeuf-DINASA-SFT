import { useState } from "react"
import type { FormEvent } from "react"
import { Navigate, useLocation, useNavigate } from "react-router"
import { useSession } from "@/auth/session"
import { messageErreur } from "@/lib/api"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"

const REGLES = [
    { texte: "8 caractères au moins", ok: (m: string) => m.length >= 8 },
    { texte: "une majuscule", ok: (m: string) => /[A-Z]/.test(m) },
    { texte: "une minuscule", ok: (m: string) => /[a-z]/.test(m) },
    { texte: "un chiffre", ok: (m: string) => /[0-9]/.test(m) },
    { texte: "un caractère spécial", ok: (m: string) => /[^a-zA-Z0-9]/.test(m) },
]

export default function Connexion() {
    const { profil, connecter, changerMotDePasse } = useSession()
    const navigate = useNavigate()
    const location = useLocation()
    const destination = (location.state as { depuis?: string } | null)?.depuis ?? "/"

    const [login, setLogin] = useState("")
    const [motDePasse, setMotDePasse] = useState("")
    const [nouveau, setNouveau] = useState("")
    const [confirmation, setConfirmation] = useState("")
    const [aChanger, setAChanger] = useState(false)
    const [erreur, setErreur] = useState<string | null>(null)
    const [envoi, setEnvoi] = useState(false)

    if (profil) return <Navigate to={destination} replace />

    const seConnecter = async (e: FormEvent) => {
        e.preventDefault()
        setErreur(null)
        setEnvoi(true)
        try {
            const r = await connecter(login.trim(), motDePasse)
            if (r.doitChangerMotDePasse) setAChanger(true)
            else navigate(destination, { replace: true })
        } catch (err) {
            setErreur(messageErreur(err))
        } finally {
            setEnvoi(false)
        }
    }

    const changer = async (e: FormEvent) => {
        e.preventDefault()
        setErreur(null)
        if (nouveau !== confirmation) {
            setErreur("Les deux mots de passe ne sont pas identiques.")
            return
        }
        setEnvoi(true)
        try {
            await changerMotDePasse(login.trim(), motDePasse, nouveau)
            navigate(destination, { replace: true })
        } catch (err) {
            setErreur(messageErreur(err))
        } finally {
            setEnvoi(false)
        }
    }

    return (
        <div className="flex min-h-svh items-center justify-center bg-muted p-4">
            <Card className="w-full max-w-sm">
                <CardHeader>
                    <CardTitle>DINASA – Facturation transporteurs</CardTitle>
                    <CardDescription>
                        {aChanger ? "Première connexion : choisissez votre mot de passe." : "Connectez-vous avec votre compte."}
                    </CardDescription>
                </CardHeader>
                <CardContent>
                    {!aChanger ? (
                        <form onSubmit={seConnecter} className="grid gap-4">
                            <div className="grid gap-2">
                                <Label htmlFor="login">Nom d'utilisateur</Label>
                                <Input id="login" autoComplete="username" autoFocus required
                                       value={login} onChange={(e) => setLogin(e.target.value)} />
                            </div>
                            <div className="grid gap-2">
                                <Label htmlFor="mdp">Mot de passe</Label>
                                <Input id="mdp" type="password" autoComplete="current-password" required
                                       value={motDePasse} onChange={(e) => setMotDePasse(e.target.value)} />
                            </div>
                            {erreur && <Alert variant="destructive"><AlertDescription>{erreur}</AlertDescription></Alert>}
                            <Button type="submit" disabled={envoi}>{envoi ? "Connexion…" : "Se connecter"}</Button>
                        </form>
                    ) : (
                        <form onSubmit={changer} className="grid gap-4">
                            <div className="grid gap-2">
                                <Label htmlFor="nouveau">Nouveau mot de passe</Label>
                                <Input id="nouveau" type="password" autoComplete="new-password" autoFocus required
                                       value={nouveau} onChange={(e) => setNouveau(e.target.value)} />
                            </div>
                            <ul className="grid gap-1 text-sm">
                                {REGLES.map((r) => (
                                    <li key={r.texte} className={r.ok(nouveau) ? "text-green-700" : "text-muted-foreground"}>
                                        {r.ok(nouveau) ? "✓" : "•"} {r.texte}
                                    </li>
                                ))}
                            </ul>
                            <div className="grid gap-2">
                                <Label htmlFor="confirmation">Confirmer le mot de passe</Label>
                                <Input id="confirmation" type="password" autoComplete="new-password" required
                                       value={confirmation} onChange={(e) => setConfirmation(e.target.value)} />
                            </div>
                            {erreur && <Alert variant="destructive"><AlertDescription>{erreur}</AlertDescription></Alert>}
                            <Button type="submit" disabled={envoi || !REGLES.every((r) => r.ok(nouveau))}>
                                {envoi ? "Enregistrement…" : "Enregistrer et entrer"}
                            </Button>
                        </form>
                    )}
                </CardContent>
            </Card>
        </div>
    )
}