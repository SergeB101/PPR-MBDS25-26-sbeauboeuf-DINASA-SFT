package ht.dinasa.fact.facturation.application;

import ht.dinasa.fact.facturation.application.LectureDocuments.EnTete;
import ht.dinasa.fact.facturation.application.LectureDocuments.Facture;
import ht.dinasa.fact.facturation.application.LectureDocuments.Releve;
import ht.dinasa.fact.facturation.application.LectureDocuments.Total;
import ht.dinasa.fact.shared.erreurs.ErreurMetier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ExportDocuments {

    public record Fichier(String nom, String typeMime, byte[] contenu) {
    }

    public static final String PDF = "application/pdf";
    public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final int LIGNES_MAX = 5000;

    private final LectureDocuments lecture;
    private final RenduPdf pdf;
    private final RenduExcel excel;
    private final Clock horloge;

    public ExportDocuments(LectureDocuments lecture, RenduPdf pdf, RenduExcel excel, Clock horloge) {
        this.lecture = lecture;
        this.pdf = pdf;
        this.excel = excel;
        this.horloge = horloge;
    }

    public Fichier facturePdf(long id) {
        Facture f = facture(id);
        return new Fichier("facture-" + nomFichier(f.entete().numero(), id) + ".pdf", PDF,
                pdf.facture(f, LocalDateTime.now(horloge)));
    }

    public Fichier factureExcel(long id) {
        Facture f = facture(id);
        return new Fichier("facture-" + nomFichier(f.entete().numero(), id) + ".xlsx", XLSX, excel.facture(f));
    }

    public Fichier listeExcel(LocalDate du, LocalDate au, Long idTransporteur, Boolean postee) {
        verifierPeriode(du, au);
        List<EnTete> factures = factures(du, au, idTransporteur, postee);
        return new Fichier("factures-" + du + "-au-" + au + ".xlsx", XLSX,
                excel.factures("Factures du " + du + " au " + au, factures));
    }

    public Fichier relevePdf(long idTransporteur, LocalDate du, LocalDate au) {
        Releve r = releve(idTransporteur, du, au);
        return new Fichier("releve-" + idTransporteur + "-" + du + "-au-" + au + ".pdf", PDF,
                pdf.releve(r, LocalDateTime.now(horloge)));
    }

    public Fichier releveExcel(long idTransporteur, LocalDate du, LocalDate au) {
        Releve r = releve(idTransporteur, du, au);
        return new Fichier("releve-" + idTransporteur + "-" + du + "-au-" + au + ".xlsx", XLSX,
                excel.factures("Relevé des factures – " + r.transporteur() + " – du " + du + " au " + au,
                        r.factures()));
    }

    Releve releve(long idTransporteur, LocalDate du, LocalDate au) {
        verifierPeriode(du, au);
        String nom = lecture.transporteur(idTransporteur).orElseThrow(() ->
                new ErreurMetier("TRANSPORTEUR_INTROUVABLE", "Transporteur introuvable : " + idTransporteur));
        List<EnTete> factures = factures(du, au, idTransporteur, null);

        Map<String, List<EnTete>> parDevise = factures.stream()
                .collect(Collectors.groupingBy(EnTete::devise, TreeMap::new, Collectors.toList()));
        List<Total> totaux = parDevise.entrySet().stream().map(e -> new Total(e.getKey(), e.getValue().size(),
                somme(e.getValue(), null), somme(e.getValue(), true), somme(e.getValue(), false))).toList();
        return new Releve(idTransporteur, nom, du, au, factures, totaux);
    }

    private Facture facture(long id) {
        EnTete e = lecture.entete(id).orElseThrow(() ->
                new ErreurMetier("FACTURE_INTROUVABLE", "Facture introuvable : " + id));
        return new Facture(e, lecture.lignes(id));
    }

    private List<EnTete> factures(LocalDate du, LocalDate au, Long idTransporteur, Boolean postee) {
        List<EnTete> f = lecture.factures(du, au, idTransporteur, postee, LIGNES_MAX + 1);
        if (f.size() > LIGNES_MAX) {
            throw new ErreurMetier("TROP_DE_LIGNES",
                    "Plus de " + LIGNES_MAX + " factures : réduisez la période ou filtrez par transporteur.");
        }
        return f;
    }

    private static BigDecimal somme(List<EnTete> factures, Boolean postee) {
        return factures.stream().filter(f -> postee == null || f.postee() == postee)
                .map(EnTete::montant).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void verifierPeriode(LocalDate du, LocalDate au) {
        if (du == null || au == null || au.isBefore(du)) {
            throw new ErreurMetier("PERIODE_INVALIDE", "La date de fin doit être après la date de début.");
        }
    }

    private static String nomFichier(String numero, long id) {
        return numero == null || numero.isBlank() ? String.valueOf(id) : numero.trim().replaceAll("[^A-Za-z0-9_-]", "_");
    }
}