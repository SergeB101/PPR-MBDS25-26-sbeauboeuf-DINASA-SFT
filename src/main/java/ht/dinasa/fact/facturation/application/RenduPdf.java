package ht.dinasa.fact.facturation.application;

import java.time.LocalDateTime;

public interface RenduPdf {

    byte[] facture(LectureDocuments.Facture facture, LocalDateTime genereLe);

    byte[] releve(LectureDocuments.Releve releve, LocalDateTime genereLe);
}