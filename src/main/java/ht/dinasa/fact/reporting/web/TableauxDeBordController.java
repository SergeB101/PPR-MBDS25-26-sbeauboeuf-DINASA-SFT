package ht.dinasa.fact.reporting.web;

import ht.dinasa.fact.reporting.application.LectureTableauxDeBord.CoutTransporteur;
import ht.dinasa.fact.reporting.application.LectureTableauxDeBord.FactureEnAttente;
import ht.dinasa.fact.reporting.application.LectureTableauxDeBord.VolumeMois;
import ht.dinasa.fact.reporting.application.TableauxDeBord;
import ht.dinasa.fact.shared.erreurs.ErreurMetier;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/v1/tableaux-de-bord")
class TableauxDeBordController {

    private final TableauxDeBord tableaux;

    TableauxDeBordController(TableauxDeBord tableaux) {
        this.tableaux = tableaux;
    }

    @Operation(summary = "Synthèse du mois : livraisons, coût du transport, factures en attente, anomalies ACCPAC")
    @GetMapping("/synthese")
    TableauxDeBord.Synthese synthese(@RequestParam int annee, @RequestParam int mois) {
        return tableaux.synthese(periode(annee, mois));
    }

    @Operation(summary = "Volumes et coûts mois par mois (12 lignes)")
    @GetMapping("/volumes-par-mois")
    List<VolumeMois> volumesParMois(@RequestParam int annee) {
        return tableaux.volumesParMois(annee);
    }

    @Operation(summary = "Coût du transport par transporteur pour un mois")
    @GetMapping("/couts-par-transporteur")
    List<CoutTransporteur> coutsParTransporteur(@RequestParam int annee, @RequestParam int mois) {
        return tableaux.coutsParTransporteur(periode(annee, mois));
    }

    @Operation(summary = "Factures en attente de posting, les plus anciennes d'abord")
    @GetMapping("/factures-en-attente")
    List<FactureEnAttente> facturesEnAttente(@RequestParam(defaultValue = "100") int limite) {
        return tableaux.facturesEnAttente(limite);
    }

    private static YearMonth periode(int annee, int mois) {
        try {
            return YearMonth.of(annee, mois);
        } catch (DateTimeException e) {
            throw new ErreurMetier("PERIODE_INVALIDE", "Mois ou année invalide : " + mois + "/" + annee);
        }
    }
}