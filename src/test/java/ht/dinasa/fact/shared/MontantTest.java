package ht.dinasa.fact.shared;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MontantTest {

    @Test
    void quantite_fois_prix_est_arrondi_a_deux_decimales() {
        Montant m = Montant.produit(new BigDecimal("8500"), new BigDecimal("0.1275"), Devise.USD);
        assertThat(m.valeur()).isEqualByComparingTo("1083.75");
    }

    @Test
    void on_peut_additionner_deux_montants_en_usd() {
        assertThat(Montant.usd("10.50").plus(Montant.usd("2.25")))
                .isEqualTo(Montant.usd("12.75"));
    }

    @Test
    void on_ne_peut_pas_melanger_usd_et_htg() {
        Montant usd = Montant.usd("10");
        Montant htg = new Montant(new BigDecimal("10"), Devise.HTG);
        assertThatThrownBy(() -> usd.plus(htg))
                .isInstanceOf(IllegalArgumentException.class);
    }
}