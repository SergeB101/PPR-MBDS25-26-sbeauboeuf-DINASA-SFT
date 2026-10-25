package ht.dinasa.fact.livraison.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PreparationRecuperationTest {

    private static final LocalDate JOUR = LocalDate.of(2026, 4, 10);
    private final PreparationRecuperation preparation = new PreparationRecuperation();

    private static LigneAccpac ligne(long id, String facture, String camion, Long client, Long idCamion,
                                     Long transporteur, Integer produit, String volume) {
        return new LigneAccpac(id, facture, JOUR, camion, client, idCamion, transporteur, produit,
                new BigDecimal(volume), new BigDecimal(volume));
    }

    @Test
    void une_facture_accpac_a_plusieurs_produits_donne_un_seul_bon_avec_un_detail_par_produit() {
        var r = preparation.preparer(List.of(
                ligne(1, "IN001 ", "MD1", 10L, 100L, 500L, 1, "3000"),
                ligne(2, "IN001", "MD1", 10L, 100L, 500L, 2, "2000")));

        assertThat(r.bons()).hasSize(1);
        BonACreer bon = r.bons().getFirst();
        assertThat(bon.noFacture()).isEqualTo("IN001");
        assertThat(bon.details()).hasSize(2);
        assertThat(bon.details().getFirst().volumeCommande()).isEqualByComparingTo("3000");
        assertThat(bon.idsLignesAccpac()).containsExactly(1L, 2L);
    }

//    @Test
//    void les_lignes_OT_sont_ignorees() {
//        var r = preparation.preparer(List.of(ligne(1, "IN002", "OT15", 10L, 100L, 500L, 1, "3000")));
//
//        assertThat(r.bons()).isEmpty();
//        assertThat(r.lignesOT()).isEqualTo(1);
//    }

    @Test
    void une_ligne_OT_donne_un_bon_sans_detail_comme_l_ancien_systeme() {
        var r = preparation.preparer(List.of(ligne(1, "IN002", "OT15", 10L, 100L, 500L, 1, "3000")));

        assertThat(r.lignesOT()).isEqualTo(1);
        assertThat(r.anomalies()).isEmpty();
        assertThat(r.bons()).singleElement().satisfies(b -> {
            assertThat(b.ot()).isTrue();
            assertThat(b.details()).isEmpty();
        });
    }

    @Test
    void chaque_type_d_anomalie_bloquante_empeche_la_creation_du_bon() {
        var r = preparation.preparer(List.of(
                ligne(1, "A", " ", 10L, null, null, 1, "100"),        // camion non précisé
                ligne(2, "B", "XX9", 10L, null, null, 1, "100"),      // camion inconnu
                ligne(3, "C", "MD1", null, 100L, 500L, 1, "100"),     // client inconnu
                ligne(4, "D", "MD2", 10L, 101L, null, 1, "100")));    // transporteur inconnu

        assertThat(r.bons()).isEmpty();
        assertThat(r.bloquantes()).extracting(a -> a.anomalies().getFirst()).containsExactly(
                TypeAnomalie.CAMION_NON_PRECISE, TypeAnomalie.CAMION_INCONNU,
                TypeAnomalie.CLIENT_INCONNU, TypeAnomalie.TRANSPORTEUR_INCONNU);
    }

    @Test
    void un_produit_inconnu_est_signale_mais_le_bon_est_cree() {
        var r = preparation.preparer(List.of(ligne(1, "E", "MD1", 10L, 100L, 500L, null, "100")));

        assertThat(r.bons()).hasSize(1);
        assertThat(r.anomalies()).extracting(a -> a.anomalies().getFirst())
                .containsExactly(TypeAnomalie.PRODUIT_INCONNU);
        assertThat(r.bloquantes()).isEmpty();
    }
}