package ht.dinasa.fact.facturation.web;

import ht.dinasa.fact.facturation.application.ApercuFacturation;
import ht.dinasa.fact.facturation.domain.ResultatCalcul;
import ht.dinasa.fact.shared.erreurs.ErreurMetier;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.YearMonth;

@RestController
@RequestMapping("/api/v1/facturation")
class ApercuFacturationController {

    private final ApercuFacturation apercu;

    ApercuFacturationController(ApercuFacturation apercu) {
        this.apercu = apercu;
    }

    @Operation(summary = "Aperçu des factures au gallon d'un mois (rien n'est enregistré)")
    @GetMapping("/apercu")
    ResultatCalcul apercu(@RequestParam int annee, @RequestParam int mois) {
        try {
            return apercu.pourLeMois(YearMonth.of(annee, mois));
        } catch (DateTimeException e) {
            throw new ErreurMetier("PERIODE_INVALIDE", "Mois ou année invalide : " + mois + "/" + annee);
        }
    }
}