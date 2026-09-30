package ht.dinasa.fact.facturation.infrastructure;

import ht.dinasa.fact.facturation.application.DepotSuppressions;
import ht.dinasa.fact.shared.erreurs.ErreurMetier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
class DepotSuppressionsSql implements DepotSuppressions {

    private static final String EST_SERVICE =
            "(d.CODE_TYPE_SERVICE_A_DEDUIRE IS NOT NULL OR d.PRIX_SERVICE IS NOT NULL)";

    private static final String LIGNE = """
            SELECT d.ID_DETAIL_FACTURE, f.ID_FACTURE, f.NO_FACTURE, f.POSTE_YN,
                   d.MONTANT, d.QTE_COMMANDEE, d.CODE_MOUVEMENT,
                   CASE WHEN %s THEN 'SERVICE'
                        WHEN d.PRIX_TRANSP_GALON IS NOT NULL AND d.FRAIS_FIXE IS NULL
                             AND d.PRIX_KILOMETRAGE IS NULL THEN 'TRANSPORT'
                        ELSE 'AUTRE' END AS TYPE_LIGNE
              FROM FACT.DETAIL_FACTURE d WITH (UPDLOCK)
              JOIN FACT.FACTURE f WITH (UPDLOCK) ON f.ID_FACTURE = d.CODE_FACTURE
             WHERE d.ID_DETAIL_FACTURE = :idLigne AND d.CODE_FACTURE = :idFacture
            """.formatted(EST_SERVICE);

    private static final String SERVICES_RATTACHES = """
            SELECT COUNT(*) FROM FACT.DETAIL_FACTURE d
             WHERE d.CODE_FACTURE = :idFacture AND d.CODE_MOUVEMENT = :bon
               AND d.ID_DETAIL_FACTURE <> :idLigne AND %s
            """.formatted(EST_SERVICE);

    private static final String AUTRE_BON = """
            SELECT TOP 1 d.CODE_MOUVEMENT FROM FACT.DETAIL_FACTURE d
             WHERE d.CODE_FACTURE = :idFacture AND d.ID_DETAIL_FACTURE <> :idLigne
               AND d.CODE_MOUVEMENT <> :bon
               AND d.PRIX_TRANSP_GALON IS NOT NULL AND NOT %s
             ORDER BY d.CODE_MOUVEMENT
            """.formatted(EST_SERVICE);

    private static final String RATTACHER = """
            UPDATE d SET d.CODE_MOUVEMENT = :nouveau, d.MODIFBY = :par, d.DATEMODIF = :le
              FROM FACT.DETAIL_FACTURE d
             WHERE d.CODE_FACTURE = :idFacture AND d.CODE_MOUVEMENT = :ancien AND %s
            """.formatted(EST_SERVICE);

    private static final String LIBERER_SERVICE = """
            UPDATE FACT.SERVICE_A_DEDUIRE_FACTURE
               SET CODE_FACTURE = NULL, FACTURE_YN = 0, MODIFBY = :par, DATEMODIF = :le
             WHERE CODE_FACTURE = :idLigne
            """;

    /** À faire AVANT de supprimer la ligne : sinon FK_Mouvement_DETAILFACTURE bloque la suppression. */
    private static final String DETACHER_BONS = """
            UPDATE m
               SET m.CodeFacture = (SELECT TOP 1 d.ID_DETAIL_FACTURE
                                      FROM FACT.DETAIL_FACTURE d
                                     WHERE d.CODE_MOUVEMENT = m.ID_Mouvement
                                       AND d.ID_DETAIL_FACTURE <> :idLigne
                                     ORDER BY CASE WHEN d.PRIX_TRANSP_GALON IS NOT NULL THEN 0 ELSE 1 END,
                                              d.ID_DETAIL_FACTURE),
                   m.ModifBy = :par, m.DateModif = :le
              FROM dbo.Mouvement m
             WHERE m.CodeFacture = :idLigne
            """;

    private static final String SUPPRIMER_LIGNE =
            "DELETE FROM FACT.DETAIL_FACTURE WHERE ID_DETAIL_FACTURE = :idLigne";

    private static final String MAJ_EN_TETE = """
            UPDATE FACT.FACTURE
               SET MONTANT = MONTANT - :montant,
                   MONTANT_TRANSACTION = CASE WHEN :transport = 1
                                              THEN ISNULL(MONTANT_TRANSACTION, 0) - :montant
                                              ELSE MONTANT_TRANSACTION END,
                   MONTANT_SERVICE = CASE WHEN :transport = 0
                                          THEN ISNULL(MONTANT_SERVICE, 0) - :montant
                                          ELSE MONTANT_SERVICE END,
                   MODIFBY = :par, DATEMODIF = :le
             WHERE ID_FACTURE = :idFacture AND POSTE_YN = 0
            """;

    private static final String LIBERER_BON = """
            UPDATE dbo.Mouvement SET CodeFacture = NULL, ModifBy = :par, DateModif = :le
             WHERE ID_Mouvement = :bon
               AND NOT EXISTS (SELECT 1 FROM FACT.DETAIL_FACTURE d WHERE d.CODE_MOUVEMENT = :bon)
            """;

    private static final String SUPPRIMER_EN_TETE_VIDE = """
            DELETE FROM FACT.FACTURE
             WHERE ID_FACTURE = :idFacture AND POSTE_YN = 0
               AND NOT EXISTS (SELECT 1 FROM FACT.DETAIL_FACTURE d WHERE d.CODE_FACTURE = :idFacture)
            """;

    private final JdbcClient jdbc;

    DepotSuppressionsSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Ligne> lignePourSuppression(long idFacture, long idLigne) {
        return jdbc.sql(LIGNE).param("idLigne", idLigne).param("idFacture", idFacture)
                .query((rs, n) -> {
                    long mvt = rs.getLong("CODE_MOUVEMENT");
                    Long idMouvement = rs.wasNull() ? null : mvt;
                    return new Ligne(rs.getLong("ID_DETAIL_FACTURE"), rs.getLong("ID_FACTURE"),
                            rs.getString("NO_FACTURE"), rs.getBoolean("POSTE_YN"),
                            TypeLigne.valueOf(rs.getString("TYPE_LIGNE")),
                            rs.getBigDecimal("MONTANT"), rs.getBigDecimal("QTE_COMMANDEE"), idMouvement);
                })
                .optional();
    }

    @Override
    public int nombreServicesRattaches(long idFacture, long idBon, long idLigneExclue) {
        return jdbc.sql(SERVICES_RATTACHES)
                .param("idFacture", idFacture).param("bon", idBon).param("idLigne", idLigneExclue)
                .query(Integer.class).single();
    }

    @Override
    public Optional<Long> autreBonDeTransport(long idFacture, long idLigneExclue, long idBonExclu) {
        return jdbc.sql(AUTRE_BON)
                .param("idFacture", idFacture).param("idLigne", idLigneExclue).param("bon", idBonExclu)
                .query(Long.class).optional();
    }

    @Override
    public void rattacherServices(long idFacture, long ancienBon, long nouveauBon, String utilisateur) {
        jdbc.sql(RATTACHER)
                .param("nouveau", nouveauBon).param("par", utilisateur).param("le", LocalDateTime.now())
                .param("idFacture", idFacture).param("ancien", ancienBon)
                .update();
    }

    @Override
    public void supprimer(Ligne ligne, String utilisateur) {
        LocalDateTime maintenant = LocalDateTime.now();
        if (ligne.type() == TypeLigne.SERVICE) {
            jdbc.sql(LIBERER_SERVICE)
                    .param("par", utilisateur).param("le", maintenant).param("idLigne", ligne.idLigne())
                    .update();
        }

        // NOUVEAU : détacher les bons qui pointent vers cette ligne, avant de la supprimer
        jdbc.sql(DETACHER_BONS)
                .param("idLigne", ligne.idLigne()).param("par", utilisateur).param("le", maintenant)
                .update();

        jdbc.sql(SUPPRIMER_LIGNE).param("idLigne", ligne.idLigne()).update();

        int maj = jdbc.sql(MAJ_EN_TETE)
                .param("montant", ligne.montant())
                .param("transport", ligne.type() == TypeLigne.TRANSPORT ? 1 : 0)
                .param("par", utilisateur).param("le", maintenant)
                .param("idFacture", ligne.idFacture())
                .update();
        if (maj != 1) {
            throw new ErreurMetier("FACTURE_POSTEE",
                    "La facture " + ligne.numeroFacture() + " a été postée entre-temps.");
        }
    }

    @Override
    public boolean libererBon(long idBon, String utilisateur) {
        return jdbc.sql(LIBERER_BON)
                .param("par", utilisateur).param("le", LocalDateTime.now()).param("bon", idBon)
                .update() == 1;
    }

    @Override
    public boolean supprimerEnTeteSiVide(long idFacture) {
        return jdbc.sql(SUPPRIMER_EN_TETE_VIDE).param("idFacture", idFacture).update() == 1;
    }
}