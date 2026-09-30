package ht.dinasa.fact.facturation.application;

import java.time.LocalDate;

/** Filtres de recherche ; un champ vide = pas de filtre. */
public record CriteresFactures(LocalDate du, LocalDate au, Long codeTransporteur, Boolean postee,
                               int page, int taille) {

    public static final int TAILLE_MAX = 200;

    public CriteresFactures {
        page = Math.max(page, 0);
        taille = Math.clamp(taille, 1, TAILLE_MAX);
    }
}