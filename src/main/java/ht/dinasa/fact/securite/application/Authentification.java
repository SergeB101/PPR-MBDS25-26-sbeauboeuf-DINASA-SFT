package ht.dinasa.fact.securite.application;

import ht.dinasa.fact.securite.application.ConnexionRefusee.Raison;
import ht.dinasa.fact.securite.application.DepotComptes.Compte;
import ht.dinasa.fact.securite.domain.Permission;
import ht.dinasa.fact.securite.domain.PolitiqueMotDePasse;
import ht.dinasa.fact.shared.erreurs.ErreurMetier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class Authentification {

    public record ResultatConnexion(String jeton, Instant expireA, boolean doitChangerMotDePasse,
                                    String login, String nom, Set<Permission> permissions) {
    }

    public record Profil(String login, String nom, long idGroupe, Set<Permission> permissions) {
    }

    private final DepotComptes comptes;
    private final DepotPermissions permissions;
    private final EmetteurJetons emetteur;
    private final PasswordEncoder encodeur;
    private final ProprietesSecurite proprietes;
    private final Clock horloge;
    private final String empreinteFactice;

    public Authentification(DepotComptes comptes, DepotPermissions permissions, EmetteurJetons emetteur,
                            PasswordEncoder encodeur, ProprietesSecurite proprietes, Clock horloge) {
        this.comptes = comptes;
        this.permissions = permissions;
        this.emetteur = emetteur;
        this.encodeur = encodeur;
        this.proprietes = proprietes;
        this.horloge = horloge;
        // Même temps de réponse que le login existe ou non.
        this.empreinteFactice = encodeur.encode("factice-" + UUID.randomUUID());
    }

    /** Les échecs et le journal doivent rester enregistrés même si la connexion est refusée. */
    @Transactional(noRollbackFor = ConnexionRefusee.class)
    public ResultatConnexion connecter(String login, String motDePasse, String ip) {
        Compte c = verifier(login, motDePasse, ip);
        if (c.doitChanger()) {
            comptes.journaliserConnexion(c.login(), "MOT_DE_PASSE_A_CHANGER", ip(ip), maintenant());
            return new ResultatConnexion(null, null, true, c.login(), c.nomComplet(), Set.of());
        }
        comptes.journaliserConnexion(c.login(), "SUCCES", ip(ip), maintenant());
        return ouvrirSession(c);
    }

    @Transactional(noRollbackFor = ConnexionRefusee.class)
    public ResultatConnexion changerMotDePasse(String login, String ancien, String nouveau, String ip) {
        Compte c = verifier(login, ancien, ip);
        PolitiqueMotDePasse.valider(nouveau);
        if (encodeur.matches(nouveau, c.motDePasse())) {
            throw new ErreurMetier("MEME_MOT_DE_PASSE", "Le nouveau mot de passe doit être différent de l'ancien.");
        }
        LocalDateTime le = maintenant();
        comptes.enregistrerMotDePasse(c.id(), encodeur.encode(nouveau), false, le, c.login());
        comptes.journaliserConnexion(c.login(), "MOT_DE_PASSE_CHANGE", ip(ip), le);
        return ouvrirSession(comptes.parId(c.id()).orElseThrow());
    }

    public Profil profil(String login) {
        Compte c = comptes.parLogin(login).orElseThrow(() ->
                new ErreurMetier("UTILISATEUR_INTROUVABLE", "Utilisateur introuvable : " + login));
        return new Profil(c.login(), c.nomComplet(), c.idGroupe(), permissions.duGroupe(c.idGroupe()));
    }

    /**
     * Appelé à CHAQUE requête : un compte désactivé, des droits modifiés ou une déconnexion forcée
     * prennent effet immédiatement.
     */
    public Optional<Set<Permission>> permissionsSiJetonValide(String login, int version) {
        return comptes.parLogin(login)
                .filter(c -> c.actif() && c.motDePasse() != null && !c.doitChanger() && c.versionJeton() == version)
                .map(c -> permissions.duGroupe(c.idGroupe()));
    }

    private ResultatConnexion ouvrirSession(Compte c) {
        EmetteurJetons.Jeton jeton = emetteur.emettre(c.login(), c.versionJeton(), horloge.instant());
        return new ResultatConnexion(jeton.valeur(), jeton.expireA(), false, c.login(), c.nomComplet(),
                permissions.duGroupe(c.idGroupe()));
    }

    private Compte verifier(String login, String motDePasse, String ip) {
        String l = login == null ? "" : login.trim();
        String mdp = motDePasse == null ? "" : motDePasse;
        LocalDateTime le = maintenant();

        Compte c = comptes.parLogin(l).orElse(null);
        if (c == null) {
            encodeur.matches(mdp, empreinteFactice);
            throw refus(l, ip, Raison.IDENTIFIANTS_INCORRECTS);
        }
        if (c.bloqueJusqua() != null && c.bloqueJusqua().isAfter(le)) {
            throw refus(c.login(), ip, Raison.COMPTE_BLOQUE);
        }
        if (c.motDePasse() == null) {
            encodeur.matches(mdp, empreinteFactice);
            throw refus(c.login(), ip, Raison.COMPTE_NON_ACTIVE);
        }
        if (!encodeur.matches(mdp, c.motDePasse())) {
            int echecs = c.echecs() + 1;
            boolean bloquer = echecs >= proprietes.tentativesMax();
            comptes.enregistrerEchec(c.id(), bloquer ? 0 : echecs, bloquer, le.plus(proprietes.dureeBlocage()));
            throw refus(c.login(), ip, bloquer ? Raison.COMPTE_BLOQUE : Raison.IDENTIFIANTS_INCORRECTS);
        }
        if (!c.actif()) {
            throw refus(c.login(), ip, Raison.COMPTE_INACTIF);
        }
        comptes.enregistrerSucces(c.id(), le, ip(ip));
        return c;
    }

    private ConnexionRefusee refus(String login, String ip, Raison raison) {
        String l = login.length() > 50 ? login.substring(0, 50) : login;
        comptes.journaliserConnexion(l, "REFUS_" + raison.name(), ip(ip), maintenant());
        return new ConnexionRefusee(raison);
    }

    private LocalDateTime maintenant() {
        return LocalDateTime.now(horloge).truncatedTo(ChronoUnit.SECONDS);
    }

    private static String ip(String ip) {
        if (ip == null) return "";
        return ip.length() > 45 ? ip.substring(0, 45) : ip;
    }
}