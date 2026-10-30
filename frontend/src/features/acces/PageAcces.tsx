import { useState } from "react"
import { useQueryClient } from "@tanstack/react-query"
import { useSession } from "@/auth/session"
import { actionsAcces, CLE_ACCES, useGroupes, usePermissions, useUtilisateurs } from "./api"
import type { MotDePasseTemporaire, Utilisateur } from "./api"
import type { Permission } from "@/lib/types"
import { DialogueAction } from "@/components/DialogueAction"
import { messageErreur } from "@/lib/api"
import { date, heure } from "@/lib/format"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { NativeSelect } from "@/components/ui/native-select"
import { Textarea } from "@/components/ui/textarea"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"

function useRafraichir() {
    const client = useQueryClient()
    return () => client.invalidateQueries({ queryKey: [CLE_ACCES] })
}

/** Le mot de passe temporaire n'est montré qu'une fois : bouton pour le copier. */
function AfficheMotDePasse({ mdp, onFermer }: { mdp: MotDePasseTemporaire; onFermer: () => void }) {
    const [copie, setCopie] = useState(false)
    return (
        <Alert>
            <AlertTitle>Mot de passe temporaire de {mdp.login}</AlertTitle>
            <AlertDescription className="grid gap-2">
                <div className="flex flex-wrap items-center gap-3">
                    <code className="rounded bg-muted px-3 py-1 text-base text-foreground">{mdp.motDePasse}</code>
                    <Button size="sm" variant="outline" onClick={async () => {
                        await navigator.clipboard.writeText(mdp.motDePasse)
                        setCopie(true)
                    }}>{copie ? "Copié ✓" : "Copier"}</Button>
                    <Button size="sm" variant="ghost" onClick={onFermer}>J'ai noté, masquer</Button>
                </div>
                <span>Transmettez-le à l'utilisateur de vive voix. Il ne sera plus affiché ; l'utilisateur devra le changer à sa première connexion.</span>
            </AlertDescription>
        </Alert>
    )
}

function Statut({ u }: { u: Utilisateur }) {
    const bloque = u.bloqueJusqua && new Date(u.bloqueJusqua) > new Date()
    return (
        <span className="flex flex-wrap gap-1">
      {!u.actif && <Badge variant="outline">Désactivé</Badge>}
            {u.actif && !u.compteActive && <Badge variant="secondary">Sans mot de passe</Badge>}
            {u.actif && u.compteActive && u.doitChangerMotDePasse && <Badge variant="secondary">Mot de passe à changer</Badge>}
            {bloque && <Badge variant="destructive">Bloqué jusqu'à {heure(u.bloqueJusqua)}</Badge>}
            {u.actif && u.compteActive && !u.doitChangerMotDePasse && !bloque && <Badge>Actif</Badge>}
    </span>
    )
}

function DialogueNouvelUtilisateur({ onCree }: { onCree: (m: MotDePasseTemporaire) => void }) {
    const rafraichir = useRafraichir()
    const groupes = useGroupes()
    const [login, setLogin] = useState("")
    const [nom, setNom] = useState("")
    const [prenom, setPrenom] = useState("")
    const [idGroupe, setIdGroupe] = useState("")

    return (
        <DialogueAction
            declencheur={<Button>Nouvel utilisateur</Button>}
            onOuvrir={() => { setLogin(""); setNom(""); setPrenom(""); setIdGroupe("") }}
            titre="Créer un utilisateur"
            description="Un mot de passe temporaire sera créé et affiché une seule fois."
            libelle="Créer"
            peutValider={login.trim().length >= 3 && nom.trim().length > 0 && idGroupe !== ""}
            onValider={async () => {
                const m = await actionsAcces.creer({ login: login.trim(), nom: nom.trim(), prenom: prenom.trim(), idGroupe: Number(idGroupe) })
                rafraichir()
                onCree(m)
            }}
        >
            <div className="grid gap-1.5">
                <Label htmlFor="login">Nom d'utilisateur</Label>
                <Input id="login" maxLength={20} value={login} onChange={(e) => setLogin(e.target.value)} placeholder="ex. jdupont" />
                <span className="text-xs text-muted-foreground">3 à 20 caractères : lettres sans accent, chiffres, point, tiret.</span>
            </div>
            <div className="grid grid-cols-2 gap-4">
                <div className="grid gap-1.5">
                    <Label htmlFor="nom">Nom</Label>
                    <Input id="nom" maxLength={50} value={nom} onChange={(e) => setNom(e.target.value)} />
                </div>
                <div className="grid gap-1.5">
                    <Label htmlFor="prenom">Prénom</Label>
                    <Input id="prenom" maxLength={50} value={prenom} onChange={(e) => setPrenom(e.target.value)} />
                </div>
            </div>
            <div className="grid gap-1.5">
                <Label htmlFor="groupe">Groupe</Label>
                <NativeSelect id="groupe" value={idGroupe} onChange={(e) => setIdGroupe(e.target.value)}>
                    <option value="">Choisir…</option>
                    {(groupes.data ?? []).map((g) => <option key={g.id} value={g.id}>{g.libelle}</option>)}
                </NativeSelect>
            </div>
        </DialogueAction>
    )
}

function DialogueModifier({ u, moiMeme }: { u: Utilisateur; moiMeme: boolean }) {
    const rafraichir = useRafraichir()
    const groupes = useGroupes()
    const [idGroupe, setIdGroupe] = useState(String(u.idGroupe))
    const [actif, setActif] = useState(u.actif)

    return (
        <DialogueAction
            declencheur={<Button variant="ghost" size="sm">Modifier</Button>}
            onOuvrir={() => { setIdGroupe(String(u.idGroupe)); setActif(u.actif) }}
            titre={`Modifier ${u.login}`}
            description={moiMeme ? "Vous ne pouvez pas changer votre propre groupe ni vous désactiver." : "Une désactivation déconnecte l'utilisateur immédiatement."}
            libelle="Enregistrer"
            peutValider={!moiMeme}
            onValider={async () => { await actionsAcces.modifier(u.id, Number(idGroupe), actif); rafraichir() }}
        >
            <div className="grid gap-1.5">
                <Label htmlFor="groupe-mod">Groupe</Label>
                <NativeSelect id="groupe-mod" value={idGroupe} disabled={moiMeme} onChange={(e) => setIdGroupe(e.target.value)}>
                    {(groupes.data ?? []).map((g) => <option key={g.id} value={g.id}>{g.libelle}</option>)}
                </NativeSelect>
            </div>
            <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" className="size-4 accent-primary" checked={actif} disabled={moiMeme}
                       onChange={(e) => setActif(e.target.checked)} />
                Compte actif
            </label>
        </DialogueAction>
    )
}

function Utilisateurs({ onMotDePasse }: { onMotDePasse: (m: MotDePasseTemporaire) => void }) {
    const { profil } = useSession()
    const rafraichir = useRafraichir()
    const utilisateurs = useUtilisateurs()
    const [texte, setTexte] = useState("")
    const [inactifs, setInactifs] = useState(false)

    const t = texte.trim().toLowerCase()
    const liste = (utilisateurs.data ?? [])
        .filter((u) => inactifs || u.actif)
        .filter((u) => !t || `${u.login} ${u.nom} ${u.prenom ?? ""}`.toLowerCase().includes(t))

    return (
        <Card>
            <CardHeader>
                <CardTitle>Utilisateurs</CardTitle>
                <CardDescription>Les mêmes fiches que l'ancienne application ; seuls les mots de passe sont propres à la nouvelle.</CardDescription>
            </CardHeader>
            <CardContent className="grid gap-3">
                <div className="flex flex-wrap items-center gap-3">
                    <Input className="max-w-xs" placeholder="Rechercher…" value={texte} onChange={(e) => setTexte(e.target.value)} />
                    <label className="flex items-center gap-2 text-sm">
                        <input type="checkbox" className="size-4 accent-primary" checked={inactifs} onChange={(e) => setInactifs(e.target.checked)} />
                        Afficher les comptes désactivés
                    </label>
                    <div className="ml-auto"><DialogueNouvelUtilisateur onCree={onMotDePasse} /></div>
                </div>
                {utilisateurs.error && <Alert variant="destructive"><AlertDescription>{messageErreur(utilisateurs.error)}</AlertDescription></Alert>}
                <Table>
                    <TableHeader><TableRow>
                        <TableHead>Utilisateur</TableHead><TableHead>Nom</TableHead><TableHead>Groupe</TableHead>
                        <TableHead>État</TableHead><TableHead>Dernière connexion</TableHead><TableHead />
                    </TableRow></TableHeader>
                    <TableBody>
                        {liste.map((u) => {
                            const moiMeme = u.login.toLowerCase() === profil?.login.toLowerCase()
                            return (
                                <TableRow key={u.id} className={u.actif ? "" : "opacity-60"}>
                                    <TableCell className="font-mono">{u.login} {moiMeme && <Badge variant="outline">vous</Badge>}</TableCell>
                                    <TableCell>{u.nom} {u.prenom}</TableCell>
                                    <TableCell>{u.groupe}</TableCell>
                                    <TableCell><Statut u={u} /></TableCell>
                                    <TableCell>{u.derniereConnexion ? `${date(u.derniereConnexion)} ${heure(u.derniereConnexion)}` : "—"}</TableCell>
                                    <TableCell className="text-right">
                                        <DialogueModifier u={u} moiMeme={moiMeme} />
                                        {u.actif && (
                                            <DialogueAction
                                                declencheur={<Button variant="ghost" size="sm">Mot de passe temporaire</Button>}
                                                titre={`Nouveau mot de passe temporaire pour ${u.login} ?`}
                                                description="L'ancien mot de passe ne fonctionnera plus et l'utilisateur sera déconnecté. Il devra choisir un nouveau mot de passe à la connexion."
                                                libelle="Créer le mot de passe"
                                                onValider={async () => { onMotDePasse(await actionsAcces.motDePasseTemporaire(u.id)); rafraichir() }}
                                            />
                                        )}
                                        {u.compteActive && !moiMeme && (
                                            <DialogueAction
                                                declencheur={<Button variant="ghost" size="sm">Déconnecter</Button>}
                                                titre={`Déconnecter ${u.login} ?`}
                                                description="Ses sessions ouvertes sont fermées immédiatement. Il pourra se reconnecter avec son mot de passe."
                                                libelle="Déconnecter"
                                                onValider={async () => { await actionsAcces.deconnecter(u.id); rafraichir() }}
                                            />
                                        )}
                                    </TableCell>
                                </TableRow>
                            )
                        })}
                    </TableBody>
                </Table>
            </CardContent>
        </Card>
    )
}

function Droits() {
    const rafraichir = useRafraichir()
    const groupes = useGroupes()
    const permissions = usePermissions()
    const [idGroupe, setIdGroupe] = useState<number | null>(null)
    const [coches, setCoches] = useState<Set<Permission>>(new Set())
    const [motif, setMotif] = useState("")
    const [envoi, setEnvoi] = useState(false)
    const [retour, setRetour] = useState<{ ok: boolean; texte: string } | null>(null)
    const groupe = groupes.data?.find((g) => g.id === idGroupe)

    const choisir = (id: number) => {
        setIdGroupe(id)
        setCoches(new Set(groupes.data?.find((g) => g.id === id)?.permissions ?? []))
        setMotif("")
        setRetour(null)
    }

    const enregistrer = async () => {
        if (idGroupe === null) return
        setEnvoi(true)
        setRetour(null)
        try {
            await actionsAcces.droits(idGroupe, [...coches], motif.trim())
            rafraichir()
            setRetour({ ok: true, texte: "Droits enregistrés. Ils s'appliquent immédiatement aux membres du groupe." })
        } catch (e) {
            setRetour({ ok: false, texte: messageErreur(e) })
        } finally {
            setEnvoi(false)
        }
    }

    return (
        <Card>
            <CardHeader>
                <CardTitle>Droits des groupes</CardTitle>
                <CardDescription>Choisissez un groupe, cochez ses droits, puis enregistrez.</CardDescription>
            </CardHeader>
            <CardContent className="grid gap-4 lg:grid-cols-[18rem_1fr]">
                <div className="grid content-start gap-1">
                    {(groupes.data ?? []).map((g) => (
                        <button key={g.id} type="button" onClick={() => choisir(g.id)}
                                className={`flex justify-between rounded-md px-3 py-2 text-left text-sm ${g.id === idGroupe ? "bg-primary text-primary-foreground" : "hover:bg-muted"}`}>
                            <span>{g.libelle}</span>
                            <span className="opacity-70">{g.permissions.length} droit(s) · {g.utilisateursActifs} pers.</span>
                        </button>
                    ))}
                </div>
                {groupe ? (
                    <div className="grid content-start gap-3">
                        <div className="grid gap-2 sm:grid-cols-2">
                            {(permissions.data ?? []).map((p) => (
                                <label key={p.code} className="flex items-start gap-2 rounded-md border p-2 text-sm">
                                    <input type="checkbox" className="mt-0.5 size-4 accent-primary" checked={coches.has(p.code)}
                                           onChange={() => {
                                               const s = new Set(coches)
                                               if (s.has(p.code)) s.delete(p.code)
                                               else s.add(p.code)
                                               setCoches(s)
                                           }} />
                                    <span>{p.libelle}</span>
                                </label>
                            ))}
                        </div>
                        <div className="grid gap-1.5">
                            <Label htmlFor="motif-droits">Motif (gardé dans le journal)</Label>
                            <Textarea id="motif-droits" maxLength={500} value={motif} onChange={(e) => setMotif(e.target.value)} />
                        </div>
                        {retour && (
                            <Alert variant={retour.ok ? "default" : "destructive"}><AlertDescription>{retour.texte}</AlertDescription></Alert>
                        )}
                        <Button className="w-fit" onClick={enregistrer} disabled={envoi}>
                            {envoi ? "Enregistrement…" : `Enregistrer les droits de « ${groupe.libelle} »`}
                        </Button>
                    </div>
                ) : (
                    <p className="text-sm text-muted-foreground">Aucun groupe choisi.</p>
                )}
            </CardContent>
        </Card>
    )
}

export default function PageAcces() {
    const [mdp, setMdp] = useState<MotDePasseTemporaire | null>(null)
    return (
        <div className="grid gap-4">
            <h1 className="text-2xl font-semibold">Gestion des accès</h1>
            {mdp && <AfficheMotDePasse mdp={mdp} onFermer={() => setMdp(null)} />}
            <Utilisateurs onMotDePasse={setMdp} />
            <Droits />
        </div>
    )
}