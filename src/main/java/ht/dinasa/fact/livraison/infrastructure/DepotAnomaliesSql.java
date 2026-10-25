package ht.dinasa.fact.livraison.infrastructure;

import ht.dinasa.fact.livraison.application.DepotAnomalies;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Optional;

@Repository
class DepotAnomaliesSql implements DepotAnomalies {

    private static final String LIGNE = """
            SELECT ID_Livraison, DateMouvement, CodeCamion, CodeClient, ISNULL(IsRetreive, 0) AS RECUPEREE
              FROM dbo.Livraison_Acpacc WITH (UPDLOCK)
             WHERE ID_Livraison = :id
            """;

    private static final String ID_CAMION = """
            SELECT TOP 1 ID_Camion FROM dbo.Camion
             WHERE RTRIM(LTRIM(CamionCode)) = :code ORDER BY ID_Camion DESC
            """;

    private static final String ID_CLIENT = """
            SELECT TOP 1 ID_CLIENT FROM FACT.CLIENT
             WHERE RTRIM(LTRIM(CODE_CLIENT)) = :code ORDER BY ID_CLIENT DESC
            """;

    /** Seul le camion change (l'ancien système écrasait aussi le code client de toutes ces lignes). */
    private static final String CORRIGER_CAMION = """
            UPDATE dbo.Livraison_Acpacc
               SET CodeCamion = :code, id_camion = :idCamion,
                   ModifiedBy = :par, ModifiedOn = :le,
                   ModificationLog = :log + ISNULL(ModificationLog, '')
             WHERE ISNULL(IsRetreive, 0) = 0
               AND DateMouvement >= :debut AND DateMouvement <= :fin
               AND (ID_Livraison = :id OR (:ancien <> '' AND RTRIM(LTRIM(CodeCamion)) = :ancien))
            """;

    private static final String CORRIGER_CLIENT = """
            UPDATE dbo.Livraison_Acpacc
               SET CodeClient = :code, id_client = :idClient,
                   ModifiedBy = :par, ModifiedOn = :le,
                   ModificationLog = :log + ISNULL(ModificationLog, '')
             WHERE ID_Livraison = :id AND ISNULL(IsRetreive, 0) = 0
            """;

    private static final String BONS_FACTURES = """
            SELECT COUNT(DISTINCT m.ID_Mouvement)
              FROM dbo.Livraison_Acpacc l
              JOIN dbo.Mouvement m ON m.ID_Mouvement = l.id_mouvement
             WHERE l.DateMouvement >= :debut AND l.DateMouvement <= :fin
               AND ((m.CodeFacture IS NOT NULL AND m.CodeFacture <> 0)
                    OR EXISTS (SELECT 1 FROM FACT.DETAIL_FACTURE d WHERE d.CODE_MOUVEMENT = m.ID_Mouvement))
            """;

    /**
     * Ne supprime QUE les bons créés par la récupération (CreatedBy = 'System', livraisons) rattachés aux lignes
     * de la période. L'historique des corrections (ModificationLog) est conservé.
     */
    private static final String ANNULER = """
            SET NOCOUNT ON;
            DECLARE @bons TABLE (id bigint PRIMARY KEY);
            DECLARE @details int, @lignes int, @supprimes int;

            INSERT INTO @bons (id)
            SELECT DISTINCT m.ID_Mouvement
              FROM dbo.Livraison_Acpacc l
              JOIN dbo.Mouvement m ON m.ID_Mouvement = l.id_mouvement
             WHERE l.DateMouvement >= :debut AND l.DateMouvement <= :fin
               AND m.CreatedBy = 'System' AND m.CodeTypeMouvement = 2;

            DELETE d FROM dbo.MouvementDetail d JOIN @bons b ON b.id = d.CodeMouvement;
            SET @details = @@ROWCOUNT;

            UPDATE dbo.Livraison_Acpacc
               SET IsRetreive = 0, id_client = NULL, id_client_fact = NULL, id_camion = NULL,
                   id_transporteur = NULL, id_produit = NULL, id_mouvement = NULL
             WHERE DateMouvement >= :debut AND DateMouvement <= :fin;
            SET @lignes = @@ROWCOUNT;

            DELETE m FROM dbo.Mouvement m JOIN @bons b ON b.id = m.ID_Mouvement;
            SET @supprimes = @@ROWCOUNT;

            SELECT @supprimes AS BONS, @details AS DETAILS, @lignes AS LIGNES;
            """;

    private final JdbcClient jdbc;

    DepotAnomaliesSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<LigneACorriger> ligne(long idLigne) {
        return jdbc.sql(LIGNE).param("id", idLigne)
                .query((rs, n) -> new LigneACorriger(rs.getLong("ID_Livraison"),
                        rs.getObject("DateMouvement", LocalDate.class), rs.getString("CodeCamion"),
                        rs.getString("CodeClient"), rs.getBoolean("RECUPEREE")))
                .optional();
    }

    @Override
    public Optional<Long> idCamion(String code) {
        return jdbc.sql(ID_CAMION).param("code", code).query(Long.class).optional();
    }

    @Override
    public Optional<Long> idClient(String code) {
        return jdbc.sql(ID_CLIENT).param("code", code).query(Long.class).optional();
    }

    @Override
    public int corrigerCamion(LigneACorriger ligne, String nouveauCode, long idCamion, String log, String utilisateur) {
        YearMonth mois = YearMonth.from(ligne.date());
        return jdbc.sql(CORRIGER_CAMION)
                .param("code", nouveauCode).param("idCamion", idCamion)
                .param("par", utilisateur).param("le", LocalDateTime.now()).param("log", log)
                .param("debut", mois.atDay(1)).param("fin", mois.atEndOfMonth())
                .param("id", ligne.id())
                .param("ancien", ligne.codeCamion() == null ? "" : ligne.codeCamion().trim())
                .update();
    }

    @Override
    public int corrigerClient(LigneACorriger ligne, String nouveauCode, long idClient, String log, String utilisateur) {
        return jdbc.sql(CORRIGER_CLIENT)
                .param("code", nouveauCode).param("idClient", idClient)
                .param("par", utilisateur).param("le", LocalDateTime.now()).param("log", log)
                .param("id", ligne.id())
                .update();
    }

    @Override
    public int bonsDejaFactures(LocalDate debut, LocalDate fin) {
        return jdbc.sql(BONS_FACTURES).param("debut", debut).param("fin", fin).query(Integer.class).single();
    }

    @Override
    public BilanAnnulation annuler(LocalDate debut, LocalDate fin) {
        return jdbc.sql(ANNULER).param("debut", debut).param("fin", fin)
                .query((rs, n) -> new BilanAnnulation(rs.getInt("BONS"), rs.getInt("DETAILS"), rs.getInt("LIGNES")))
                .single();
    }
}