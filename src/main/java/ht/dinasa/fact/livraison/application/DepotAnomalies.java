package ht.dinasa.fact.livraison.application;

import java.time.LocalDate;
import java.util.Optional;

public interface DepotAnomalies {

    record LigneACorriger(long id, LocalDate date, String codeCamion, String codeClient, boolean recuperee) {
    }

    record BilanAnnulation(int bonsSupprimes, int detailsSupprimes, int lignesReinitialisees) {
    }

    Optional<LigneACorriger> ligne(long idLigne);

    Optional<Long> idCamion(String code);

    Optional<Long> idClient(String code);

    /** Corrige le camion de cette ligne ET des lignes non récupérées du même mois ayant le même ancien code. */
    int corrigerCamion(LigneACorriger ligne, String nouveauCode, long idCamion, String log, String utilisateur);

    /** Corrige le client de cette seule ligne. */
    int corrigerClient(LigneACorriger ligne, String nouveauCode, long idClient, String log, String utilisateur);

    int bonsDejaFactures(LocalDate debut, LocalDate fin);

    /** Supprime les bons créés par la récupération et remet les lignes ACCPAC « à récupérer ». */
    BilanAnnulation annuler(LocalDate debut, LocalDate fin);
}