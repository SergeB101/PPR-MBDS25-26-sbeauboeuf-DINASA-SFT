package ht.dinasa.fact.livraison.web;

import ht.dinasa.fact.livraison.application.RecuperationAccpac;
import ht.dinasa.fact.shared.erreurs.ErreurMetier;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.YearMonth;

@RestController
@RequestMapping("/api/v1/accpac")
class RecuperationAccpacController {

    private final RecuperationAccpac recuperation;

    RecuperationAccpacController(RecuperationAccpac recuperation) {
        this.recuperation = recuperation;
    }

    @Operation(summary = "Contrôler un mois : bons prévus et anomalies (rien n'est écrit)")
    @GetMapping("/controle")
    RecuperationAccpac.Controle controler(@RequestParam int annee, @RequestParam int mois,
                                          @RequestParam(defaultValue = "false") boolean inclureMoisPrecedent) {
        return recuperation.controler(periode(annee, mois), inclureMoisPrecedent);
    }

    @Operation(summary = "Récupérer les bons d'un mois depuis ACCPAC (tout ou rien)")
    @PostMapping("/recuperation")
    RecuperationAccpac.Bilan recuperer(@RequestParam int annee, @RequestParam int mois,
                                       @RequestParam(defaultValue = "false") boolean inclureMoisPrecedent) {
        return recuperation.recuperer(periode(annee, mois), inclureMoisPrecedent);
    }

    private static YearMonth periode(int annee, int mois) {
        try {
            return YearMonth.of(annee, mois);
        } catch (DateTimeException e) {
            throw new ErreurMetier("PERIODE_INVALIDE", "Mois ou année invalide : " + mois + "/" + annee);
        }
    }
}