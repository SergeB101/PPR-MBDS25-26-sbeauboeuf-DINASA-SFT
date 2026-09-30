package ht.dinasa.fact.facturation.application;

import ht.dinasa.fact.facturation.application.RegistrePosting.EtatFacture;
import ht.dinasa.fact.shared.UtilisateurCourant;
import ht.dinasa.fact.shared.audit.EntreeAudit;                  // ← AUDIT
import ht.dinasa.fact.shared.audit.Journal;                      // ← AUDIT
import ht.dinasa.fact.shared.erreurs.ElementIntrouvable;
import ht.dinasa.fact.shared.erreurs.ErreurMetier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;                                            // ← AUDIT

/**
 * Posting version 1 : valider et verrouiller une facture.
 * Ne modifie PAS les soldes ni le relevé des transporteurs (module abandonné depuis 2021,
 * en attente de décision métier).
 */
@Service
public class PostingFactures {

    private final RegistrePosting registre;
    private final UtilisateurCourant utilisateur;
    private final Journal journal;                               // ← AUDIT (1. le champ)

    public PostingFactures(RegistrePosting registre, UtilisateurCourant utilisateur,
                           Journal journal) {                    // ← AUDIT (2. le paramètre)
        this.registre = registre;
        this.utilisateur = utilisateur;
        this.journal = journal;                                  // ← AUDIT
    }

    @Transactional
    public void poster(long idFacture) {
        EtatFacture etat = registre.etat(idFacture)
                .orElseThrow(() -> new ElementIntrouvable("Facture", idFacture));

        if (etat.postee()) {
            throw new ErreurMetier("FACTURE_DEJA_POSTEE",
                    "La facture " + etat.numero() + " est déjà postée.");
        }
        if (etat.nombreLignes() == 0) {
            throw new ErreurMetier("FACTURE_VIDE",
                    "La facture " + etat.numero() + " n'a aucune ligne.");
        }
        if (etat.montantEnTete().compareTo(etat.sommeDesLignes()) != 0) {
            throw new ErreurMetier("MONTANT_INCOHERENT",
                    "Facture " + etat.numero() + " : le montant de l'en-tête (" + etat.montantEnTete()
                            + ") est différent de la somme des lignes (" + etat.sommeDesLignes() + "). Veuillez vérifier.");
        }
        if (!registre.marquerPostee(idFacture, utilisateur.login())) {
            throw new ErreurMetier("FACTURE_DEJA_POSTEE",
                    "La facture " + etat.numero() + " vient d'être postée par un autre utilisateur.");
        }

        // ← AUDIT (3. l'appel, une fois le posting réussi)
        journal.enregistrer(new EntreeAudit(
                "FACTURE_POSTEE",             // action
                "FACTURE",                    // type d'objet
                idFacture,                    // identifiant de l'objet
                idFacture,                    // facture concernée
                null,                         // motif (aucun pour un posting)
                Map.of("postee", false),      // avant
                Map.of("postee", true, "montant", etat.montantEnTete())));   // après
    }
}