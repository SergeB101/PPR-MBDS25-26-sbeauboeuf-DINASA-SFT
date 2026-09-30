package ht.dinasa.fact.facturation.application;

import ht.dinasa.fact.facturation.domain.Facture;
import ht.dinasa.fact.facturation.domain.ResultatCalcul;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Écrit réellement dans la base, puis tout est annulé à la fin du test (@Transactional). */
@SpringBootTest
@Transactional
class GenerationFacturesTests {

    private static final YearMonth MARS_2026 = YearMonth.of(2026, 3);

    @Autowired GenerationFactures generation;
    @Autowired JdbcClient jdbc;

    @Test
    void les_factures_sont_enregistrees_comme_l_ancien_systeme() {
        ResultatCalcul r = generation.generer(MARS_2026, List.of());
        System.out.println("Factures créées : " + r.factures().size() + " — rejets : " + r.rejets().size());
        assertThat(r.factures()).isNotEmpty();

        for (Facture f : r.factures()) {
            // En-tête : même montant que la somme des lignes, non postée, USD, méthode 1
            var entete = jdbc.sql("""
                    SELECT MONTANT, POSTE_YN, SIGLE_MONNAIE, CODE_METHODE_TARIFICATION
                      FROM FACT.FACTURE WHERE NO_FACTURE = :n""")
                    .param("n", f.numero())
                    .query((rs, i) -> new Object[]{rs.getBigDecimal(1), rs.getBoolean(2), rs.getString(3), rs.getInt(4)})
                    .single();
            assertThat((BigDecimal) entete[0]).isEqualByComparingTo(f.total().valeur());
            assertThat(entete[1]).isEqualTo(false);
            assertThat(entete[2]).isEqualTo("USD");
            assertThat(entete[3]).isEqualTo(1);

            // Lignes : une par bon, rattachées à l'en-tête
            Long lignes = jdbc.sql("""
                    SELECT COUNT(*) FROM FACT.DETAIL_FACTURE d
                      JOIN FACT.FACTURE e ON e.ID_FACTURE = d.CODE_FACTURE
                     WHERE e.NO_FACTURE = :n""")
                    .param("n", f.numero()).query(Long.class).single();
            assertThat(lignes).isEqualTo(f.lignes().size());
        }

        // Chaque bon pointe vers sa ligne de facture
        long idBon = r.factures().getFirst().lignes().getFirst().idMouvement();
        Long lien = jdbc.sql("""
                SELECT COUNT(*) FROM dbo.Mouvement m
                  JOIN FACT.DETAIL_FACTURE d ON d.ID_DETAIL_FACTURE = m.CodeFacture
                 WHERE m.ID_Mouvement = :id AND d.CODE_MOUVEMENT = :id""")
                .param("id", idBon).query(Long.class).single();
        assertThat(lien).isEqualTo(1);
    }

    @Test
    void un_bon_ne_peut_pas_etre_facture_deux_fois() {
        ResultatCalcul premiere = generation.generer(MARS_2026, List.of());
        assertThat(premiere.factures()).isNotEmpty();

        ResultatCalcul seconde = generation.generer(MARS_2026, List.of());
        assertThat(seconde.factures()).isEmpty();
    }
}