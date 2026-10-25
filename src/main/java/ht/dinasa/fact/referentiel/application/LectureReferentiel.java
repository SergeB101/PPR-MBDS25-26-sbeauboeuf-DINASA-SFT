package ht.dinasa.fact.referentiel.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface LectureReferentiel {

    record Transporteur(long id, String nom, String prenom, String type, int camions) {
    }

    /** fichesMemeCode > 1 : le code est porté par plusieurs fiches (tarif / transporteur choisis au hasard). */
    record Camion(int id, String code, String immatriculation, String marque, String modele, Integer annee,
                  BigDecimal capacite, Integer idType, String type, String statut, String methodeTarification,
                  Long idTransporteur, String transporteur, int fichesMemeCode) {
    }

    /** Plusieurs fiches pour un même code client est normal si les points de livraison diffèrent. */
    record Client(long id, String code, String nom, Integer idPointLivraison, String pointLivraison,
                  BigDecimal distance, String telephone, String adresse, int fichesMemeCode) {
    }

    record PointLivraison(int id, String libelle, BigDecimal distance, int zone, int clients) {
    }

    record TypeCamion(int id, String libelle, Integer anneeMin, Integer anneeMax, int camions,
                      int tarifsActifs, String alerte) {
    }

    record Tarif(int id, String libelle, Integer idTypeCamion, String typeCamion, LocalDate debut,
                 LocalDate fin, boolean actif, String devise, int prix) {
    }

    record PrixTarif(int idPointLivraison, String pointLivraison, BigDecimal distance, BigDecimal prixAuGallon) {
    }

    List<Transporteur> transporteurs(String recherche);

    List<Camion> camions(String recherche, Long idTransporteur);

    List<Client> clients(String recherche);

    List<PointLivraison> pointsLivraison();

    List<TypeCamion> typesCamion();

    List<Tarif> tarifs(boolean actifsSeulement);

    List<PrixTarif> prixDuTarif(int idTarif);
}