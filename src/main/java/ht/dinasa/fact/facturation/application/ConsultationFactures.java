package ht.dinasa.fact.facturation.application;

import ht.dinasa.fact.shared.erreurs.ElementIntrouvable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ConsultationFactures {

    private final LectureFactures lecture;

    public ConsultationFactures(LectureFactures lecture) {
        this.lecture = lecture;
    }

    public List<FicheFacture> rechercher(CriteresFactures criteres) {
        return lecture.rechercher(criteres);
    }

    public FicheFacture detail(long id) {
        return lecture.parId(id).orElseThrow(() -> new ElementIntrouvable("Facture", id));
    }
}