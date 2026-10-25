package ht.dinasa.fact.livraison.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public record LigneAccpac(
        long idLigne,
        String noFacture,
        LocalDate date,
        String codeCamion,
        Long idClient,
        Long idCamion,
        Long idTransporteur,
        Integer idProduit,
        BigDecimal volumeFourni,
        BigDecimal volumeLivre) {

    /** Préfixe des camions hors contrat, exclus de la récupération (règle de l'ancienne procédure). */
    public static final String PREFIXE_OT = "OT";

    public boolean estOT() {
        return codeCamion != null && codeCamion.trim().toUpperCase().startsWith(PREFIXE_OT);
    }

    public List<TypeAnomalie> anomalies() {
        List<TypeAnomalie> liste = new ArrayList<>();
        if (noFacture == null || noFacture.isBlank()) {
            liste.add(TypeAnomalie.NUMERO_FACTURE_ABSENT);
        }
        if (codeCamion == null || codeCamion.isBlank()) {
            liste.add(TypeAnomalie.CAMION_NON_PRECISE);
        } else if (idCamion == null) {
            liste.add(TypeAnomalie.CAMION_INCONNU);
        } else if (idTransporteur == null) {
            liste.add(TypeAnomalie.TRANSPORTEUR_INCONNU);
        }
        if (idClient == null) {
            liste.add(TypeAnomalie.CLIENT_INCONNU);
        }
        if (idProduit == null) {
            liste.add(TypeAnomalie.PRODUIT_INCONNU);
        }
        return liste;
    }

    public boolean estRecuperable() {
        return anomalies().stream().noneMatch(TypeAnomalie::bloquante);
    }
}