package ht.dinasa.fact.facturation.infrastructure;

import ht.dinasa.fact.facturation.application.DepotCorrections;
import ht.dinasa.fact.shared.erreurs.ErreurMetier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
class DepotCorrectionsSql implements DepotCorrections {

    private static final String LIGNE = """
            SELECT d.ID_DETAIL_FACTURE, f.ID_FACTURE, f.NO_FACTURE, f.POSTE_YN, f.SIGLE_MONNAIE,
                   d.QTE_COMMANDEE, d.PRIX_TRANSP_GALON, d.MONTANT, d.CODE_MOUVEMENT
              FROM FACT.DETAIL_FACTURE d WITH (UPDLOCK)
              JOIN FACT.FACTURE f WITH (UPDLOCK) ON f.ID_FACTURE = d.CODE_FACTURE
             WHERE d.ID_DETAIL_FACTURE = :idLigne
               AND d.CODE_FACTURE = :idFacture
               AND d.PRIX_TRANSP_GALON IS NOT NULL
               AND d.CODE_TYPE_SERVICE_A_DEDUIRE IS NULL
            """;

    private static final String MAJ_LIGNE = """
            UPDATE FACT.DETAIL_FACTURE
               SET QTE_COMMANDEE = :quantite, MONTANT = :montant, MODIFBY = :par, DATEMODIF = :le
             WHERE ID_DETAIL_FACTURE = :idLigne
            """;

    private static final String MAJ_EN_TETE = """
            UPDATE FACT.FACTURE
               SET MONTANT = MONTANT + :difference,
                   MONTANT_TRANSACTION = ISNULL(MONTANT_TRANSACTION, 0) + :difference,
                   MODIFBY = :par, DATEMODIF = :le
             WHERE ID_FACTURE = :idFacture AND POSTE_YN = 0
            """;

    private final JdbcClient jdbc;

    DepotCorrectionsSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<LigneTransport> lignePourModification(long idFacture, long idLigne) {
        return jdbc.sql(LIGNE).param("idLigne", idLigne).param("idFacture", idFacture)
                .query((rs, n) -> {
                    long mvt = rs.getLong("CODE_MOUVEMENT");
                    Long idMouvement = rs.wasNull() ? null : mvt;
                    return new LigneTransport(
                            rs.getLong("ID_DETAIL_FACTURE"), rs.getLong("ID_FACTURE"), rs.getString("NO_FACTURE"),
                            rs.getBoolean("POSTE_YN"), rs.getString("SIGLE_MONNAIE"),
                            rs.getBigDecimal("QTE_COMMANDEE"), rs.getBigDecimal("PRIX_TRANSP_GALON"),
                            rs.getBigDecimal("MONTANT"), idMouvement);
                })
                .optional();
    }

    @Override
    public void appliquer(LigneTransport ligne, BigDecimal nouvelleQuantite, BigDecimal nouveauMontant,
                          String utilisateur) {
        LocalDateTime maintenant = LocalDateTime.now();
        BigDecimal difference = nouveauMontant.subtract(ligne.montant());

        jdbc.sql(MAJ_LIGNE)
                .param("quantite", nouvelleQuantite)
                .param("montant", nouveauMontant)
                .param("par", utilisateur)
                .param("le", maintenant)
                .param("idLigne", ligne.idLigne())
                .update();

        int maj = jdbc.sql(MAJ_EN_TETE)
                .param("difference", difference)
                .param("par", utilisateur)
                .param("le", maintenant)
                .param("idFacture", ligne.idFacture())
                .update();
        if (maj != 1) {
            throw new ErreurMetier("FACTURE_POSTEE",
                    "La facture " + ligne.numeroFacture() + " a été postée entre-temps.");
        }
    }
}