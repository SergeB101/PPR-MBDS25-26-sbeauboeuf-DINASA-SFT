package ht.dinasa.fact.facturation.infrastructure;

import ht.dinasa.fact.facturation.application.LectureDocuments.EnTete;
import ht.dinasa.fact.facturation.application.LectureDocuments.Facture;
import ht.dinasa.fact.facturation.application.LectureDocuments.Ligne;
import ht.dinasa.fact.facturation.application.RenduExcel;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
class RenduExcelPoi implements RenduExcel {

    /** Styles partagés d'un classeur. */
    private record Styles(CellStyle entete, CellStyle titre, CellStyle date, CellStyle montant,
                          CellStyle quantite, CellStyle prix, CellStyle total) {

        static Styles de(Workbook wb) {
            DataFormat f = wb.createDataFormat();
            Font gras = wb.createFont();
            gras.setBold(true);
            Font grand = wb.createFont();
            grand.setBold(true);
            grand.setFontHeightInPoints((short) 13);

            CellStyle entete = wb.createCellStyle();
            entete.setFont(gras);
            entete.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            entete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle titre = wb.createCellStyle();
            titre.setFont(grand);
            CellStyle date = wb.createCellStyle();
            date.setDataFormat(f.getFormat("dd/mm/yyyy"));
            CellStyle montant = wb.createCellStyle();
            montant.setDataFormat(f.getFormat("#,##0.00"));
            CellStyle quantite = wb.createCellStyle();
            quantite.setDataFormat(f.getFormat("#,##0"));
            CellStyle prix = wb.createCellStyle();
            prix.setDataFormat(f.getFormat("0.0000"));
            CellStyle total = wb.createCellStyle();
            total.setFont(gras);
            total.setDataFormat(f.getFormat("#,##0.00"));
            return new Styles(entete, titre, date, montant, quantite, prix, total);
        }
    }

    @Override
    public byte[] facture(Facture f) {
        return classeur(wb -> {
            Styles st = Styles.de(wb);
            Sheet s = wb.createSheet("Facture");
            EnTete e = f.entete();
            texte(s.createRow(0), 0, "DINASA — Facture " + nul(e.numero()), st.titre());
            info(s, 1, "Date", e.generee(), st.date());
            info(s, 2, "Transporteur", e.idTransporteur() + " – " + nul(e.transporteur()), null);
            info(s, 3, "Devise", e.devise(), null);
            info(s, 4, "Statut", e.postee() ? "Postée" : "En attente de posting", null);

            int r = 6;
            entetes(s.createRow(r++), st, "Type", "Date", "Bon", "Camion", "Client facturé", "Point de livraison",
                    "Description", "Qté (gal)", "Prix", "Montant");
            int premiere = r;
            for (Ligne l : f.lignes()) {
                Row row = s.createRow(r++);
                texte(row, 0, l.service() ? "Service" : "Transport", null);
                date(row, 1, l.dateLivraison(), st);
                texte(row, 2, l.bon(), null);
                texte(row, 3, l.camion(), null);
                texte(row, 4, l.client(), null);
                texte(row, 5, l.pointLivraison(), null);
                texte(row, 6, l.description(), null);
                nombre(row, 7, l.quantite(), st.quantite());
                nombre(row, 8, l.prix(), st.prix());
                nombre(row, 9, l.montant(), st.montant());
            }
            Row total = s.createRow(r);
            texte(total, 8, "TOTAL", st.entete());
            if (r > premiere) {
                total.createCell(9).setCellFormula("SUM(J" + (premiere + 1) + ":J" + r + ")");
                total.getCell(9).setCellStyle(st.total());
            }
            largeurs(s, 10, 12, 18, 12, 32, 26, 30, 12, 10, 14);
            s.createFreezePane(0, 7);
        });
    }

    @Override
    public byte[] factures(String titre, List<EnTete> factures) {
        return classeur(wb -> {
            Styles st = Styles.de(wb);
            Sheet s = wb.createSheet("Factures");
            texte(s.createRow(0), 0, titre, st.titre());
            entetes(s.createRow(2), st, "ID", "N° facture", "Date", "Code transporteur", "Transporteur",
                    "Qté (gal)", "Montant", "Devise", "Postée");
            int r = 3;
            for (EnTete e : factures) {
                Row row = s.createRow(r++);
                row.createCell(0).setCellValue(e.id());
                texte(row, 1, e.numero(), null);
                date(row, 2, e.generee(), st);
                row.createCell(3).setCellValue(e.idTransporteur());
                texte(row, 4, e.transporteur(), null);
                nombre(row, 5, e.quantite(), st.quantite());
                nombre(row, 6, e.montant(), st.montant());
                texte(row, 7, e.devise(), null);
                texte(row, 8, e.postee() ? "Oui" : "Non", null);
            }
            if (!factures.isEmpty()) {
                Row total = s.createRow(r);
                texte(total, 5, "TOTAL", st.entete());
                total.createCell(6).setCellFormula("SUM(G4:G" + r + ")");
                total.getCell(6).setCellStyle(st.total());
                s.setAutoFilter(new CellRangeAddress(2, r - 1, 0, 8));
            }
            largeurs(s, 10, 30, 12, 12, 32, 12, 16, 8, 8);
            s.createFreezePane(0, 3);
        });
    }

    // ---------- outils ----------

    @FunctionalInterface
    private interface Remplissage {
        void remplir(Workbook wb);
    }

    private static byte[] classeur(Remplissage remplissage) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            remplissage.remplir(wb);
            wb.write(sortie);
            return sortie.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Création du fichier Excel impossible", e);
        }
    }

    private static void entetes(Row row, Styles st, String... noms) {
        for (int i = 0; i < noms.length; i++) {
            texte(row, i, noms[i], st.entete());
        }
    }

    private static void info(Sheet s, int ligne, String libelle, Object valeur, CellStyle style) {
        Row row = s.createRow(ligne);
        texte(row, 0, libelle, null);
        if (valeur instanceof LocalDate d) {
            Cell c = row.createCell(1);
            c.setCellValue(d);
            c.setCellStyle(style);
        } else {
            texte(row, 1, valeur == null ? "" : valeur.toString(), null);
        }
    }

    private static void texte(Row row, int col, String valeur, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(valeur == null ? "" : valeur.trim());
        if (style != null) {
            c.setCellStyle(style);
        }
    }

    private static void date(Row row, int col, LocalDate valeur, Styles st) {
        if (valeur != null) {
            Cell c = row.createCell(col);
            c.setCellValue(valeur);
            c.setCellStyle(st.date());
        }
    }

    private static void nombre(Row row, int col, BigDecimal valeur, CellStyle style) {
        if (valeur != null) {
            Cell c = row.createCell(col);
            c.setCellValue(valeur.doubleValue());
            c.setCellStyle(style);
        }
    }

    private static void largeurs(Sheet s, int... caracteres) {
        for (int i = 0; i < caracteres.length; i++) {
            s.setColumnWidth(i, caracteres[i] * 256);
        }
    }

    private static String nul(String s) {
        return s == null ? "" : s.trim();
    }
}