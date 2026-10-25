package ht.dinasa.fact.reporting.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface LectureTableauxDeBord {

    record Livraisons(int bons, BigDecimal volumeLivre, int bonsFactures, int bonsAFacturer, int bonsExclus) {
    }

    /** Lignes de transport (hors services) des bons livrés sur la période. */
    record CoutTransport(BigDecimal quantiteFacturee, BigDecimal montant, BigDecimal prixMoyenGallon) {
    }

    record MontantDevise(String devise, int factures, BigDecimal montant) {
    }

    record AttentePosting(int factures, int incoherentes, LocalDate plusAncienne, List<MontantDevise> parDevise) {
    }

    record Accpac(int lignesARecuperer, int lignesOT, int lignesSansCamion, int lignesCamionInconnu,
                  int codesCamionEnDouble, int codesCamionTypesDifferents) {
    }

    record VolumeMois(int mois, int bons, BigDecimal volumeLivre, BigDecimal quantiteFacturee, BigDecimal montant) {
    }

    record CoutTransporteur(long idTransporteur, String transporteur, int bons, BigDecimal quantite,
                            BigDecimal montant, BigDecimal prixMoyenGallon) {
    }

    record FactureEnAttente(long id, String numero, long idTransporteur, String transporteur, LocalDate generee,
                            int joursAttente, String devise, BigDecimal montant, BigDecimal sommeLignes,
                            boolean incoherente) {
    }

    /** debut inclus, fin exclue. */
    Livraisons livraisons(LocalDate debut, LocalDate fin);

    CoutTransport cout(LocalDate debut, LocalDate fin);

    AttentePosting attentePosting();

    Accpac accpac(LocalDate debut, LocalDate fin);

    List<VolumeMois> volumesParMois(int annee);

    List<CoutTransporteur> coutsParTransporteur(LocalDate debut, LocalDate fin);

    List<FactureEnAttente> facturesEnAttente(int limite, LocalDate aujourdhui);
}