package ht.dinasa.fact.livraison.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface LectureAnomalies {

    /** Codes bruts venant d'ACCPAC (texte), pour afficher et corriger une ligne. */
    record CodesAccpac(String codeClient, String shipTo, String codeProduit) {
    }

    /** Un code présent sur plusieurs fiches. valeursDifferentes = type de camion (ou point de livraison) différent. */
    record CodeEnDouble(String code, int fiches, boolean valeursDifferentes, String detail) {
    }

    Map<Long, CodesAccpac> codesAccpac(LocalDate debut, LocalDate fin);

    List<CodeEnDouble> camionsEnDouble(LocalDate debut, LocalDate fin);

    List<CodeEnDouble> clientsEnDouble(LocalDate debut, LocalDate fin);
}