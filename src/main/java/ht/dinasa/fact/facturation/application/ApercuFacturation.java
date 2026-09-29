package ht.dinasa.fact.facturation.application;

import ht.dinasa.fact.facturation.domain.CalculFactureGallon;
import ht.dinasa.fact.facturation.domain.ResultatCalcul;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;

/** Calcule les factures d'un mois sans rien enregistrer. */
@Service
public class ApercuFacturation {

    private final BonsAFacturer bons;
    private final SourceTarifs tarifs;
    private final Clock horloge;
    private final CalculFactureGallon calcul = new CalculFactureGallon();

    public ApercuFacturation(BonsAFacturer bons, SourceTarifs tarifs, Clock horloge) {
        this.bons = bons;
        this.tarifs = tarifs;
        this.horloge = horloge;
    }

    @Transactional(readOnly = true)
    public ResultatCalcul pourLeMois(YearMonth mois) {
        return calcul.calculer(bons.duMois(mois, false), tarifs.actifs(), LocalDateTime.now(horloge));
    }
}