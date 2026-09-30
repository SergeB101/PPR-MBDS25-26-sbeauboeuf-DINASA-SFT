package ht.dinasa.fact.securite.infrastructure;

import ht.dinasa.fact.shared.UtilisateurCourant;
import org.springframework.stereotype.Component;

/** Version provisoire, remplacée à l'étape Sécurité par l'utilisateur connecté. */
@Component
class UtilisateurProvisoire implements UtilisateurCourant {

    @Override
    public String login() {
        return "dinasa-fact-api";
    }
}