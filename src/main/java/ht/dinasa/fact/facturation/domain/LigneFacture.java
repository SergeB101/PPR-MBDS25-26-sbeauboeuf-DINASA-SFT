package ht.dinasa.fact.facturation.domain;

import ht.dinasa.fact.shared.Montant;

import java.math.BigDecimal;

public record LigneFacture(
        long idMouvement,
        String numeroBon,
        String codeCamion,
        BigDecimal quantiteCommandee,
        BigDecimal prixAuGallon,
        Montant montant) {
}