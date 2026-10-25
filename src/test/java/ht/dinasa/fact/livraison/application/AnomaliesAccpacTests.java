package ht.dinasa.fact.livraison.application;

import ht.dinasa.fact.livraison.application.AnomaliesAccpac.TypeCorrection;
import ht.dinasa.fact.livraison.domain.TypeAnomalie;
import ht.dinasa.fact.shared.erreurs.ErreurMetier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tout ce que ces tests écrivent est annulé à la fin (@Transactional). */
@SpringBootTest
@Transactional
class AnomaliesAccpacTests {

    private static final YearMonth AVRIL = YearMonth.of(2026, 4);
    private static final YearMonth MARS = YearMonth.of(2026, 3);

    @Autowired AnomaliesAccpac anomalies;
    @Autowired RecuperationAccpac recuperation;
    @Autowired JdbcClient jdbc;

    private long compter(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    @Test
    void le_tableau_d_avril_montre_les_anomalies_et_les_codes_en_double() {
        var t = anomalies.tableau(AVRIL, false);

        System.out.println();
        System.out.println("===== TABLEAU DES ANOMALIES AVRIL 2026 =====");
        System.out.println("Lignes en anomalie : " + t.lignes().size());
        t.anomaliesParType().forEach((type, nb) -> System.out.println("  " + type.libelle() + " : " + nb));
        System.out.println("Codes camion en double : " + t.camionsEnDouble().size()
                + " (dont types différents : " + t.camionsEnDouble().stream().filter(d -> d.valeursDifferentes()).count() + ")");
        t.camionsEnDouble().stream().limit(5).forEach(d -> System.out.println("   " + d.code() + " → " + d.detail()));
        System.out.println("Codes client en double : " + t.clientsEnDouble().size()
                + " (dont points différents : " + t.clientsEnDouble().stream().filter(d -> d.valeursDifferentes()).count() + ")");
        t.clientsEnDouble().stream().limit(5).forEach(d -> System.out.println("   " + d.code() + " → " + d.detail()));
        System.out.println("=============================================");

        assertThat(t.lignes()).isNotEmpty();
        assertThat(t.camionsEnDouble()).isNotEmpty();
    }

    @Test
    void corriger_un_camion_inconnu_fait_disparaitre_l_anomalie_et_trace_la_correction() {
        var ligne = anomalies.tableau(AVRIL, false).lignes().stream()
                .filter(l -> l.anomalies().contains(TypeAnomalie.CAMION_INCONNU))
                .findFirst().orElseThrow();
        String codeValide = jdbc.sql("""
                SELECT TOP 1 RTRIM(LTRIM(c.CamionCode)) FROM dbo.Camion c
                  JOIN dbo.Personne p ON p.ID_Personne = c.CODE_TRANSPORTEUR
                 WHERE c.CamionCode NOT LIKE 'OT%' AND LEN(c.CamionCode) > 0
                 ORDER BY c.ID_Camion DESC""").query(String.class).single();

        var r = anomalies.corriger(ligne.idLigne(), TypeCorrection.CAMION, codeValide, "Code mal saisi dans ACCPAC");

        assertThat(r.lignesCorrigees()).isGreaterThanOrEqualTo(1);
        assertThat(anomalies.tableau(AVRIL, false).lignes())
                .noneMatch(l -> l.idLigne() == ligne.idLigne() && l.anomalies().contains(TypeAnomalie.CAMION_INCONNU));
        assertThat(compter("SELECT COUNT(*) FROM APP.JOURNAL_AUDIT WHERE ACTION = 'ACCPAC_ANOMALIE_CORRIGEE'"))
                .isEqualTo(1);
    }

    @Test
    void un_code_inexistant_est_refuse() {
        var ligne = anomalies.tableau(AVRIL, false).lignes().getFirst();

        assertThatThrownBy(() -> anomalies.corriger(ligne.idLigne(), TypeCorrection.CAMION, "ZZZ-INEXISTANT", "Test"))
                .isInstanceOf(ErreurMetier.class)
                .extracting(e -> ((ErreurMetier) e).getCode()).isEqualTo("CODE_INCONNU");
    }

    @Test
    void l_annulation_d_un_mois_deja_facture_est_refusee() {
        assertThatThrownBy(() -> anomalies.annuler(MARS, "Test"))
                .isInstanceOf(ErreurMetier.class)
                .extracting(e -> ((ErreurMetier) e).getCode()).isEqualTo("PERIODE_DEJA_FACTUREE");
    }

    @Test
    void recuperer_puis_annuler_remet_avril_dans_son_etat_initial() {
        long bonsAvant = compter("SELECT COUNT(*) FROM dbo.Mouvement");

        var bilanRecup = recuperation.recuperer(AVRIL, false);
        var bilanAnnul = anomalies.annuler(AVRIL, "Test de l'annulation");

        assertThat(bilanAnnul.bonsSupprimes()).isEqualTo(bilanRecup.bonsCrees());
        assertThat(compter("SELECT COUNT(*) FROM dbo.Mouvement")).isEqualTo(bonsAvant);
        assertThat(compter("SELECT COUNT(*) FROM dbo.Livraison_Acpacc"
                + " WHERE DateMouvement >= '2026-04-01' AND DateMouvement < '2026-05-01' AND IsRetreive = 1"))
                .isZero();
    }
}