package ht.dinasa.fact.facturation.domain;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * La tarification active : un prix au gallon par point de livraison.
 */
public record GrilleTarifaire(int idTarification, Map<Integer, BigDecimal> prixParPointLivraison) {

    public GrilleTarifaire {
        prixParPointLivraison = Map.copyOf(prixParPointLivraison);
    }

    public Optional<BigDecimal> prixAuGallon(int pointLivraison) {
        return Optional.ofNullable(prixParPointLivraison.get(pointLivraison));
    }
}