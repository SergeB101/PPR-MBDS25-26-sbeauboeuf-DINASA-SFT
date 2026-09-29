package ht.dinasa.fact.facturation.application;

import ht.dinasa.fact.facturation.domain.ResultatCalcul;
import ht.dinasa.fact.facturation.domain.TarifsActifs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApercuFacturationTests {

    @Autowired
    SourceTarifs sourceTarifs;

    @Autowired
    BonsAFacturer bonsAFacturer;

    @Autowired
    ApercuFacturation apercu;

    @Test
    void les_tarifs_actifs_couvrent_les_types_3_6_et_8() {
        TarifsActifs tarifs = sourceTarifs.actifs();
        assertThat(tarifs.parTypeCamion()).containsKeys(3, 6, 8);
    }

    @Test
    void les_bons_de_mars_2026_sont_lus_y_compris_ceux_deja_factures() {
        var bons = bonsAFacturer.duMois(YearMonth.of(2026, 3), true);
        System.out.println("Bons de mars 2026 (tous) : " + bons.size());
        assertThat(bons).isNotEmpty();
    }

    @Test
    void l_apercu_de_mars_2026_fonctionne() {
        ResultatCalcul r = apercu.pourLeMois(YearMonth.of(2026, 3));
        System.out.println("Factures : " + r.factures().size() + " — rejets : " + r.rejets().size());
        r.rejets().stream()
                .collect(java.util.stream.Collectors.groupingBy(rj -> rj.motif(), java.util.stream.Collectors.counting()))
                .forEach((motif, nb) -> System.out.println("  " + motif + " : " + nb));
    }
}