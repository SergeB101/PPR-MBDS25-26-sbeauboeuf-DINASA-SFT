package ht.dinasa.fact.livraison.infrastructure;

import ht.dinasa.fact.livraison.application.DepotRecuperation;
import ht.dinasa.fact.livraison.domain.BonACreer;
import ht.dinasa.fact.livraison.domain.RegleClientFacture.CamionJour;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/** Écritures de la récupération, identiques à celles de l'ancienne procédure (CreatedBy = 'System'). */
@Repository
class DepotRecuperationSql implements DepotRecuperation {

    private static final String PARAM_DERNIER_BON = "LAST_MOUVEMENT_ID_BEFORE_RETREIVING_DATA_FROM_ACCPAC";

    private static final String MAJ_PARAM = """
            UPDATE FACT.PARAMETRES_GENERAUX
               SET VALEUR = (SELECT CAST(MAX(ID_Mouvement) AS nvarchar(100)) FROM dbo.Mouvement),
                   MODIFBY = :par, DATEMODIF = :le
             WHERE LIBELLE = :libelle
            """;

    private static final String INSERER_PARAM = """
            INSERT INTO FACT.PARAMETRES_GENERAUX (LIBELLE, VALEUR, CREATEDBY, DATECREATED)
            VALUES (:libelle, (SELECT CAST(MAX(ID_Mouvement) AS nvarchar(100)) FROM dbo.Mouvement), :par, :le)
            """;

    /**
     * Copie FIDÈLE des 5 instructions de reconnaissance de FACT.PS_ACCPAC_RetreiveData_ByMonthAndYear,
     * avec des dates de type datetime comme la procédure. En cas de code en double, SQL Server
     * fait ainsi le même choix que l'ancienne procédure (choix non garanti : voir le tableau de bord).
     */
    private static final String CORRESPONDANCES = """
            SET NOCOUNT ON;
            DECLARE @DateDebut datetime = :debut, @DateFin datetime = :fin;

            update l set l.id_client = c.ID_CLIENT
            from Livraison_Acpacc l
            join fact.client c on rtrim(ltrim(l.CodeClient)) = rtrim(ltrim(c.CODE_CLIENT))
            where l.shipto = ''
              and l.DateMouvement >= @DateDebut and l.DateMouvement <= @DateFin
              and IsRetreive = 0;

            update l set l.id_client = rtrim(ltrim(c.ID_CLIENT))
            from Livraison_Acpacc l
            join [FACT].[zShip_To_Locations] stl on rtrim(ltrim(stl.IDCUST)) = rtrim(ltrim(l.CodeClient))
                                                and rtrim(ltrim(stl.IDCUSTSHPT)) = rtrim(ltrim(l.ShipTo))
            join [FACT].[REF_POINT_LIVRAISON] pl on pl.LIBELLE = stl.TEXTSTRE2
            join [FACT].[CLIENT] c on c.CODE_CLIENT = l.CodeClient and c.CODE_POINT_LIVRAISON = pl.ID_POINT_LIVRAISON
            where l.ShipTo <> ''
              and l.DateMouvement >= @DateDebut and l.DateMouvement <= @DateFin
              and IsRetreive = 0;

            update l set l.id_camion = c.ID_CAMION
            from Livraison_Acpacc l
            join dbo.Camion c on Rtrim(Ltrim(l.CodeCamion)) = Rtrim(Ltrim(c.CamionCode))
            where l.DateMouvement >= @DateDebut and l.DateMouvement <= @DateFin;

            update l set l.id_transporteur = c.CODE_TRANSPORTEUR
            from Livraison_Acpacc l
            join dbo.Camion c on Rtrim(Ltrim(l.CodeCamion)) = Rtrim(Ltrim(c.CamionCode))
            where l.DateMouvement between @DateDebut and @DateFin;

            update l set l.id_produit = p.Id_Produit
            from Livraison_Acpacc l
            join [dbo].[Produit] p on Rtrim(Ltrim(p.ProduitCode)) = Rtrim(Ltrim(l.CodeProduit))
            where l.id_produit is null
              and l.DateMouvement between @DateDebut and @DateFin;
            """;

    private static final String INSERER_BON = """
            INSERT INTO dbo.Mouvement
                   (CodeClient, CodeMouvement, CodeTypeMouvement, CodeUniteVolume, CodeCompagnie, NoCommande,
                    DateMouvement, CodeCamion, CodeTransporteur, NoFacture, CodeStatutMouvement,
                    IsAutoTransporteur, IsLivre, DateLivraison, CreatedBy, DateCreated)
            OUTPUT INSERTED.ID_Mouvement
            VALUES (:client, :numero, 2, 2, 1, :numero,
                    :date, :camion, :transporteur, :numero, 2,
                    0, 1, :date, 'System', GETDATE())
            """;

    private static final String INSERER_DETAIL = """
            INSERT INTO dbo.MouvementDetail
                   (CodeMouvement, CodeProduit, VolumeCommande, Volumefourni, VolumeLivre, CreatedBy, DateCreated)
            VALUES (:bon, :produit, :commande, :fourni, :livre, 'System', GETDATE())
            """;

    private static final String LIER_LIGNES = """
            UPDATE dbo.Livraison_Acpacc SET id_mouvement = :bon WHERE ID_Livraison IN (:ids)
            """;

    /** Même règle que l'ancienne procédure : toute ligne de la période dont la facture a un bon. */
    private static final String MARQUER = """
            UPDATE l SET l.IsRetreive = 1
              FROM dbo.Livraison_Acpacc l
              JOIN dbo.Mouvement m ON m.NoCommande = l.NoFacture
             WHERE l.DateMouvement BETWEEN :debut AND :fin
               AND ISNULL(l.IsRetreive, 0) = 0
            """;

    private static final String CLIENT_FACTURE_LIGNES = """
            UPDATE dbo.Livraison_Acpacc SET id_client_fact = :client
             WHERE id_camion = :camion AND DateMouvement = :date
               AND CodeCamion NOT LIKE 'OT%' AND LEN(CodeCamion) > 0 AND IsRetreive = 1
               AND (id_client_fact IS NULL OR id_client_fact = 0)
            """;

    private static final String CLIENT_FACTURE_BONS = """
            UPDATE m SET m.CodeClientFact = l.id_client_fact
              FROM dbo.Mouvement m
              JOIN dbo.Livraison_Acpacc l ON RTRIM(LTRIM(m.NoCommande)) = RTRIM(LTRIM(l.NoFacture))
             WHERE l.IsRetreive = 1
               AND l.id_client_fact IS NOT NULL AND l.id_client_fact <> 0
               AND (m.CodeClientFact = 0 OR m.CodeClientFact IS NULL)
               AND l.DateMouvement BETWEEN :debut AND :fin
            """;

    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate lots;

    DepotRecuperationSql(JdbcClient jdbc, NamedParameterJdbcTemplate lots) {
        this.jdbc = jdbc;
        this.lots = lots;
    }

    @Override
    public void memoriserDernierBonAvantRecuperation(String utilisateur) {
        LocalDateTime maintenant = LocalDateTime.now();
        int maj = jdbc.sql(MAJ_PARAM)
                .param("par", utilisateur).param("le", maintenant).param("libelle", PARAM_DERNIER_BON)
                .update();
        if (maj == 0) {
            jdbc.sql(INSERER_PARAM)
                    .param("libelle", PARAM_DERNIER_BON).param("par", utilisateur).param("le", maintenant)
                    .update();
        }
    }

    @Override
    public void etablirCorrespondances(LocalDate debut, LocalDate fin) {
        jdbc.sql(CORRESPONDANCES).param("debut", debut).param("fin", fin).update();
    }

    @Override
    public long creerBon(BonACreer bon) {
        long idBon = jdbc.sql(INSERER_BON)
                .param("client", bon.idClient())
                .param("numero", bon.noFacture())
                .param("date", bon.date())
                .param("camion", bon.idCamion())
                .param("transporteur", bon.idTransporteur())
                .query(Long.class).single();

        for (BonACreer.Detail d : bon.details()) {
            jdbc.sql(INSERER_DETAIL)
                    .param("bon", idBon)
                    .param("produit", d.idProduit(), Types.TINYINT)
                    .param("commande", d.volumeCommande(), Types.DECIMAL)
                    .param("fourni", d.volumeFourni(), Types.DECIMAL)
                    .param("livre", d.volumeLivre(), Types.DECIMAL)
                    .update();
        }
        jdbc.sql(LIER_LIGNES).param("bon", idBon).param("ids", bon.idsLignesAccpac()).update();
        return idBon;
    }

    @Override
    public int marquerRecuperees(LocalDate debut, LocalDate fin) {
        return jdbc.sql(MARQUER).param("debut", debut).param("fin", fin).update();
    }

    @Override
    public int appliquerClientsFactures(Map<CamionJour, Long> clients, LocalDate debut, LocalDate fin) {
        MapSqlParameterSource[] parametres = clients.entrySet().stream()
                .map(e -> new MapSqlParameterSource()
                        .addValue("client", e.getValue())
                        .addValue("camion", e.getKey().idCamion())
                        .addValue("date", e.getKey().date()))
                .toArray(MapSqlParameterSource[]::new);
        if (parametres.length > 0) {
            lots.batchUpdate(CLIENT_FACTURE_LIGNES, parametres);
        }
        return jdbc.sql(CLIENT_FACTURE_BONS).param("debut", debut).param("fin", fin).update();
    }

    @Override
    public void genererRapportOT() {
        jdbc.sql("EXEC FACT.PS_Generate_OT_Shipments").update();
    }
}