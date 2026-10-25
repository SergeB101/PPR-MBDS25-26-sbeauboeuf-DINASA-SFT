package ht.dinasa.fact.facturation.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ExportDocumentsTests {

    private static final Path DOSSIER = Path.of("target", "exports");

    @Autowired ExportDocuments exports;
    @Autowired JdbcClient jdbc;

    /** Une facture récente qui a des lignes de transport ET des services, si possible. */
    private long factureExemple() {
        return jdbc.sql("""
                SELECT TOP 1 f.ID_FACTURE FROM FACT.FACTURE f
                 WHERE EXISTS (SELECT 1 FROM FACT.DETAIL_FACTURE d WHERE d.CODE_FACTURE = f.ID_FACTURE
                                 AND d.CODE_TYPE_SERVICE_A_DEDUIRE IS NULL)
                 ORDER BY CASE WHEN EXISTS (SELECT 1 FROM FACT.DETAIL_FACTURE d WHERE d.CODE_FACTURE = f.ID_FACTURE
                                              AND d.CODE_TYPE_SERVICE_A_DEDUIRE IS NOT NULL) THEN 0 ELSE 1 END,
                          f.ID_FACTURE DESC""").query(Long.class).single();
    }

    private void ecrire(ExportDocuments.Fichier f) throws Exception {
        Files.createDirectories(DOSSIER);
        Files.write(DOSSIER.resolve(f.nom()), f.contenu());
        System.out.println("Écrit : " + DOSSIER.resolve(f.nom()).toAbsolutePath() + " (" + f.contenu().length + " octets)");
    }

    @Test
    void facture_en_pdf_et_en_excel() throws Exception {
        long id = factureExemple();
        var pdf = exports.facturePdf(id);
        var xlsx = exports.factureExcel(id);
        ecrire(pdf);
        ecrire(xlsx);

        assertThat(new String(pdf.contenu(), 0, 5)).isEqualTo("%PDF-");
        assertThat(new String(xlsx.contenu(), 0, 2)).isEqualTo("PK");
    }

    @Test
    void releve_et_liste_de_mars_2026() throws Exception {
        long idTransporteur = jdbc.sql("SELECT CODE_TRANSPORTEUR FROM FACT.FACTURE WHERE ID_FACTURE = :id")
                .param("id", factureExemple()).query(Long.class).single();
        LocalDate du = LocalDate.of(2026, 1, 1);
        LocalDate au = LocalDate.of(2026, 4, 30);

        var relevePdf = exports.relevePdf(idTransporteur, du, au);
        var releveExcel = exports.releveExcel(idTransporteur, du, au);
        var liste = exports.listeExcel(LocalDate.of(2026, 4, 1), au, null, null);
        ecrire(relevePdf);
        ecrire(releveExcel);
        ecrire(liste);

        var releve = exports.releve(idTransporteur, du, au);
        System.out.println("Relevé : " + releve.factures().size() + " facture(s), totaux " + releve.totaux());
        assertThat(new String(relevePdf.contenu(), 0, 5)).isEqualTo("%PDF-");
        assertThat(new String(liste.contenu(), 0, 2)).isEqualTo("PK");
    }
}