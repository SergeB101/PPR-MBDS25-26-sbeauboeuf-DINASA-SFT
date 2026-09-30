package ht.dinasa.fact.facturation.application;

import ht.dinasa.fact.facturation.domain.BonDeLivraison;
import ht.dinasa.fact.facturation.domain.Facture;
import ht.dinasa.fact.facturation.domain.ResultatCalcul;
import ht.dinasa.fact.shared.erreurs.ErreurMetier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class SuppressionLigneTests {

    private static final int TYPE_MANQUE = 1;

    @Autowired SuppressionLigne suppression;
    @Autowired ServicesFacture services;
    @Autowired PostingFactures posting;
    @Autowired GenerationFactures generation;
    @Autowired BonsAFacturer bonsAFacturer;
    @Autowired JdbcClient jdbc;

    private long idFacture;

    /** Une facture USD non postée, cohérente, avec au moins 2 lignes de transport. */
    @BeforeEach
    void choisirUneFacture() {
        idFacture = jdbc.sql("""
                SELECT TOP 1 f.ID_FACTURE FROM FACT.FACTURE f
                 WHERE f.POSTE_YN = 0 AND f.SIGLE_MONNAIE = 'USD'
                   AND f.MONTANT = (SELECT SUM(d.MONTANT) FROM FACT.DETAIL_FACTURE d WHERE d.CODE_FACTURE = f.ID_FACTURE)
                   AND (SELECT COUNT(*) FROM FACT.DETAIL_FACTURE d
                         WHERE d.CODE_FACTURE = f.ID_FACTURE AND d.PRIX_TRANSP_GALON IS NOT NULL) >= 2
                 ORDER BY f.ID_FACTURE DESC""").query(Long.class).single();
    }

    /** La ligne de transport dont le bon a le plus grand ou le plus petit numéro. */
    private long[] ligneTransport(boolean plusPetitBon) {
        return jdbc.sql("SELECT TOP 1 ID_DETAIL_FACTURE, CODE_MOUVEMENT FROM FACT.DETAIL_FACTURE"
                        + " WHERE CODE_FACTURE = :id AND PRIX_TRANSP_GALON IS NOT NULL"
                        + " ORDER BY CODE_MOUVEMENT " + (plusPetitBon ? "ASC" : "DESC"))
                .param("id", idFacture)
                .query((rs, n) -> new long[]{rs.getLong(1), rs.getLong(2)}).single();
    }

    private boolean bonFacturable(long idBon) {
        LocalDate livraison = jdbc.sql("SELECT DateLivraison FROM dbo.Mouvement WHERE ID_Mouvement = :id")
                .param("id", idBon).query(LocalDate.class).single();
        return bonsAFacturer.duMois(YearMonth.from(livraison), false).stream()
                .anyMatch(b -> b.idMouvement() == idBon);
    }

    @Test
    void supprimer_une_ligne_de_transport_rend_le_bon_facturable_et_garde_la_facture_coherente() {
        long[] ligne = ligneTransport(false);

        SuppressionLigne.Resultat r = suppression.supprimer(idFacture, ligne[0], "Livraison annulée par le client");

        assertThat(r.factureSupprimee()).isFalse();
        assertThat(r.bonRedevenuFacturable()).isTrue();
        assertThat(bonFacturable(ligne[1])).isTrue();
        posting.poster(idFacture);   // en-tête toujours égal à la somme des lignes
    }

    @Test
    void supprimer_une_ligne_de_service_remet_le_service_en_attente() {
        services.ajouter(idFacture, TYPE_MANQUE, new BigDecimal("15"), LocalDate.now(), "Test");
        long idLigneService = jdbc.sql("SELECT MAX(ID_DETAIL_FACTURE) FROM FACT.DETAIL_FACTURE"
                        + " WHERE CODE_FACTURE = :id AND CODE_TYPE_SERVICE_A_DEDUIRE IS NOT NULL")
                .param("id", idFacture).query(Long.class).single();

        suppression.supprimer(idFacture, idLigneService, "Manque saisi par erreur");

        Long enAttente = jdbc.sql("SELECT COUNT(*) FROM FACT.SERVICE_A_DEDUIRE_FACTURE"
                        + " WHERE NOTE_SERVICE = 'Test' AND FACTURE_YN = 0 AND CODE_FACTURE IS NULL")
                .query(Long.class).single();
        assertThat(enAttente).isGreaterThanOrEqualTo(1);
        posting.poster(idFacture);
    }

    @Test
    void les_services_sont_rattaches_a_un_autre_bon_quand_on_supprime_leur_bon() {
        services.ajouter(idFacture, TYPE_MANQUE, new BigDecimal("15"), LocalDate.now(), "Test");
        long[] ligneDuPremierBon = ligneTransport(true);   // l'« artifice » utilise le plus petit bon

        SuppressionLigne.Resultat r = suppression.supprimer(idFacture, ligneDuPremierBon[0], "Test artifice");

        assertThat(r.bonRedevenuFacturable()).isTrue();
        Long servicesSurAncienBon = jdbc.sql("SELECT COUNT(*) FROM FACT.DETAIL_FACTURE"
                        + " WHERE CODE_FACTURE = :id AND CODE_MOUVEMENT = :bon")
                .param("id", idFacture).param("bon", ligneDuPremierBon[1]).query(Long.class).single();
        assertThat(servicesSurAncienBon).isZero();
    }

    @Test
    void la_facture_est_supprimee_quand_il_ne_reste_plus_aucune_ligne() {
        ResultatCalcul r = generation.generer(YearMonth.of(2026, 3), List.of());
        Facture petite = r.factures().stream()
                .min(Comparator.comparingInt(f -> f.lignes().size())).orElseThrow();
        long id = jdbc.sql("SELECT ID_FACTURE FROM FACT.FACTURE WHERE NO_FACTURE = :n")
                .param("n", petite.numero()).query(Long.class).single();
        List<Long> lignes = jdbc.sql("SELECT ID_DETAIL_FACTURE FROM FACT.DETAIL_FACTURE WHERE CODE_FACTURE = :id")
                .param("id", id).query(Long.class).list();

        SuppressionLigne.Resultat dernier = null;
        for (Long idLigne : lignes) {
            dernier = suppression.supprimer(id, idLigne, "Test suppression complète");
        }

        assertThat(dernier.factureSupprimee()).isTrue();
        assertThat(petite.lignes()).extracting(l -> l.idMouvement())
                .allMatch(bon -> bonsAFacturer.duMois(YearMonth.of(2026, 3), false).stream()
                        .map(BonDeLivraison::idMouvement).anyMatch(bon::equals));
    }

    @Test
    void le_motif_est_obligatoire_et_une_facture_postee_est_verrouillee() {
        long[] ligne = ligneTransport(false);
        assertThatThrownBy(() -> suppression.supprimer(idFacture, ligne[0], " "))
                .isInstanceOf(ErreurMetier.class)
                .extracting(e -> ((ErreurMetier) e).getCode()).isEqualTo("MOTIF_OBLIGATOIRE");

        posting.poster(idFacture);
        assertThatThrownBy(() -> suppression.supprimer(idFacture, ligne[0], "Test"))
                .isInstanceOf(ErreurMetier.class)
                .extracting(e -> ((ErreurMetier) e).getCode()).isEqualTo("FACTURE_POSTEE");
    }
}