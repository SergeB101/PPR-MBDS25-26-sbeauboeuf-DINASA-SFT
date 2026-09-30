package ht.dinasa.fact.facturation.application;

import java.util.List;
import java.util.Optional;

public interface LectureFactures {

    /** Liste des en-têtes (sans les lignes), du plus récent au plus ancien. */
    List<FicheFacture> rechercher(CriteresFactures criteres);

    /** Une facture avec toutes ses lignes. */
    Optional<FicheFacture> parId(long id);
}