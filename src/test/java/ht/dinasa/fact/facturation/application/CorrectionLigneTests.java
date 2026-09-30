package ht.dinasa.fact.facturation.application;

import ht.dinasa.fact.shared.audit.Journal;
import ht.dinasa.fact.shared.erreurs.ElementIntrouvable;
import ht.dinasa.fact.shared.erreurs.ErreurMetier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CorrectionLigneTests {

    @Autowired CorrectionLigne correction;
    @Autowired PostingFactures posting;
    @Autowired Journal journal;
    @Autowired JdbcClient jdbc;

    private long idFacture;
    private long idLigne;
    private BigDecimal quantite;
    private BigDecimal prix;

    @BeforeEach
    void choisirUneLigne() {
        var l = jdbc.sql("""
                SELECT TOP 1 d.CODE_FACTURE, d.ID_DETAIL_FACTURE, d.QTE_COMMANDEE, d.PRIX_TRANSP_GALON
                  FROM FACT.DETAIL_FACTURE d
                  JOIN FACT.FACTURE f ON f.ID_FACTURE = d.CODE_FACTURE
                 WHERE f.POSTE_YN = 0 AND f.SIGLE_MONNAIE = 'USD'
                   AND d.PRIX_TRANSP_GALON IS NOT NULL AND d.QTE_COMMANDEE > 100
                   AND f.MONTANT = (SELECT SUM(x.MONTANT) FROM FACT.DETAIL_FACTURE x WHERE x.CODE_FACTURE = f.ID_FACTURE)
                 ORDER BY d.ID_DETAIL_FACTURE DESC""")
                .query((rs, n) -> new Object[]{rs.getLong(1), rs.getLong(2), rs.getBigDecimal(3), rs.getBigDecimal(4)})
                .single();
        idFacture = (Long) l[0];
        idLigne = (Long) l[1];
        quantite = (BigDecimal) l[2];
        prix = (BigDecimal) l[3];
    }

    @Test
    void la_correction_met_a_jour_la_ligne_l_en_tete_et_le_journal() {
        BigDecimal nouvelle = quantite.subtract(BigDecimal.TEN);

        correction.corrigerQuantite(idFacture, idLigne, nouvelle, "Volume réel constaté au déchargement");

        BigDecimal montant = jdbc.sql("SELECT MONTANT FROM FACT.DETAIL_FACTURE WHERE ID_DETAIL_FACTURE = :id")
                .param("id", idLigne).query(BigDecimal.class).single();
        assertThat(montant).isEqualByComparingTo(nouvelle.multiply(prix).setScale(2, RoundingMode.DOWN));

        var historique = journal.historiqueFacture(idFacture);
        assertThat(historique).anySatisfy(e -> {
            assertThat(e.action()).isEqualTo("FACTURE_LIGNE_CORRIGEE");
            assertThat(e.motif()).isEqualTo("Volume réel constaté au déchargement");
            assertThat(e.avant()).contains("quantite");
        });

        // l'en-tête reste égal à la somme des lignes : le posting est toujours possible
        posting.poster(idFacture);
    }

    @Test
    void le_motif_est_obligatoire() {
        assertThatThrownBy(() -> correction.corrigerQuantite(idFacture, idLigne, BigDecimal.ZERO, "  "))
                .isInstanceOf(ErreurMetier.class)
                .extracting(e -> ((ErreurMetier) e).getCode()).isEqualTo("MOTIF_OBLIGATOIRE");
    }

    @Test
    void impossible_de_corriger_une_facture_postee() {
        posting.poster(idFacture);

        assertThatThrownBy(() -> correction.corrigerQuantite(idFacture, idLigne, BigDecimal.ZERO, "Test"))
                .isInstanceOf(ErreurMetier.class)
                .extracting(e -> ((ErreurMetier) e).getCode()).isEqualTo("FACTURE_POSTEE");
    }

    @Test
    void une_ligne_d_une_autre_facture_est_introuvable() {
        assertThatThrownBy(() -> correction.corrigerQuantite(-1, idLigne, BigDecimal.ZERO, "Test"))
                .isInstanceOf(ElementIntrouvable.class);
    }
}