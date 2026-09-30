package ht.dinasa.fact.livraison.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Un bon de livraison à créer : un par facture ACCPAC (et par client/camion/transporteur/date). */
public record BonACreer(
        String noFacture,
        LocalDate date,
        long idClient,
        long idCamion,
        long idTransporteur,
        List<Detail> details,
        List<Long> idsLignesAccpac) {

    /** Une ligne par produit. La quantité commandée est le volume fourni (règle de l'ancien système). */
    public record Detail(Integer idProduit, BigDecimal volumeCommande, BigDecimal volumeFourni,
                         BigDecimal volumeLivre) {
    }

    public BonACreer {
        details = List.copyOf(details);
        idsLignesAccpac = List.copyOf(idsLignesAccpac);
    }
}