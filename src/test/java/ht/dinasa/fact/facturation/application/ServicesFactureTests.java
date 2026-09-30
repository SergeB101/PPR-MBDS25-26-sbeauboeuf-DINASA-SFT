package ht.dinasa.fact.facturation.application;

import ht.dinasa.fact.shared.erreurs.ErreurMetier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ServicesFactureTests {

    private static final int TYPE_MANQUE = 1;          // signe -1
    private static final int TYPE_REMBOURSEMENT = 16;  // signe +1

    @Autowired ServicesFacture services;
    @Autowired PostingFactures posting;
    @Autowired JdbcClient jdbc;

    private long idFacture;
    private long transporteur;
    private LocalDate dateGeneration;

    @BeforeEach
    void choisirUneFactureUsdCoherenteNonPostee() {
        var ligne = jdbc.sql("""
                SELECT TOP 1 f.ID_FACTURE, f.CODE_TRANSPORTEUR, f.DATE_GENERER_FACTURE FROM FACT.FACTURE f
                 WHERE f.POSTE_YN = 0 AND f.SIGLE_MONNAIE = 'USD'
                   AND f.MONTANT = (SELECT SUM(d.MONTANT) FROM FACT.DETAIL_FACTURE d WHERE d.CODE_FACTURE = f.ID_FACTURE)
                 ORDER BY f.ID_FACTURE DESC""")
                .query((rs, n) -> new Object[]{rs.getLong(1), rs.getLong(2), rs.getObject(3, LocalDate.class)})
                .single();
        idFacture = (Long) ligne[0];
        transporteur = (Long) ligne[1];
        dateGeneration = (LocalDate) ligne[2];
    }

    private BigDecimal montantEnTete() {
        return jdbc.sql("SELECT MONTANT FROM FACT.FACTURE WHERE ID_FACTURE = :id")
                .param("id", idFacture).query(BigDecimal.class).single();
    }

    @Test
    void un_manque_diminue_la_facture_et_reste_coherent_pour_le_posting() {
        BigDecimal avant = montantEnTete();

        services.ajouter(idFacture, TYPE_MANQUE, new BigDecimal("25.50"), LocalDate.now(), "Test manque");

        assertThat(montantEnTete()).isEqualByComparingTo(avant.subtract(new BigDecimal("25.50")));
        Long servicesMarques = jdbc.sql("""
                SELECT COUNT(*) FROM FACT.SERVICE_A_DEDUIRE_FACTURE s
                  JOIN FACT.DETAIL_FACTURE d ON d.ID_DETAIL_FACTURE = s.CODE_FACTURE
                 WHERE d.CODE_FACTURE = :id AND s.FACTURE_YN = 1 AND s.MONTANT = -25.50""")
                .param("id", idFacture).query(Long.class).single();
        assertThat(servicesMarques).isEqualTo(1);

        // l'en-tête correspond toujours aux lignes : le posting passe
        posting.poster(idFacture);
    }

    @Test
    void un_remboursement_augmente_la_facture() {
        BigDecimal avant = montantEnTete();

        services.ajouter(idFacture, TYPE_REMBOURSEMENT, new BigDecimal("10"), LocalDate.now(), "Test");

        assertThat(montantEnTete()).isEqualByComparingTo(avant.add(BigDecimal.TEN));
    }

    @Test
    void impossible_d_ajouter_un_service_a_une_facture_postee() {
        posting.poster(idFacture);

        assertThatThrownBy(() -> services.ajouter(idFacture, TYPE_MANQUE, BigDecimal.ONE, LocalDate.now(), null))
                .isInstanceOf(ErreurMetier.class)
                .extracting(e -> ((ErreurMetier) e).getCode()).isEqualTo("FACTURE_POSTEE");
    }

    @Test
    void un_montant_nul_ou_negatif_est_refuse() {
        assertThatThrownBy(() -> services.ajouter(idFacture, TYPE_MANQUE, BigDecimal.ZERO, LocalDate.now(), null))
                .isInstanceOf(ErreurMetier.class)
                .extracting(e -> ((ErreurMetier) e).getCode()).isEqualTo("MONTANT_OBLIGATOIRE");
    }

    @Test
    void les_services_en_attente_du_mois_sont_incorpores_une_seule_fois() {
        jdbc.sql("""
                INSERT INTO FACT.SERVICE_A_DEDUIRE_FACTURE
                       (CODE_TRANSPORTEUR, CODE_TYPE_SERVICE_A_DEDUIRE, DATE_SERVICE, MONTANT, SIGLE_MONNAIE,
                        FACTURE_YN, NOTE_SERVICE, CREATEDBY, DATECREATED)
                VALUES (:t, :type, :d, 12.00, 'USD', 0, 'Test en attente', 'test', GETDATE())""")
                .param("t", transporteur).param("type", TYPE_MANQUE).param("d", dateGeneration)
                .update();
        BigDecimal avant = montantEnTete();

        int premiere = services.incorporerEnAttente(idFacture);
        int seconde = services.incorporerEnAttente(idFacture);

        assertThat(premiere).isGreaterThanOrEqualTo(1);
        assertThat(seconde).isZero();
        assertThat(montantEnTete()).isLessThan(avant);
    }
}