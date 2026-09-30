package ht.dinasa.fact.facturation.application;

import ht.dinasa.fact.facturation.domain.Facture;

import java.util.List;

public interface DepotFactures {

    /** Enregistre les factures et marque les bons comme facturés. Échoue si un bon est déjà facturé. */
    void enregistrer(List<Facture> factures, String utilisateur);
}