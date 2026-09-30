package ht.dinasa.fact.facturation.application;

import ht.dinasa.fact.shared.erreurs.ElementIntrouvable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ConsultationFacturesTests {

    @Autowired ConsultationFactures consultation;
    @Autowired JdbcClient jdbc;

    @Test
    void la_liste_filtre_par_periode_et_respecte_la_taille_de_page() {
        var factures = consultation.rechercher(new CriteresFactures(
                LocalDate.of(2026, 4, 15), LocalDate.of(2026, 4, 22), null, null, 0, 10));

        System.out.println("Factures du 15 au 22 avril 2026 (10 premières) :");
        factures.forEach(f -> System.out.printf("  %-25s %-30s %12s %s postée=%s%n",
                f.numero(), f.nomTransporteur(), f.montant(), f.devise(), f.postee()));

        assertThat(factures).isNotEmpty().hasSizeLessThanOrEqualTo(10);
        assertThat(factures).allMatch(f -> !f.dateGeneration().isBefore(LocalDate.of(2026, 4, 15))
                && !f.dateGeneration().isAfter(LocalDate.of(2026, 4, 22)));
    }

    @Test
    void le_detail_signale_les_lignes_modifiees_a_la_main() {
        long id = jdbc.sql("SELECT ID_FACTURE FROM FACT.FACTURE WHERE NO_FACTURE = '20153-260417131529-U'")
                .query(Long.class).single();

        FicheFacture facture = consultation.detail(id);

        System.out.println(facture.numero() + " : " + facture.lignes().size() + " lignes, "
                + facture.lignes().stream().filter(FicheFacture.Ligne::modifieeManuellement).count()
                + " modifiées à la main");
        assertThat(facture.lignes()).hasSize(facture.nombreLignes());
        assertThat(facture.lignes()).filteredOn(FicheFacture.Ligne::modifieeManuellement).isNotEmpty();
    }

    @Test
    void une_facture_inexistante_donne_une_erreur_claire() {
        assertThatThrownBy(() -> consultation.detail(-1))
                .isInstanceOf(ElementIntrouvable.class);
    }
}