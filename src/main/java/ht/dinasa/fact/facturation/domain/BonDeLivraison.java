package ht.dinasa.fact.facturation.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Un bon de livraison prêt à être facturé, avec les informations
 * du camion et du client facturé déjà rassemblées.
 */
public record BonDeLivraison(
        long idMouvement,
        String numeroBon,
        long codeTransporteur,
        String codeCamion,
        MethodeTarification methodeCamion,   // peut être vide si non renseignée
        long idClientFacture,
        Integer pointLivraisonClient,        // vide ou 0 = non renseigné
        BigDecimal quantiteCommandee) {

    public BonDeLivraison {
        Objects.requireNonNull(quantiteCommandee, "quantité commandée obligatoire");
    }

    boolean clientAUnPointDeLivraison() {
        return pointLivraisonClient != null && pointLivraisonClient > 0;
    }

    boolean camionEstAuGallon() {
        return methodeCamion == MethodeTarification.GALLON;
    }
}