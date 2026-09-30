package ht.dinasa.fact.facturation.web;

import ht.dinasa.fact.facturation.application.ConsultationFactures;
import ht.dinasa.fact.facturation.application.CriteresFactures;
import ht.dinasa.fact.facturation.application.FicheFacture;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/factures")
class FacturesController {

    private final ConsultationFactures consultation;

    FacturesController(ConsultationFactures consultation) {
        this.consultation = consultation;
    }

    @Operation(summary = "Liste des factures (filtres facultatifs, du plus récent au plus ancien)")
    @GetMapping
    List<FicheFacture> rechercher(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) Long transporteur,
            @RequestParam(required = false) Boolean postee,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int taille) {
        return consultation.rechercher(new CriteresFactures(du, au, transporteur, postee, page, taille));
    }

    @Operation(summary = "Détail d'une facture avec toutes ses lignes")
    @GetMapping("/{id}")
    FicheFacture detail(@PathVariable long id) {
        return consultation.detail(id);
    }
}