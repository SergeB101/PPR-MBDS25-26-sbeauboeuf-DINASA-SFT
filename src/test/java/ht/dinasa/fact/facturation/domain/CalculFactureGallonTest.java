package ht.dinasa.fact.facturation.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CalculFactureGallonTest {

    private static final LocalDateTime MAINTENANT = LocalDateTime.of(2026, 9, 24, 14, 5, 9);
    private static final int POINT_CAP_HAITIEN = 10;
    private static final int POINT_JACMEL = 20;
    private static final int TYPE_PLUS_12_ANS = 3;
    private static final int TYPE_MOINS_12_ANS = 6;

    private final CalculFactureGallon calcul = new CalculFactureGallon();

    private final TarifsActifs tarifs = new TarifsActifs(Map.of(
            TYPE_PLUS_12_ANS, new GrilleTarifaire(17, Map.of(
                    POINT_CAP_HAITIEN, new BigDecimal("0.127500"),
                    POINT_JACMEL, new BigDecimal("0.095000"))),
            TYPE_MOINS_12_ANS, new GrilleTarifaire(18, Map.of(
                    POINT_CAP_HAITIEN, new BigDecimal("0.150000")))));

    private static BonDeLivraison bon(long id, long transporteur, int type, Integer point, String quantite) {
        return new BonDeLivraison(id, "BL-" + id, transporteur, "CAM-" + transporteur, type,
                MethodeTarification.GALLON, 500L, point, new BigDecimal(quantite));
    }

    private static BonDeLivraison bon(long id, long transporteur, Integer point, String quantite) {
        return bon(id, transporteur, TYPE_PLUS_12_ANS, point, quantite);
    }

    @Test
    void montant_egal_quantite_fois_prix_en_usd() {
        ResultatCalcul r = calcul.calculer(List.of(bon(1, 42, POINT_CAP_HAITIEN, "8500")), tarifs, MAINTENANT);

        assertThat(r.rejets()).isEmpty();
        LigneFacture ligne = r.factures().getFirst().lignes().getFirst();
        assertThat(ligne.prixAuGallon()).isEqualByComparingTo("0.1275");
        assertThat(ligne.montant().valeur()).isEqualByComparingTo("1083.75");
        assertThat(ligne.montant().devise().name()).isEqualTo("USD");
    }

    @Test
    void le_prix_depend_du_type_de_camion() {
        ResultatCalcul r = calcul.calculer(List.of(
                bon(1, 42, TYPE_PLUS_12_ANS, POINT_CAP_HAITIEN, "1000"),
                bon(2, 77, TYPE_MOINS_12_ANS, POINT_CAP_HAITIEN, "1000")), tarifs, MAINTENANT);

        assertThat(r.factures()).extracting(f -> f.lignes().getFirst().prixAuGallon())
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("0.1275"), new BigDecimal("0.15"));
    }

    @Test
    void numero_de_facture_au_meme_format_que_l_ancien_systeme() {
        ResultatCalcul r = calcul.calculer(List.of(bon(1, 42, POINT_CAP_HAITIEN, "100")), tarifs, MAINTENANT);

        assertThat(r.factures().getFirst().numero()).isEqualTo("42-260924140509-U");
    }

    @Test
    void une_seule_facture_par_transporteur_meme_si_les_bons_sont_melanges() {
        ResultatCalcul r = calcul.calculer(List.of(
                bon(1, 42, POINT_CAP_HAITIEN, "1000"),
                bon(2, 77, POINT_JACMEL, "2000"),
                bon(3, 42, POINT_JACMEL, "3000")), tarifs, MAINTENANT);

        assertThat(r.factures()).hasSize(2);
        Facture facture42 = r.factures().stream().filter(f -> f.codeTransporteur() == 42).findFirst().orElseThrow();
        assertThat(facture42.lignes()).extracting(LigneFacture::idMouvement).containsExactly(1L, 3L);
        assertThat(facture42.total().valeur()).isEqualByComparingTo("412.50");
        assertThat(facture42.quantiteTotale()).isEqualByComparingTo("4000");
    }

    @Test
    void client_sans_point_de_livraison_est_rejete() {
        ResultatCalcul r = calcul.calculer(List.of(bon(1, 42, null, "100")), tarifs, MAINTENANT);

        assertThat(r.factures()).isEmpty();
        assertThat(r.rejets()).extracting(Rejet::motif).containsExactly(MotifRejet.CLIENT_SANS_POINT_LIVRAISON);
    }

    @Test
    void camion_non_configure_au_gallon_est_rejete() {
        BonDeLivraison fixeVariable = new BonDeLivraison(1, "BL-1", 42, "CAM-42", TYPE_PLUS_12_ANS,
                MethodeTarification.FIXE_VARIABLE, 500L, POINT_CAP_HAITIEN, new BigDecimal("100"));

        ResultatCalcul r = calcul.calculer(List.of(fixeVariable), tarifs, MAINTENANT);

        assertThat(r.rejets()).extracting(Rejet::motif).containsExactly(MotifRejet.CAMION_PAS_AU_GALLON);
    }

    @Test
    void type_de_camion_sans_tarification_active_est_rejete() {
        int typeSansTarif = 2;
        ResultatCalcul r = calcul.calculer(
                List.of(bon(1, 42, typeSansTarif, POINT_CAP_HAITIEN, "100")), tarifs, MAINTENANT);

        assertThat(r.rejets()).extracting(Rejet::motif).containsExactly(MotifRejet.AUCUN_TARIF_ACTIF);
    }

    @Test
    void sans_aucune_tarification_active_tous_les_bons_sont_rejetes() {
        ResultatCalcul r = calcul.calculer(
                List.of(bon(1, 42, POINT_CAP_HAITIEN, "100"), bon(2, 77, POINT_JACMEL, "100")),
                TarifsActifs.aucun(), MAINTENANT);

        assertThat(r.factures()).isEmpty();
        assertThat(r.rejets()).extracting(Rejet::motif).containsOnly(MotifRejet.AUCUN_TARIF_ACTIF);
    }

    @Test
    void point_de_livraison_sans_prix_est_rejete_et_les_autres_bons_continuent() {
        int pointSansPrix = 99;
        ResultatCalcul r = calcul.calculer(
                List.of(bon(1, 42, pointSansPrix, "100"), bon(2, 42, POINT_CAP_HAITIEN, "100")), tarifs, MAINTENANT);

        assertThat(r.rejets()).extracting(Rejet::motif).containsExactly(MotifRejet.PAS_DE_PRIX_POUR_CE_POINT);
        assertThat(r.factures()).hasSize(1);
        assertThat(r.factures().getFirst().lignes()).hasSize(1);
    }
}