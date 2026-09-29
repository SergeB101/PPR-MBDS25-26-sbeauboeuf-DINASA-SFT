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
 * Calcule les factures au gallon. Le prix dépend du type du camion
 * (une tarification active par type) et du point de livraison du client facturé.
 */
public class CalculFactureGallon {

    public ResultatCalcul calculer(List<BonDeLivraison> bons, TarifsActifs tarifs, LocalDateTime maintenant) {
        List<Rejet> rejets = new ArrayList<>();
        Map<Long, List<LigneFacture>> lignesParTransporteur = new TreeMap<>();

        for (BonDeLivraison bon : bons) {
            Optional<MotifRejet> motif = verifier(bon, tarifs);
            if (motif.isPresent()) {
                rejets.add(new Rejet(bon.idMouvement(), bon.numeroBon(), motif.get()));
                continue;
            }
            GrilleTarifaire grille = tarifs.pourTypeCamion(bon.typeCamion()).orElseThrow();
            BigDecimal prix = grille.prixAuGallon(bon.pointLivraisonClient()).orElseThrow();

            lignesParTransporteur
                    .computeIfAbsent(bon.codeTransporteur(), code -> new ArrayList<>())
                    .add(new LigneFacture(
                            bon.idMouvement(), bon.numeroBon(), bon.codeCamion(),
                            bon.quantiteCommandee(), prix,
                            Montant.produit(bon.quantiteCommandee(), prix, Devise.USD)));
        }

        List<Facture> factures = new ArrayList<>();
        lignesParTransporteur.forEach((codeTransporteur, lignes) ->
                factures.add(new Facture(
                        NumeroFacture.auGallon(codeTransporteur, maintenant),
                        codeTransporteur, maintenant, lignes)));

        return new ResultatCalcul(factures, rejets);
    }

    private Optional<MotifRejet> verifier(BonDeLivraison bon, TarifsActifs tarifs) {
        if (!bon.clientAUnPointDeLivraison()) {
            return Optional.of(MotifRejet.CLIENT_SANS_POINT_LIVRAISON);
        }
        if (!bon.camionEstAuGallon()) {
            return Optional.of(MotifRejet.CAMION_PAS_AU_GALLON);
        }
        Optional<GrilleTarifaire> grille = tarifs.pourTypeCamion(bon.typeCamion());
        if (grille.isEmpty()) {
            return Optional.of(MotifRejet.AUCUN_TARIF_ACTIF);
        }
        if (grille.get().prixAuGallon(bon.pointLivraisonClient()).isEmpty()) {
            return Optional.of(MotifRejet.PAS_DE_PRIX_POUR_CE_POINT);
        }
        return Optional.empty();
    }
}