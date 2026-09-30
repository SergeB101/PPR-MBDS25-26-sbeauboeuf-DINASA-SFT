package ht.dinasa.fact.facturation.domain;

import ht.dinasa.fact.shared.Devise;
import ht.dinasa.fact.shared.Montant;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Une facture pour un transporteur, regroupant plusieurs bons de livraison.
 */
public record Facture(String numero, long codeTransporteur, LocalDateTime dateGeneration, List<LigneFacture> lignes) {

    public Facture {
        lignes = List.copyOf(lignes);
        if (lignes.isEmpty()) {
            throw new IllegalArgumentException("Une facture doit avoir au moins une ligne");
        }
    }

    public Montant total() {
        return lignes.stream()
                .map(LigneFacture::montant)
                .reduce(Montant.zero(Devise.USD), Montant::plus);
    }

    public BigDecimal quantiteTotale() {
        return lignes.stream()
                .map(LigneFacture::quantiteCommandee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Somme EXACTE de quantité × prix, sans coupure ligne par ligne.
     * Correspond à la colonne MONTANT_TRANSACTION de l'ancien système.
     */
    public BigDecimal montantTransactionExact() {
        return lignes.stream()
                .map(l -> l.quantiteCommandee().multiply(l.prixAuGallon()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}