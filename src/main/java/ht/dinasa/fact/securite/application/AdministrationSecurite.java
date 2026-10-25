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