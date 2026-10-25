package ht.dinasa.fact.facturation.infrastructure;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import ht.dinasa.fact.facturation.application.LectureDocuments.EnTete;
import ht.dinasa.fact.facturation.application.LectureDocuments.Facture;
import ht.dinasa.fact.facturation.application.LectureDocuments.Ligne;
import ht.dinasa.fact.facturation.application.LectureDocuments.Releve;
import ht.dinasa.fact.facturation.application.LectureDocuments.Total;
import ht.dinasa.fact.facturation.application.RenduPdf;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
class RenduPdfOpenPdf implements RenduPdf {

    private static final Font TITRE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
    private static final Font GRAS = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 8);
    private static final Font NOTE = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7, Font.ITALIC, Color.DARK_GRAY);
    private static final Font ALERTE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.BOLD, new Color(180, 0, 0));
    private static final Color FOND = new Color(230, 230, 230);
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FunctionalInterface
    private interface Contenu {
        void ecrire(Document doc) throws Exception;
    }

    @Override
    public byte[] facture(Facture f, LocalDateTime genereLe) {
        EnTete e = f.entete();
        return document(doc -> {
            doc.add(new Paragraph("DINASA — Facture de transport", TITRE));
            doc.add(infos(List.of(
                    new String[]{"N° facture", texte(e.numero())},
                    new String[]{"Date", date(e.generee())},
                    new String[]{"Transporteur", e.idTransporteur() + " – " + texte(e.transporteur())},
                    new String[]{"Devise", e.devise()},
                    new String[]{"Statut", e.postee() ? "Postée" : "En attente de posting"})));

            List<Ligne> transport = f.lignes().stream().filter(l -> !l.service()).toList();
            List<Ligne> services = f.lignes().stream().filter(Ligne::service).toList();

            doc.add(sousTitre("Transport (" + transport.size() + " bon(s))"));
            PdfPTable t = tableau(new float[]{9, 12, 10, 26, 18, 9, 7, 9},
                    "Date", "Bon", "Camion", "Client facturé", "Point de livraison", "Qté (gal)", "Prix/gal", "Montant");
            for (Ligne l : transport) {
                t.addCell(cellule(date(l.dateLivraison())));
                t.addCell(cellule(texte(l.bon())));
                t.addCell(cellule(texte(l.camion())));
                t.addCell(cellule(texte(l.client())));
                t.addCell(cellule(texte(l.pointLivraison())));
                t.addCell(nombre(l.quantite(), "#,##0"));
                t.addCell(nombre(l.prix(), "0.0000"));
                t.addCell(nombre(l.montant(), "#,##0.00"));
            }
            doc.add(t);

            if (!services.isEmpty()) {
                doc.add(sousTitre("Services déduits / ajoutés"));
                PdfPTable s = tableau(new float[]{70, 15, 15}, "Description", "Prix", "Montant");
                for (Ligne l : services) {
                    s.addCell(cellule(l.description() == null ? "Service" : l.description()));
                    s.addCell(nombre(l.prix(), "#,##0.00"));
                    s.addCell(nombre(l.montant(), "#,##0.00"));
                }
                doc.add(s);
            }

            doc.add(totaux(List.of(
                    new String[]{"Quantité totale (gal)", montant(e.quantite(), "#,##0")},
                    new String[]{"TOTAL " + e.devise(), montant(e.montant(), "#,##0.00")})));
            if (f.incoherente()) {
                doc.add(new Paragraph("Attention : le total de l'en-tête (" + montant(e.montant(), "#,##0.00")
                        + ") diffère de la somme des lignes (" + montant(f.sommeLignes(), "#,##0.00")
                        + "). Facture à vérifier avant posting.", ALERTE));
            }
            doc.add(new Paragraph("Montants arrondis au centime inférieur. Document généré le "
                    + genereLe.format(HORODATAGE) + ".", NOTE));
        });
    }

    @Override
    public byte[] releve(Releve r, LocalDateTime genereLe) {
        return document(doc -> {
            doc.add(new Paragraph("DINASA — Relevé des factures", TITRE));
            doc.add(infos(List.of(
                    new String[]{"Transporteur", r.idTransporteur() + " – " + texte(r.transporteur())},
                    new String[]{"Période", "du " + date(r.du()) + " au " + date(r.au())},
                    new String[]{"Nombre de factures", String.valueOf(r.factures().size())})));

            PdfPTable t = tableau(new float[]{30, 14, 16, 18, 8, 14},
                    "N° facture", "Date", "Qté (gal)", "Montant", "Devise", "Statut");
            for (EnTete e : r.factures()) {
                t.addCell(cellule(texte(e.numero())));
                t.addCell(cellule(date(e.generee())));
                t.addCell(nombre(e.quantite(), "#,##0"));
                t.addCell(nombre(e.montant(), "#,##0.00"));
                t.addCell(cellule(e.devise()));
                t.addCell(cellule(e.postee() ? "Postée" : "En attente"));
            }
            doc.add(t);

            doc.add(sousTitre("Totaux"));
            PdfPTable tot = tableau(new float[]{10, 15, 25, 25, 25}, "Devise", "Factures", "Total", "Postées", "En attente");
            for (Total x : r.totaux()) {
                tot.addCell(cellule(x.devise()));
                tot.addCell(nombre(BigDecimal.valueOf(x.factures()), "#,##0"));
                tot.addCell(nombre(x.montant(), "#,##0.00"));
                tot.addCell(nombre(x.postees(), "#,##0.00"));
                tot.addCell(nombre(x.enAttente(), "#,##0.00"));
            }
            doc.add(tot);
            doc.add(new Paragraph("Relevé des factures émises : les paiements ne sont pas suivis dans ce système. "
                    + "Document généré le " + genereLe.format(HORODATAGE) + ".", NOTE));
        });
    }

    // ---------- outils ----------

    private static byte[] document(Contenu contenu) {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 30, 30, 30, 30);
        try {
            PdfWriter.getInstance(doc, sortie);
            doc.open();
            contenu.ecrire(doc);
        } catch (Exception e) {
            throw new IllegalStateException("Création du PDF impossible", e);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
        return sortie.toByteArray();
    }

    private static PdfPTable infos(List<String[]> lignes) {
        PdfPTable t = new PdfPTable(new float[]{20, 80});
        t.setWidthPercentage(60);
        t.setHorizontalAlignment(Element.ALIGN_LEFT);
        t.setSpacingBefore(8);
        for (String[] l : lignes) {
            PdfPCell c1 = new PdfPCell(new Phrase(l[0], GRAS));
            PdfPCell c2 = new PdfPCell(new Phrase(l[1], NORMAL));
            c1.setBorder(Rectangle.NO_BORDER);
            c2.setBorder(Rectangle.NO_BORDER);
            t.addCell(c1);
            t.addCell(c2);
        }
        return t;
    }

    private static PdfPTable totaux(List<String[]> lignes) {
        PdfPTable t = new PdfPTable(new float[]{60, 40});
        t.setWidthPercentage(35);
        t.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.setSpacingBefore(8);
        for (String[] l : lignes) {
            PdfPCell c1 = new PdfPCell(new Phrase(l[0], GRAS));
            c1.setBackgroundColor(FOND);
            PdfPCell c2 = new PdfPCell(new Phrase(l[1], GRAS));
            c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
            t.addCell(c1);
            t.addCell(c2);
        }
        return t;
    }

    private static Paragraph sousTitre(String texte) {
        Paragraph p = new Paragraph(texte, GRAS);
        p.setSpacingBefore(10);
        p.setSpacingAfter(4);
        return p;
    }

    private static PdfPTable tableau(float[] largeurs, String... entetes) {
        PdfPTable t = new PdfPTable(largeurs);
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        for (String e : entetes) {
            PdfPCell c = new PdfPCell(new Phrase(e, GRAS));
            c.setBackgroundColor(FOND);
            t.addCell(c);
        }
        return t;
    }

    private static PdfPCell cellule(String texte) {
        return new PdfPCell(new Phrase(texte, NORMAL));
    }

    private static PdfPCell nombre(BigDecimal valeur, String format) {
        PdfPCell c = new PdfPCell(new Phrase(montant(valeur, format), NORMAL));
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return c;
    }

    private static String montant(BigDecimal valeur, String format) {
        return valeur == null ? "" : new DecimalFormat(format, DecimalFormatSymbols.getInstance(Locale.US)).format(valeur);
    }

    private static String date(LocalDate d) {
        return d == null ? "" : d.format(JOUR);
    }

    private static String texte(String s) {
        return s == null ? "" : s.trim();
    }
}