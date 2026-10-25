package ht.dinasa.fact.livraison.infrastructure;

import ht.dinasa.fact.livraison.application.SourceAccpac;
import ht.dinasa.fact.livraison.domain.LigneAccpac;
import ht.dinasa.fact.livraison.domain.RegleClientFacture;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
class SourceAccpacSql implements SourceAccpac {

    private static final String PREMIERE_DATE = """
            SELECT TOP 1 TRY_CAST(VALEUR AS date) FROM FACT.PARAMETRES_GENERAUX
             WHERE LIBELLE = 'ACCPAC_DATA_STARTING_DATE'
            """;

    /**
     * Lignes non récupérées, avec les correspondances écrites par DepotRecuperation.etablirCorrespondances.
     * Le transporteur n'est retenu que s'il a une fiche Personne (règle de l'ancienne procédure).
     */
    private static final String LIGNES = """
            SELECT l.ID_Livraison, l.NoFacture, l.DateMouvement, l.CodeCamion,
                   l.id_client, l.id_camion, l.id_produit, l.Volumefourni, l.VolumeLivre,
                   CASE WHEN EXISTS (SELECT 1 FROM dbo.Personne pe WHERE pe.ID_Personne = l.id_transporteur)
                        THEN l.id_transporteur END AS ID_TRANSPORTEUR_VALIDE
              FROM dbo.Livraison_Acpacc l
             WHERE l.IsRetreive = 0
               AND l.DateMouvement >= :debut AND l.DateMouvement <= :fin
             ORDER BY l.DateMouvement, l.NoFacture, l.ID_Livraison
            """;

    private static final String LIVRAISONS_RECUPEREES = """
            SELECT a.id_camion, a.DateMouvement, a.id_client, pl.DISTANCE
              FROM dbo.Livraison_Acpacc a
              JOIN FACT.CLIENT c ON c.ID_CLIENT = a.id_client
              JOIN FACT.REF_POINT_LIVRAISON pl ON pl.ID_POINT_LIVRAISON = c.CODE_POINT_LIVRAISON
             WHERE a.IsRetreive = 1
               AND a.CodeCamion NOT LIKE 'OT%' AND LEN(a.CodeCamion) > 0
               AND a.id_camion IS NOT NULL
               AND a.DateMouvement >= :debut AND a.DateMouvement <= :fin
            """;

    private final JdbcClient jdbc;

    SourceAccpacSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<LocalDate> premiereDateAccpac() {
        return jdbc.sql(PREMIERE_DATE).query(LocalDate.class).optional();
    }

    @Override
    public List<LigneAccpac> lignesARecuperer(LocalDate debut, LocalDate fin) {
        return jdbc.sql(LIGNES).param("debut", debut).param("fin", fin)
                .query((rs, n) -> new LigneAccpac(
                        rs.getLong("ID_Livraison"),
                        rs.getString("NoFacture"),
                        rs.getObject("DateMouvement", LocalDate.class),
                        rs.getString("CodeCamion"),
                        longOuVide(rs, "id_client"),
                        longOuVide(rs, "id_camion"),
                        longOuVide(rs, "ID_TRANSPORTEUR_VALIDE"),
                        entierOuVide(rs, "id_produit"),
                        rs.getBigDecimal("Volumefourni"),
                        rs.getBigDecimal("VolumeLivre")))
                .list();
    }

    @Override
    public List<RegleClientFacture.Livraison> livraisonsRecuperees(LocalDate debut, LocalDate fin) {
        return jdbc.sql(LIVRAISONS_RECUPEREES).param("debut", debut).param("fin", fin)
                .query((rs, n) -> new RegleClientFacture.Livraison(
                        rs.getLong("id_camion"),
                        rs.getObject("DateMouvement", LocalDate.class),
                        rs.getLong("id_client"),
                        rs.getBigDecimal("DISTANCE")))
                .list();
    }

    private static Long longOuVide(ResultSet rs, String colonne) throws SQLException {
        long v = rs.getLong(colonne);
        return rs.wasNull() ? null : v;
    }

    private static Integer entierOuVide(ResultSet rs, String colonne) throws SQLException {
        int v = rs.getInt(colonne);
        return rs.wasNull() ? null : v;
    }
}