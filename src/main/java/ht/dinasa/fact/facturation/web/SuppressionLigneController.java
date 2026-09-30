package ht.dinasa.fact.facturation.web;

import ht.dinasa.fact.facturation.application.ConsultationFactures;
import ht.dinasa.fact.facturation.application.FicheFacture;
import ht.dinasa.fact.facturation.application.SuppressionLigne;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/factures/{idFacture}/lignes")
class SuppressionLigneController {

    /** facture vaut null si elle a été supprimée (plus aucune ligne). */
    record ReponseSuppression(boolean factureSupprimee, boolean bonRedevenuFacturable, FicheFacture facture) {
    }

    private final SuppressionLigne suppression;
    private final ConsultationFactures consultation;

    SuppressionLigneController(SuppressionLigne suppression, ConsultationFactures consultation) {
        this.suppression = suppression;
        this.consultation = consultation;
    }

    @Operation(summary = "Supprimer une ligne de facture (motif obligatoire)")
    @DeleteMapping("/{idLigne}")
    ReponseSuppression supprimer(@PathVariable long idFacture, @PathVariable long idLigne,
                                 @RequestParam String motif) {
        SuppressionLigne.Resultat r = suppression.supprimer(idFacture, idLigne, motif);
        return new ReponseSuppression(r.factureSupprimee(), r.bonRedevenuFacturable(),
                r.factureSupprimee() ? null : consultation.detail(idFacture));
    }
}