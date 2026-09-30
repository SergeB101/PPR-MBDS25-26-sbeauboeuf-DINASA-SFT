package ht.dinasa.fact.facturation.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface DepotServices {

    /** En-tête d'une facture, bloqué pour modification. */
    record EnTeteFacture(long id, String numero, long codeTransporteur, String devise,
                         LocalDate dateGeneration, boolean postee, Long premierBon) {
    }

    record TypeService(int id, String libelle, int signe) {
    }

    /** Service saisi à l'avance, pas encore rattaché à une facture. */
    record ServiceEnAttente(int id, int idType, BigDecimal montant, LocalDate date, String note) {
    }

    Optional<EnTeteFacture> enTetePourModification(long idFacture);

    List<TypeService> typesServices();

    Optional<TypeService> typeService(int idType);

    List<ServiceEnAttente> servicesEnAttente(long codeTransporteur, YearMonth mois, String devise);

    /** Ajoute la ligne de service ET met à jour les totaux de l'en-tête. Renvoie l'id de la ligne. */
    long ajouterLigneService(EnTeteFacture facture, TypeService type, BigDecimal montantSigne,
                             String description, String utilisateur);

    void enregistrerServiceFacture(EnTeteFacture facture, TypeService type, BigDecimal montantSigne,
                                   LocalDate dateService, String note, long idLigne, String utilisateur);

    void marquerServiceFacture(int idService, long idLigne, String utilisateur);
}