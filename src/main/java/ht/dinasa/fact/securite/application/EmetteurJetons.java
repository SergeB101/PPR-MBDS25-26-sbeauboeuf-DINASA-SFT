package ht.dinasa.fact.securite.application;

import java.time.Instant;

public interface EmetteurJetons {

    record Jeton(String valeur, Instant expireA) {
    }

    Jeton emettre(String login, int version, Instant maintenant);
}