package ht.dinasa.fact.securite.web;

import ht.dinasa.fact.securite.application.Authentification;
import ht.dinasa.fact.securite.application.ConnexionRefusee;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
class AuthentificationController {

    record DemandeConnexion(@NotBlank @Size(max = 50) String login,
                            @NotBlank @Size(max = 64) String motDePasse) {
    }

    record DemandeChangement(@NotBlank @Size(max = 50) String login,
                             @NotBlank @Size(max = 64) String ancienMotDePasse,
                             @NotBlank @Size(max = 64) String nouveauMotDePasse) {
    }

    private final Authentification authentification;

    AuthentificationController(Authentification authentification) {
        this.authentification = authentification;
    }

    @Operation(summary = "Se connecter (si doitChangerMotDePasse = true, aucun jeton : changer d'abord le mot de passe)")
    @SecurityRequirements
    @PostMapping("/connexion")
    Authentification.ResultatConnexion connecter(@Valid @RequestBody DemandeConnexion d, HttpServletRequest requete) {
        return authentification.connecter(d.login(), d.motDePasse(), requete.getRemoteAddr());
    }

    @Operation(summary = "Changer son mot de passe (obligatoire à la première connexion) — renvoie un jeton")
    @SecurityRequirements
    @PostMapping("/changement-mot-de-passe")
    Authentification.ResultatConnexion changer(@Valid @RequestBody DemandeChangement d, HttpServletRequest requete) {
        return authentification.changerMotDePasse(d.login(), d.ancienMotDePasse(), d.nouveauMotDePasse(),
                requete.getRemoteAddr());
    }

    @Operation(summary = "Mon profil et mes droits")
    @GetMapping("/moi")
    Authentification.Profil moi(Authentication auth) {
        return authentification.profil(auth.getName());
    }

    @ExceptionHandler(ConnexionRefusee.class)
    ProblemDetail refus(ConnexionRefusee e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
        p.setTitle("Connexion refusée");
        p.setProperty("code", "CONNEXION_REFUSEE");
        return p;
    }
}