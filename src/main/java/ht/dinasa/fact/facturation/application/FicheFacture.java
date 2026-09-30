package ht.dinasa.fact.facturation.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Ce qu'on affiche d'une facture (en-tête et, pour le détail, ses lignes). */
public record FicheFacture(
        long id,
        String numero,
        long codeTransporteur,
        String nomTransporteur,
        LocalDate dateGeneration,
        String devise,
        BigDecimal montant,
        BigDecimal montantTransport,
        BigDecimal montantServices,
        boolean postee,
        int nombreLignes,
        List<Ligne> lignes) {

    public enum TypeLigne { TRANSPORT, SERVICE }

    public record Ligne(
            long id,
            TypeLigne type,
            Long idMouvement,
            String numeroBon,
            String codeCamion,
            LocalDate dateLivraison,
            String client,
            BigDecimal quantite,
            BigDecimal prixAuGallon,
            BigDecimal montant,
            String description,
            boolean modifieeManuellement) {
    }

    public FicheFacture avecLignes(List<Ligne> lignes) {
        return new FicheFacture(id, numero, codeTransporteur, nomTransporteur, dateGeneration, devise,
                montant, montantTransport, montantServices, postee, nombreLignes, List.copyOf(lignes));
    }
}