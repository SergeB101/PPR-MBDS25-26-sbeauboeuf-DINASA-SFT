package ht.dinasa.fact.facturation.infrastructure;

import ht.dinasa.fact.facturation.application.BonsAFacturer;
import ht.dinasa.fact.facturation.domain.BonDeLivraison;
import ht.dinasa.fact.facturation.domain.MethodeTarification;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.List;

@Repository
class BonsAFacturerSql implements BonsAFacturer {

    private static final String SQL = """
            SELECT m.ID_Mouvement,
                   m.NoCommande,
                   COALESCE(m.CodeTransporteur, c.CODE_TRANSPORTEUR) AS CODE_TRANSPORTEUR,
                   c.CamionCode,
                   c.CODE_TYPE_CAMION,
                   c.CODE_METHODE_TARIFICATION,
                   cl.ID_CLIENT,
                   cl.CODE_POINT_LIVRAISON,
                   (SELECT COALESCE(SUM(md.VolumeCommande), 0)
                      FROM dbo.MouvementDetail md
                     WHERE md.CodeMouvement = m.ID_Mouvement) AS QUANTITE
              FROM dbo.Mouvement m
              JOIN dbo.Camion c   ON c.ID_Camion = m.CodeCamion
              JOIN FACT.CLIENT cl ON cl.ID_CLIENT = COALESCE(NULLIF(m.CodeClientFact, 0), m.CodeClient)
             WHERE m.CodeTypeMouvement = 2
               AND m.estRenverse = 0
               AND m.DateMouvement >= :debut AND m.DateMouvement < :fin
               AND (:inclureDejaFactures = 1
                    OR NOT EXISTS (SELECT 1 FROM FACT.DETAIL_FACTURE df
                                    WHERE df.CODE_MOUVEMENT = m.ID_Mouvement))
             ORDER BY CODE_TRANSPORTEUR, m.DateMouvement, m.ID_Mouvement
            """;

    private final JdbcClient jdbc;

    BonsAFacturerSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<BonDeLivraison> duMois(YearMonth mois, boolean inclureDejaFactures) {
        return jdbc.sql(SQL)
                .param("debut", mois.atDay(1))
                .param("fin", mois.plusMonths(1).atDay(1))
                .param("inclureDejaFactures", inclureDejaFactures ? 1 : 0)
                .query(this::versBon)
                .list();
    }

    private BonDeLivraison versBon(ResultSet rs, int ligne) throws SQLException {
        return new BonDeLivraison(
                rs.getLong("ID_Mouvement"),
                rs.getString("NoCommande"),
                rs.getLong("CODE_TRANSPORTEUR"),
                rs.getString("CamionCode"),
                entierOuVide(rs, "CODE_TYPE_CAMION"),
                methode(entierOuVide(rs, "CODE_METHODE_TARIFICATION")),
                rs.getLong("ID_CLIENT"),
                entierOuVide(rs, "CODE_POINT_LIVRAISON"),
                rs.getBigDecimal("QUANTITE"));
    }

    /** Table FACT.METHODE_TARIFICATION : 1 = PAR GALLON, 2 = FIXE VARIABLE. */
    private static MethodeTarification methode(Integer code) {
        if (code == null) return null;
        return switch (code) {
            case 1 -> MethodeTarification.GALLON;
            case 2 -> MethodeTarification.FIXE_VARIABLE;
            default -> null;
        };
    }

    static Integer entierOuVide(ResultSet rs, String colonne) throws SQLException {
        int valeur = rs.getInt(colonne);
        return rs.wasNull() ? null : valeur;
    }
}