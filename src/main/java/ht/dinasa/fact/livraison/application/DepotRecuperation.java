package ht.dinasa.fact.livraison.application;

import ht.dinasa.fact.livraison.domain.BonACreer;
import ht.dinasa.fact.livraison.domain.RegleClientFacture.CamionJour;

import java.time.LocalDate;
import java.util.Map;

public interface DepotRecuperation {

    /** Garde le numéro du dernier bon existant (utilisé par l'annulation de l'ancien système). */
    void memoriserDernierBonAvantRecuperation(String utilisateur);

    /**
     * Reconnaît clients, camions, transporteurs et produits dans la table intermédiaire,
     * avec EXACTEMENT les instructions de l'ancienne procédure (même choix en cas de doublon).
     */
    void etablirCorrespondances(LocalDate debut, LocalDate fin);

    /** Crée le bon et ses détails ; renvoie son identifiant. */
    long creerBon(BonACreer bon);

    /** Marque comme récupérées les lignes de la période dont le numéro de facture a un bon. */
    int marquerRecuperees(LocalDate debut, LocalDate fin);

    /** Applique la règle du client facturé ; renvoie le nombre de bons mis à jour. */
    int appliquerClientsFactures(Map<CamionJour, Long> clients, LocalDate debut, LocalDate fin);

    /** Met à jour le rapport « OT » (procédure existante, inchangée). */
    void genererRapportOT();
}