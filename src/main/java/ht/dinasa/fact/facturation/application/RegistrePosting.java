package ht.dinasa.fact.facturation.application;

import java.math.BigDecimal;
import java.util.Optional;

public interface RegistrePosting {

    /** Ce qu'il faut savoir d'une facture avant de la poster. */
    record EtatFacture(long id, String numero, boolean postee, BigDecimal montantEnTete,
                       BigDecimal sommeDesLignes, int nombreLignes) {
    }

    Optional<EtatFacture> etat(long idFacture);

    /** Marque la facture comme postée si elle ne l'est pas déjà ; renvoie false sinon. */
    boolean marquerPostee(long idFacture, String utilisateur);
}