package ht.dinasa.fact.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Une somme d'argent dans une devise donnée. On ne mélange jamais deux devises.
 */
public record Montant(BigDecimal valeur, Devise devise) {

    /**
     * Règle unique pour toute l'application : on COUPE après 2 décimales (pas d'arrondi),
     * comme l'ancien système. Confirmé par le test de parité de mars 2026
     * (ex. 651,80 × 0,07 = 45,626 → 45,62).
     */
    public static final int DECIMALES = 2;
    public static final RoundingMode ARRONDI = RoundingMode.DOWN;

    public Montant {
        Objects.requireNonNull(valeur, "valeur obligatoire");
        Objects.requireNonNull(devise, "devise obligatoire");
        valeur = valeur.setScale(DECIMALES, ARRONDI);
    }

    public static Montant zero(Devise devise) {
        return new Montant(BigDecimal.ZERO, devise);
    }

    public static Montant usd(String valeur) {
        return new Montant(new BigDecimal(valeur), Devise.USD);
    }

    public Montant plus(Montant autre) {
        verifierMemeDevise(autre);
        return new Montant(valeur.add(autre.valeur), devise);
    }

    public Montant moins(Montant autre) {
        verifierMemeDevise(autre);
        return new Montant(valeur.subtract(autre.valeur), devise);
    }

    /** Ex. : prix par gallon × nombre de gallons. */
    public static Montant produit(BigDecimal quantite, BigDecimal prixUnitaire, Devise devise) {
        return new Montant(quantite.multiply(prixUnitaire), devise);
    }

    private void verifierMemeDevise(Montant autre) {
        if (autre.devise != devise) {
            throw new IllegalArgumentException(
                    "Impossible de combiner " + devise + " et " + autre.devise);
        }
    }
}