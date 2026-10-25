package ht.dinasa.fact.facturation.application;

import java.util.List;

public interface RenduExcel {

    byte[] facture(LectureDocuments.Facture facture);

    byte[] factures(String titre, List<LectureDocuments.EnTete> factures);
}