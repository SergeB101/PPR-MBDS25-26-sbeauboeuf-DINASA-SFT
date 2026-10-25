package ht.dinasa.fact.livraison.infrastructure;

import ht.dinasa.fact.livraison.application.LectureAnomalies;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
class LectureAnomaliesSql implements LectureAnomalies {

    private static final String CODES = """
            SELECT ID_Livraison, CodeClient, ShipTo, CodeProduit
              FROM dbo.Livraison_Acpacc
             WHERE IsRetreive = 0 AND DateMouvement >= :debut AND DateMouvement <= :fin
            """;

    /** Codes camion de la période présents sur plusieurs fiches ; signale si les TYPES diffèrent (tarif). */
    private static final String CAMIONS_EN_DOUBLE = """
            SELECT RTRIM(LTRIM(c.CamionCode)) AS CODE, COUNT(*) AS FICHES,
                   CASE WHEN COUNT(DISTINCT ISNULL(c.CODE_TYPE_CAMION, 255)) > 1 THEN 1 ELSE 0 END AS DIFFERENTS,
                   STRING_AGG(CONCAT(c.ID_Camion, ' (type ', ISNULL(CAST(c.CODE_TYPE_CAMION AS varchar(5)), '-'),
                                     ', statut ', c.CodeStatutCamion, ')'), ', ') AS DETAIL
              FROM dbo.Camion c
             WHERE RTRIM(LTRIM(c.CamionCode)) IN (
                     SELECT DISTINCT RTRIM(LTRIM(l.CodeCamion)) FROM dbo.Livraison_Acpacc l
                      WHERE l.DateMouvement >= :debut AND l.DateMouvement <= :fin
                        AND l.CodeCamion NOT LIKE 'OT%')
             GROUP BY RTRIM(LTRIM(c.CamionCode))
            HAVING COUNT(*) > 1
             ORDER BY CODE
            """;

    /** Codes client en double, pour les lignes sans adresse de livraison (ShipTo vide) ; signale si les POINTS diffèrent. */
    private static final String CLIENTS_EN_DOUBLE = """
            SELECT RTRIM(LTRIM(c.CODE_CLIENT)) AS CODE, COUNT(*) AS FICHES,
                   CASE WHEN COUNT(DISTINCT ISNULL(c.CODE_POINT_LIVRAISON, -1)) > 1 THEN 1 ELSE 0 END AS DIFFERENTS,
                   STRING_AGG(CONCAT(c.ID_CLIENT, ' (point ', ISNULL(CAST(c.CODE_POINT_LIVRAISON AS varchar(10)), '-'), ')'), ', ') AS DETAIL
              FROM FACT.CLIENT c
             WHERE RTRIM(LTRIM(c.CODE_CLIENT)) IN (
                     SELECT DISTINCT RTRIM(LTRIM(l.CodeClient)) FROM dbo.Livraison_Acpacc l
                      WHERE l.DateMouvement >= :debut AND l.DateMouvement <= :fin AND l.ShipTo = '')
             GROUP BY RTRIM(LTRIM(c.CODE_CLIENT))
            HAVING COUNT(*) > 1
             ORDER BY CODE
            """;

    private final JdbcClient jdbc;

    LectureAnomaliesSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<Long, CodesAccpac> codesAccpac(LocalDate debut, LocalDate fin) {
        Map<Long, CodesAccpac> codes = new HashMap<>();
        jdbc.sql(CODES).param("debut", debut).param("fin", fin)
                .query((rs, n) -> codes.put(rs.getLong("ID_Livraison"),
                        new CodesAccpac(rs.getString("CodeClient"), rs.getString("ShipTo"), rs.getString("CodeProduit"))))
                .list();
        return codes;
    }

    @Override
    public List<CodeEnDouble> camionsEnDouble(LocalDate debut, LocalDate fin) {
        return jdbc.sql(CAMIONS_EN_DOUBLE).param("debut", debut).param("fin", fin).query(this::versDouble).list();
    }

    @Override
    public List<CodeEnDouble> clientsEnDouble(LocalDate debut, LocalDate fin) {
        return jdbc.sql(CLIENTS_EN_DOUBLE).param("debut", debut).param("fin", fin).query(this::versDouble).list();
    }

    private CodeEnDouble versDouble(ResultSet rs, int n) throws SQLException {
        return new CodeEnDouble(rs.getString("CODE"), rs.getInt("FICHES"),
                rs.getInt("DIFFERENTS") == 1, rs.getString("DETAIL"));
    }
}