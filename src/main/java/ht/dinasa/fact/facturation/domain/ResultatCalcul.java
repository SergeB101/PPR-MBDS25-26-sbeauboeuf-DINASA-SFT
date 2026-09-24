package ht.dinasa.fact.facturation.domain;

import java.util.List;

public record ResultatCalcul(List<Facture> factures, List<Rejet> rejets) {

    public ResultatCalcul {
        factures = List.copyOf(factures);
        rejets = List.copyOf(rejets);
    }
}