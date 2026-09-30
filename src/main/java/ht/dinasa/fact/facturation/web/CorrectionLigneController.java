package ht.dinasa.fact.facturation.web;

import ht.dinasa.fact.facturation.application.ConsultationFactures;
import ht.dinasa.fact.facturation.application.CorrectionLigne;
import ht.dinasa.fact.facturation.application.FicheFacture;
import ht.dinasa.fact.shared.audit.EntreeJournal;
import ht.dinasa.fact.shared.audit.Journal;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/factures/{idFacture}")
class CorrectionLigneController {

    record DemandeCorrection(@NotNull @PositiveOrZero BigDecimal quantite,
                             @NotBlank @Size(max = 500) String motif) {
    }

    private final CorrectionLigne correction;
    private final ConsultationFactures consultation;
    private final Journal journal;

    CorrectionLigneController(CorrectionLigne correction, ConsultationFactures consultation, Journal journal) {
        this.correction = correction;
        this.consultation = consultation;
        this.journal = journal;
    }

    @Operation(summary = "Corriger la quantité d'une ligne de transport (motif obligatoire)")
    @PatchMapping("/lignes/{idLigne}")
    FicheFacture corriger(@PathVariable long idFacture, @PathVariable long idLigne,
                          @Valid @RequestBody DemandeCorrection demande) {
        correction.corrigerQuantite(idFacture, idLigne, demande.quantite(), demande.motif());
        return consultation.detail(idFacture);
    }

    @Operation(summary = "Historique des opérations sur une facture (journal d'audit)")
    @GetMapping("/historique")
    List<EntreeJournal> historique(@PathVariable long idFacture) {
        return journal.historiqueFacture(idFacture);
    }
}