package ht.dinasa.fact.facturation.application;

import java.math.BigDecimal;
import java.util.Optional;

public interface DepotCorrections {

    /** Une ligne de TRANSPORT d'une facture, bloquée pour modification. */
    record LigneTransport(long idLigne, long idFacture, String numeroFacture, boolean facturePostee,
                          String devise, BigDecimal quantite, BigDecimal prixAuGallon, BigDecimal montant,
                          Long idMouvement) {
    }

    Optional<LigneTransport> lignePourModification(long idFacture, long idLigne);

    /** Met à jour la ligne ET les totaux de l'en-tête (si la facture n'est toujours pas postée). */
    void appliquer(LigneTransport ligne, BigDecimal nouvelleQuantite, BigDecimal nouveauMontant, String utilisateur);
}