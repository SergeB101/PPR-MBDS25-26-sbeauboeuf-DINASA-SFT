package ht.dinasa.fact.facturation.application;

import ht.dinasa.fact.facturation.application.DepotCorrections.LigneTransport;
import ht.dinasa.fact.shared.Devise;
import ht.dinasa.fact.shared.Montant;
import ht.dinasa.fact.shared.UtilisateurCourant;
import ht.dinasa.fact.shared.audit.EntreeAudit;
import ht.dinasa.fact.shared.audit.Journal;
import ht.dinasa.fact.shared.erreurs.ElementIntrouvable;
import ht.dinasa.fact.shared.erreurs.ErreurMetier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/** Correction de la quantité d'une ligne de transport, avec motif obligatoire et trace dans le journal. */
@Service
public class CorrectionLigne {

    private final DepotCorrections depot;
    private final Journal journal;
    private final UtilisateurCourant utilisateur;

    public CorrectionLigne(DepotCorrections depot, Journal journal, UtilisateurCourant utilisateur) {
        this.depot = depot;
        this.journal = journal;
        this.utilisateur = utilisateur;
    }

    @Transactional
    public void corrigerQuantite(long idFacture, long idLigne, BigDecimal nouvelleQuantite, String motif) {
        if (motif == null || motif.isBlank()) {
            throw new ErreurMetier("MOTIF_OBLIGATOIRE", "Le motif de la correction est obligatoire.");
        }
        if (nouvelleQuantite == null || nouvelleQuantite.signum() < 0) {
            throw new ErreurMetier("QUANTITE_INVALIDE", "La quantité doit être supérieure ou égale à 0.");
        }

        LigneTransport ligne = depot.lignePourModification(idFacture, idLigne)
                .orElseThrow(() -> new ElementIntrouvable("Ligne de transport de la facture " + idFacture, idLigne));
        if (ligne.facturePostee()) {
            throw new ErreurMetier("FACTURE_POSTEE",
                    "La facture " + ligne.numeroFacture() + " est postée : elle ne peut plus être modifiée.");
        }
        if (ligne.quantite() != null && ligne.quantite().compareTo(nouvelleQuantite) == 0) {
            throw new ErreurMetier("AUCUN_CHANGEMENT", "La quantité saisie est identique à la quantité actuelle.");
        }

        // même règle que partout : quantité × prix, centimes coupés
        BigDecimal nouveauMontant = Montant.produit(nouvelleQuantite, ligne.prixAuGallon(),
                Devise.valueOf(ligne.devise().trim())).valeur();

        depot.appliquer(ligne, nouvelleQuantite, nouveauMontant, utilisateur.login());

        journal.enregistrer(new EntreeAudit("FACTURE_LIGNE_CORRIGEE", "DETAIL_FACTURE", idLigne, idFacture,
                motif.trim(),
                Map.of("quantite", valeur(ligne.quantite()), "montant", valeur(ligne.montant())),
                Map.of("quantite", nouvelleQuantite, "montant", nouveauMontant)));
    }

    private static Object valeur(BigDecimal v) {
        return v == null ? "vide" : v;
    }
}