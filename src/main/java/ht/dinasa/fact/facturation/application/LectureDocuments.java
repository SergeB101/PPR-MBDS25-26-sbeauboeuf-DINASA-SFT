package ht.dinasa.fact.facturation.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Données des documents exportés (lecture seule). */
public interface LectureDocuments {

    record EnTete(long id, String numero, LocalDate generee, String devise, boolean postee,
                  long idTransporteur, String transporteur, BigDecimal quantite, BigDecimal montant) {
    }

    record Ligne(long id, boolean service, String bon, LocalDate dateLivraison, String camion, String client,
                 String pointLivraison, BigDecimal quantite, BigDecimal prix, BigDecimal montant, String description) {
    }

    record Facture(EnTete entete, List<Ligne> lignes) {
        public BigDecimal sommeLignes() {
            return lignes.stream().map(Ligne::montant).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public boolean incoherente() {
            return entete.montant().subtract(sommeLignes()).abs().compareTo(new BigDecimal("0.01")) >= 0;
        }
    }

    record Total(String devise, int factures, BigDecimal montant, BigDecimal postees, BigDecimal enAttente) {
    }

    record Releve(long idTransporteur, String transporteur, LocalDate du, LocalDate au,
                  List<EnTete> factures, List<Total> totaux) {
    }

    Optional<EnTete> entete(long idFacture);

    List<Ligne> lignes(long idFacture);

    /** idTransporteur et postee : null = pas de filtre. */
    List<EnTete> factures(LocalDate du, LocalDate au, Long idTransporteur, Boolean postee, int limite);

    Optional<String> transporteur(long idTransporteur);
}