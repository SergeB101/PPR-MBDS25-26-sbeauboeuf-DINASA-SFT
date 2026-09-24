package ht.dinasa.fact.facturation.domain;

import ht.dinasa.fact.shared.Devise;
import ht.dinasa.fact.shared.Montant;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Calcule les factures au gallon à partir des bons de livraison sélectionnés.
 * Les bons qui ne peuvent pas être facturés sont rejetés avec un motif ;
 * les autres continuent (comme dans l'ancien système).
 */
public class CalculFactureGallon {

    /**
     * @param grilleActive la tarification active, ou null s'il n'y en a aucune
     */
    public ResultatCalcul calculer(List<BonDeLivraison> bons, GrilleTarifaire grilleActive, LocalDateTime maintenant) {
        List<Rejet> rejets = new ArrayList<>();
        Map<Long, List<LigneFacture>> lignesParTransporteur = new TreeMap<>();

        for (BonDeLivraison bon : bons) {
            Optional<MotifRejet> motif = verifier(bon, grilleActive);
            if (motif.isPresent()) {
                rejets.add(new Rejet(bon.idMouvement(), bon.numeroBon(), motif.get()));
                continue;
            }
            BigDecimal prix = grilleActive.prixAuGallon(bon.pointLivraisonClient()).orElseThrow();
            LigneFacture ligne = new LigneFacture(
                    bon.idMouvement(),
                    bon.numeroBon(),
                    bon.codeCamion(),
                    bon.quantiteCommandee(),
                    prix,
                    Montant.produit(bon.quantiteCommandee(), prix, Devise.USD));

            lignesParTransporteur
                    .computeIfAbsent(bon.codeTransporteur(), code -> new ArrayList<>())
                    .add(ligne);
        }

        List<Facture> factures = new ArrayList<>();
        lignesParTransporteur.forEach((codeTransporteur, lignes) ->
                factures.add(new Facture(
                        NumeroFacture.auGallon(codeTransporteur, maintenant),
                        codeTransporteur,
                        maintenant,
                        lignes)));

        return new ResultatCalcul(factures, rejets);
    }

    /** Mêmes vérifications, dans le même ordre, que l'ancien système. */
    private Optional<MotifRejet> verifier(BonDeLivraison bon, GrilleTarifaire grille) {
        if (!bon.clientAUnPointDeLivraison()) {
            return Optional.of(MotifRejet.CLIENT_SANS_POINT_LIVRAISON);
        }
        if (!bon.camionEstAuGallon()) {
            return Optional.of(MotifRejet.CAMION_PAS_AU_GALLON);
        }
        if (grille == null) {
            return Optional.of(MotifRejet.AUCUN_TARIF_ACTIF);
        }
        if (grille.prixAuGallon(bon.pointLivraisonClient()).isEmpty()) {
            return Optional.of(MotifRejet.PAS_DE_PRIX_POUR_CE_POINT);
        }
        return Optional.empty();
    }
}