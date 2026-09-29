package ht.dinasa.fact.facturation.application;

import ht.dinasa.fact.facturation.domain.BonDeLivraison;

import java.time.YearMonth;
import java.util.List;

public interface BonsAFacturer {

    /** Bons de livraison du mois ; les bons déjà facturés sont exclus sauf demande contraire. */
    List<BonDeLivraison> duMois(YearMonth mois, boolean inclureDejaFactures);
}