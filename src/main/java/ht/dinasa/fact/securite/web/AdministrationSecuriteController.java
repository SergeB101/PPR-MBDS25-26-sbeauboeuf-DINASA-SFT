package ht.dinasa.fact.securite.web;

import ht.dinasa.fact.securite.application.AdministrationSecurite;
import ht.dinasa.fact.securite.application.DepotComptes.UtilisateurVue;
import ht.dinasa.fact.securite.application.DepotPermissions.GroupeVue;
import ht.dinasa.fact.securite.domain.Permission;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/securite")
class AdministrationSecuriteController {

    record DemandeDroits(@NotNull Set<Permission> permissions, @Size(max = 500) String motif) {
    }

    private final AdministrationSecurite administration;

    AdministrationSecuriteController(AdministrationSecurite administration) {
        this.administration = administration;
    }

    @Operation(summary = "Utilisateurs (fiches de l'ancien système) et état de leur compte")
    @GetMapping("/utilisateurs")
    List<UtilisateurVue> utilisateurs() {
        return administration.utilisateurs();
    }

    @Operation(summary = "Groupes et leurs droits")
    @GetMapping("/groupes")
    List<GroupeVue> groupes() {
        return administration.groupes();
    }

    @Operation(summary = "Liste des droits existants")
    @GetMapping("/permissions")
    List<AdministrationSecurite.PermissionVue> permissions() {
        return administration.permissions();
    }

    @Operation(summary = "Donner un mot de passe temporaire (affiché une seule fois, à changer à la connexion)")
    @PostMapping("/utilisateurs/{id}/mot-de-passe-temporaire")
    AdministrationSecurite.MotDePasseTemporaire motDePasseTemporaire(@PathVariable long id) {
        return administration.motDePasseTemporaire(id);
    }

    @Operation(summary = "Forcer la déconnexion (tous ses jetons deviennent invalides)")
    @PostMapping("/utilisateurs/{id}/deconnexion")
    void deconnecter(@PathVariable long id) {
        administration.deconnecter(id);
    }

    @Operation(summary = "Remplacer les droits d'un groupe")
    @PutMapping("/groupes/{id}/permissions")
    GroupeVue modifier(@PathVariable long id, @Valid @RequestBody DemandeDroits d) {
        return administration.modifierPermissions(id, d.permissions(), d.motif());
    }
}