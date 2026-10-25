package ht.dinasa.fact.reporting.application;

import ht.dinasa.fact.reporting.application.LectureTableauxDeBord.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class TableauxDeBord {

    public record Synthese(String mois, Livraisons livraisons, CoutTransport coutTransport,
                           AttentePosting attentePosting, Accpac accpac, LocalDateTime calculeLe) {
    }

    private final LectureTableauxDeBord lecture;
    private final Clock horloge;

    public TableauxDeBord(LectureTableauxDeBord lecture, Clock horloge) {
        this.lecture = lecture;
        this.horloge = horloge;
    }

    public Synthese synthese(YearMonth mois) {
        LocalDate debut = mois.atDay(1);
        LocalDate fin = mois.plusMonths(1).atDay(1);
        return new Synthese(mois.toString(), lecture.livraisons(debut, fin), lecture.cout(debut, fin),
                lecture.attentePosting(), lecture.accpac(debut, fin), LocalDateTime.now(horloge));
    }

    public List<VolumeMois> volumesParMois(int annee) {
        return lecture.volumesParMois(annee);
    }

    public List<CoutTransporteur> coutsParTransporteur(YearMonth mois) {
        return lecture.coutsParTransporteur(mois.atDay(1), mois.plusMonths(1).atDay(1));
    }

    public List<FactureEnAttente> facturesEnAttente(int limite) {
        return lecture.facturesEnAttente(Math.clamp(limite, 1, 1000), LocalDate.now(horloge));
    }
}