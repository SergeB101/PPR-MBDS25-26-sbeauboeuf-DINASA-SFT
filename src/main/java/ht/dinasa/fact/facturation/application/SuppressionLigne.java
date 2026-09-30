package ht.dinasa.fact.facturation.application;

import ht.dinasa.fact.facturation.application.DepotSuppressions.Ligne;
import ht.dinasa.fact.facturation.application.DepotSuppressions.TypeLigne;
import ht.dinasa.fact.shared.UtilisateurCourant;
import ht.dinasa.fact.shared.audit.EntreeAudit;
import ht.dinasa.fact.shared.audit.Journal;
import ht.dinasa.fact.shared.erreurs.ElementIntrouvable;
import ht.dinasa.fact.shared.erreurs.ErreurMetier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/** Suppression d'une ligne de facture (transport ou service), avec motif et audit. */
@Service
public class SuppressionLigne {

    public record Resultat(boolean factureSupprimee, boolean bonRedevenuFacturable) {
    }

    private final DepotSuppressions depot;
    private final Journal journal;
    private final UtilisateurCourant utilisateur;

    public SuppressionLigne(DepotSuppressions depot, Journal journal, UtilisateurCourant utilisateur) {
        this.depot = depot;
        this.journal = journal;
        this.utilisateur = utilisateur;
    }

    @Transactional
    public Resultat supprimer(long idFacture, long idLigne, String motif) {
        if (motif == null || motif.isBlank()) {
            throw new ErreurMetier("MOTIF_OBLIGATOIRE", "Le motif de la suppression est obligatoire.");
        }
        Ligne ligne = depot.lignePourSuppression(idFacture, idLigne)
                .orElseThrow(() -> new ElementIntrouvable("Ligne de la facture " + idFacture, idLigne));
        if (ligne.facturePostee()) {
            throw new ErreurMetier("FACTURE_POSTEE",
                    "La facture " + ligne.numeroFacture() + " est postée : elle ne peut plus être modifiée.");
        }
        if (ligne.type() == TypeLigne.AUTRE) {
            throw new ErreurMetier("LIGNE_NON_PRISE_EN_CHARGE",
                    "Cette ligne (fixe + variable) n'est pas prise en charge par la nouvelle application.");
        }
        String par = utilisateur.login();

        // L'« artifice » : des services peuvent être rattachés au bon de cette ligne
        if (ligne.type() == TypeLigne.TRANSPORT && ligne.idMouvement() != null
                && depot.nombreServicesRattaches(idFacture, ligne.idMouvement(), idLigne) > 0) {
            long autreBon = depot.autreBonDeTransport(idFacture, idLigne, ligne.idMouvement())
                    .orElseThrow(() -> new ErreurMetier("SERVICES_A_RETIRER_D_ABORD",
                            "C'est la dernière ligne de transport de la facture " + ligne.numeroFacture()
                                    + " : retirez d'abord ses lignes de service."));
            depot.rattacherServices(idFacture, ligne.idMouvement(), autreBon, par);
        }

        depot.supprimer(ligne, par);

        boolean bonLibere = ligne.type() == TypeLigne.TRANSPORT && ligne.idMouvement() != null
                && depot.libererBon(ligne.idMouvement(), par);
        boolean factureSupprimee = depot.supprimerEnTeteSiVide(idFacture);

        Map<String, Object> avant = new HashMap<>();
        avant.put("type", ligne.type().name());
        avant.put("montant", ligne.montant());
        if (ligne.quantite() != null) avant.put("quantite", ligne.quantite());
        if (ligne.idMouvement() != null) avant.put("bon", ligne.idMouvement());

        journal.enregistrer(new EntreeAudit("FACTURE_LIGNE_SUPPRIMEE", "DETAIL_FACTURE", idLigne, idFacture,
                motif.trim(), avant, Map.of("bonRedevenuFacturable", bonLibere)));
        if (factureSupprimee) {
            journal.enregistrer(new EntreeAudit("FACTURE_SUPPRIMEE", "FACTURE", idFacture, idFacture,
                    "Plus aucune ligne après : " + motif.trim(), Map.of("numero", ligne.numeroFacture()), null));
        }
        return new Resultat(factureSupprimee, bonLibere);
    }
}