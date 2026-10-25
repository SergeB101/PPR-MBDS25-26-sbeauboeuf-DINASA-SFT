package ht.dinasa.fact.securite.infrastructure;

import ht.dinasa.fact.shared.UtilisateurCourant;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
class UtilisateurConnecte implements UtilisateurCourant {

    /** Hors requête HTTP (tests, tâches automatiques) : nom technique de l'application. */
    static final String SYSTEME = "dinasa-fact-api";

    @Override
    public String login() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return SYSTEME;
        }
        return auth.getName();
    }
}