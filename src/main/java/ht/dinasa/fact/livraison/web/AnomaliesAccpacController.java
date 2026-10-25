package ht.dinasa.fact.livraison.web;

import ht.dinasa.fact.livraison.application.AnomaliesAccpac;
import ht.dinasa.fact.livraison.application.DepotAnomalies.BilanAnnulation;
import ht.dinasa.fact.shared.erreurs.ErreurMetier;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.YearMonth;

@RestController
@RequestMapping("/api/v1/accpac")
class AnomaliesAccpacController {

    record DemandeCorrection(@NotNull AnomaliesAccpac.TypeCorrection type,
                             @NotBlank @Size(max = 100) String nouveauCode,
                             @NotBlank @Size(max = 500) String motif) {
    }

    private final AnomaliesAccpac anomalies;

    AnomaliesAccpacController(AnomaliesAccpac anomalies) {
        this.anomalies = anomalies;
    }

    @Operation(summary = "Tableau de bord des anomalies d'un mois, avec les codes en double (rien n'est écrit)")
    @GetMapping("/anomalies")
    AnomaliesAccpac.Tableau tableau(@RequestParam int annee, @RequestParam int mois,
                                    @RequestParam(defaultValue = "false") boolean inclureMoisPrecedent) {
        return anomalies.tableau(periode(annee, mois), inclureMoisPrecedent);
    }

    @Operation(summary = "Corriger le code camion (lignes du mois au même code) ou client (cette ligne) — motif obligatoire")
    @PostMapping("/anomalies/{idLigne}/correction")
    AnomaliesAccpac.ResultatCorrection corriger(@PathVariable long idLigne,
                                                @Valid @RequestBody DemandeCorrection demande) {
        return anomalies.corriger(idLigne, demande.type(), demande.nouveauCode(), demande.motif());
    }

    @Operation(summary = "Annuler la récupération d'un mois (refusé si un bon est déjà facturé) — motif obligatoire")
    @PostMapping("/annulation")
    BilanAnnulation annuler(@RequestParam int annee, @RequestParam int mois, @RequestParam String motif) {
        return anomalies.annuler(periode(annee, mois), motif);
    }

    private static YearMonth periode(int annee, int mois) {
        try {
            return YearMonth.of(annee, mois);
        } catch (DateTimeException e) {
            throw new ErreurMetier("PERIODE_INVALIDE", "Mois ou année invalide : " + mois + "/" + annee);
        }
    }
}