package ht.dinasa.fact.facturation.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record BonDeLivraison(
        long idMouvement,
        String numeroBon,
        long codeTransporteur,
        String codeCamion,
        Integer typeCamion,                  // choisit la tarification
        MethodeTarification methodeCamion,   // peut être vide
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