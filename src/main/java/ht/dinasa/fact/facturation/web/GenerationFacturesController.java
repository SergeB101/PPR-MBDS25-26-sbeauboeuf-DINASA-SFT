package ht.dinasa.fact.facturation.web;

import ht.dinasa.fact.facturation.application.GenerationFactures;
import ht.dinasa.fact.facturation.domain.ResultatCalcul;
import ht.dinasa.fact.shared.erreurs.ErreurMetier;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/v1/facturation")
class GenerationFacturesController {

    private final GenerationFactures generation;

    GenerationFacturesController(GenerationFactures generation) {
        this.generation = generation;
    }

    @Operation(summary = "Génère et ENREGISTRE les factures au gallon d'un mois (liste de bons facultative)")
    @PostMapping("/generation")
    ResultatCalcul generer(@RequestParam int annee, @RequestParam int mois,
                           @RequestBody(required = false) List<Long> idsMouvements) {
        YearMonth periode;
        try {
            periode = YearMonth.of(annee, mois);
        } catch (DateTimeException e) {
            throw new ErreurMetier("PERIODE_INVALIDE", "Mois ou année invalide : " + mois + "/" + annee);
        }
        return generation.generer(periode, idsMouvements);
    }
}