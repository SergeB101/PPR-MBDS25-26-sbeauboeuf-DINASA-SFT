package ht.dinasa.fact.facturation.application;

import ht.dinasa.fact.shared.erreurs.ErreurMetier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

/** Tout ce que ces tests écrivent est annulé à la fin (@Transactional). */
@SpringBootTest
@Transactional
class PostingFacturesTests {

    @Autowired PostingFactures posting;
    @Autowired JdbcClient jdbc;

    /** Une facture non postée dont l'en-tête correspond exactement à ses lignes. */
    private long factureCoherente() {
        return jdbc.sql("""
                SELECT TOP 1 f.ID_FACTURE FROM FACT.FACTURE f
                 WHERE f.POSTE_YN = 0
                   AND f.MONTANT = (SELECT SUM(d.MONTANT) FROM FACT.DETAIL_FACTURE d WHERE d.CODE_FACTURE = f.ID_FACTURE)
                 ORDER BY f.ID_FACTURE DESC""").query(Long.class).single();
    }

    @Test
    void une_facture_coherente_est_postee() {
        long id = factureCoherente();

        posting.poster(id);

        Boolean postee = jdbc.sql("SELECT POSTE_YN FROM FACT.FACTURE WHERE ID_FACTURE = :id")
                .param("id", id).query(Boolean.class).single();
        assertThat(postee).isTrue();
    }

    @Test
    void une_facture_ne_peut_pas_etre_postee_deux_fois() {
        long id = factureCoherente();
        posting.poster(id);

        assertThatThrownBy(() -> posting.poster(id))
                .isInstanceOf(ErreurMetier.class)
                .extracting(e -> ((ErreurMetier) e).getCode()).isEqualTo("FACTURE_DEJA_POSTEE");
    }

    @Test
    void une_facture_dont_l_en_tete_ne_correspond_pas_aux_lignes_est_refusee() {
        var incoherente = jdbc.sql("""
                SELECT TOP 1 f.ID_FACTURE FROM FACT.FACTURE f
                 WHERE f.POSTE_YN = 0
                   AND f.MONTANT <> ISNULL((SELECT SUM(d.MONTANT) FROM FACT.DETAIL_FACTURE d
                                             WHERE d.CODE_FACTURE = f.ID_FACTURE), 0)""")
                .query(Long.class).optional();
        assumeThat(incoherente).as("aucune facture incohérente dans la base").isPresent();

        assertThatThrownBy(() -> posting.poster(incoherente.get()))
                .isInstanceOf(ErreurMetier.class)
                .extracting(e -> ((ErreurMetier) e).getCode()).isEqualTo("MONTANT_INCOHERENT");
    }
}