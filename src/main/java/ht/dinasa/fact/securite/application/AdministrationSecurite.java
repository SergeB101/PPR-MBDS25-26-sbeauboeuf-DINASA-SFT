package ht.dinasa.fact.securite.application;

import ht.dinasa.fact.securite.application.DepotComptes.Compte;
import ht.dinasa.fact.securite.application.DepotComptes.UtilisateurVue;
import ht.dinasa.fact.securite.application.DepotPermissions.GroupeVue;
import ht.dinasa.fact.securite.domain.Permission;
import ht.dinasa.fact.securite.domain.PolitiqueMotDePasse;
import ht.dinasa.fact.shared.UtilisateurCourant;
import ht.dinasa.fact.shared.audit.EntreeAudit;
import ht.dinasa.fact.shared.audit.Journal;
import ht.dinasa.fact.shared.erreurs.ErreurMetier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AdministrationSecurite {

    public record MotDePasseTemporaire(long idUtilisateur, String login, String motDePasse) {
    }

    public record PermissionVue(String code, String libelle) {
    }

    private final DepotComptes comptes;
    private final DepotPermissions permissions;
    private final PasswordEncoder encodeur;
    private final Journal journal;
    private final UtilisateurCourant utilisateur;
    private final Clock horloge;

    public AdministrationSecurite(DepotComptes comptes, DepotPermissions permissions, PasswordEncoder encodeur,
                                  Journal journal, UtilisateurCourant utilisateur, Clock horloge) {
        this.comptes = comptes;
        this.permissions = permissions;
        this.encodeur = encodeur;
        this.journal = journal;
        this.utilisateur = utilisateur;
        this.horloge = horloge;
    }

    public List<UtilisateurVue> utilisateurs() {
        return comptes.utilisateurs();
    }

    public List<GroupeVue> groupes() {
        return permissions.groupes();
    }

    public List<PermissionVue> permissions() {
        return Arrays.stream(Permission.values()).map(p -> new PermissionVue(p.name(), p.libelle())).toList();
    }

    /** Le mot de passe temporaire n'est affiché qu'UNE fois ; l'utilisateur devra le changer. */
    @Transactional
    public MotDePasseTemporaire motDePasseTemporaire(long idUtilisateur) {
        Compte c = compte(idUtilisateur);
        if (!c.actif()) {
            throw new ErreurMetier("UTILISATEUR_INACTIF", "Cet utilisateur est désactivé : réactivez-le d'abord.");
        }
        String temporaire = PolitiqueMotDePasse.temporaire();
        comptes.enregistrerMotDePasse(c.id(), encodeur.encode(temporaire), true, maintenant(), utilisateur.login());
        journal.enregistrer(new EntreeAudit("SECURITE_MOT_DE_PASSE_TEMPORAIRE", "UTILISATEUR",
                String.valueOf(c.id()), null, null, null, Map.of("login", c.login())));
        return new MotDePasseTemporaire(c.id(), c.login(), temporaire);
    }

    @Transactional
    public void deconnecter(long idUtilisateur) {
        Compte c = compte(idUtilisateur);
        comptes.revoquer(c.id());
        journal.enregistrer(new EntreeAudit("SECURITE_DECONNEXION_FORCEE", "UTILISATEUR",
                String.valueOf(c.id()), null, null, null, Map.of("login", c.login())));
    }

    @Transactional
    public GroupeVue modifierPermissions(long idGroupe, Set<Permission> nouvelles, String motif) {
        if (!permissions.groupeExiste(idGroupe)) {
            throw new ErreurMetier("GROUPE_INTROUVABLE", "Groupe introuvable : " + idGroupe);
        }
        Set<Permission> avant = permissions.duGroupe(idGroupe);
        boolean avaitAdministrateur = permissions.groupesAvec(Permission.SECURITE_ADMINISTRER) > 0;

        permissions.remplacer(idGroupe, nouvelles, maintenant(), utilisateur.login());

        if (avaitAdministrateur && permissions.groupesAvec(Permission.SECURITE_ADMINISTRER) == 0) {
            throw new ErreurMetier("DERNIER_ADMINISTRATEUR",
                    "Au moins un groupe doit garder le droit de gérer les accès.");
        }
        journal.enregistrer(new EntreeAudit("SECURITE_DROITS_MODIFIES", "GROUPE", String.valueOf(idGroupe), null,
                motif, Map.of("permissions", trier(avant)), Map.of("permissions", trier(nouvelles))));
        return permissions.groupes().stream().filter(g -> g.id() == idGroupe).findFirst().orElseThrow();
    }

    /** Crée l'utilisateur et lui donne aussitôt un mot de passe temporaire (affiché une seule fois). */
    @Transactional
    public MotDePasseTemporaire creerUtilisateur(String login, String nom, String prenom, long idGroupe) {
        String l = login == null ? "" : login.trim();
        if (!l.matches("[A-Za-z0-9._-]{3,20}")) {
            throw new ErreurMetier("LOGIN_INVALIDE", "Le nom d'utilisateur doit faire 3 à 20 caractères : "
                    + "lettres sans accent, chiffres, point, tiret ou souligné.");
        }
        if (nom == null || nom.isBlank()) {
            throw new ErreurMetier("NOM_OBLIGATOIRE", "Le nom est obligatoire.");
        }
        if (comptes.loginExiste(l)) {
            throw new ErreurMetier("LOGIN_EXISTANT", "Le nom d'utilisateur « " + l + " » existe déjà.");
        }
        if (!permissions.groupeExiste(idGroupe)) {
            throw new ErreurMetier("GROUPE_INTROUVABLE", "Groupe introuvable : " + idGroupe);
        }
        long id = comptes.creerUtilisateur(l, nom.trim().toUpperCase(), prenom == null ? "" : prenom.trim(),
                idGroupe, maintenant(), utilisateur.login());
        journal.enregistrer(new EntreeAudit("SECURITE_UTILISATEUR_CREE", "UTILISATEUR", String.valueOf(id), null,
                null, null, Map.of("login", l, "groupe", idGroupe)));
        return motDePasseTemporaire(id);
    }

    /** Changer de groupe, désactiver ou réactiver. Une désactivation déconnecte immédiatement. */
    @Transactional
    public void modifierUtilisateur(long id, long idGroupe, boolean actif) {
        Compte c = compte(id);
        boolean moiMeme = c.login().equalsIgnoreCase(utilisateur.login());
        if (moiMeme && (!actif || idGroupe != c.idGroupe())) {
            throw new ErreurMetier("MODIFICATION_DE_SOI",
                    "Vous ne pouvez pas désactiver votre propre compte ni changer votre propre groupe.");
        }
        if (!permissions.groupeExiste(idGroupe)) {
            throw new ErreurMetier("GROUPE_INTROUVABLE", "Groupe introuvable : " + idGroupe);
        }
        comptes.modifierUtilisateur(id, idGroupe, actif, maintenant(), utilisateur.login());
        if (!actif) {
            comptes.revoquer(id);
        }
        journal.enregistrer(new EntreeAudit("SECURITE_UTILISATEUR_MODIFIE", "UTILISATEUR", String.valueOf(id), null,
                null, Map.of("groupe", c.idGroupe(), "actif", c.actif()), Map.of("groupe", idGroupe, "actif", actif)));
    }

    private Compte compte(long id) {
        return comptes.parId(id).orElseThrow(() ->
                new ErreurMetier("UTILISATEUR_INTROUVABLE", "Utilisateur introuvable : " + id));
    }

    private LocalDateTime maintenant() {
        return LocalDateTime.now(horloge).truncatedTo(ChronoUnit.SECONDS);
    }

    private static List<String> trier(Set<Permission> p) {
        return p.stream().map(Enum::name).sorted().toList();
    }
}