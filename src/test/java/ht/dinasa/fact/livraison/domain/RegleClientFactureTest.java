package ht.dinasa.fact.livraison.domain;

import ht.dinasa.fact.livraison.domain.RegleClientFacture.CamionJour;
import ht.dinasa.fact.livraison.domain.RegleClientFacture.Livraison;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegleClientFactureTest {

    private static final LocalDate JOUR = LocalDate.of(2026, 4, 10);
    private final RegleClientFacture regle = new RegleClientFacture();

    @Test
    void le_client_le_plus_eloigne_est_facture_pour_tout_le_chargement() {
        var r = regle.clientsFactures(List.of(
                new Livraison(100, JOUR, 11, new BigDecimal("45")),
                new Livraison(100, JOUR, 12, new BigDecimal("180")),   // Cap-Haïtien
                new Livraison(100, JOUR, 13, new BigDecimal("60"))));

        assertThat(r).containsEntry(new CamionJour(100, JOUR), 12L);
    }

    @Test
    void en_cas_d_egalite_le_plus_grand_numero_de_client_est_retenu() {
        var r = regle.clientsFactures(List.of(
                new Livraison(100, JOUR, 20, new BigDecimal("90")),
                new Livraison(100, JOUR, 35, new BigDecimal("90"))));

        assertThat(r).containsEntry(new CamionJour(100, JOUR), 35L);
    }

    @Test
    void chaque_camion_et_chaque_jour_sont_traites_separement() {
        var r = regle.clientsFactures(List.of(
                new Livraison(100, JOUR, 11, new BigDecimal("45")),
                new Livraison(100, JOUR.plusDays(1), 12, new BigDecimal("10")),
                new Livraison(200, JOUR, 13, new BigDecimal("5"))));

        assertThat(r).hasSize(3)
                .containsEntry(new CamionJour(100, JOUR), 11L)
                .containsEntry(new CamionJour(100, JOUR.plusDays(1)), 12L)
                .containsEntry(new CamionJour(200, JOUR), 13L);
    }

    @Test
    void sans_distance_connue_aucun_client_facture_n_est_impose() {
        var r = regle.clientsFactures(List.of(new Livraison(100, JOUR, 11, null)));

        assertThat(r).isEmpty();
    }
}