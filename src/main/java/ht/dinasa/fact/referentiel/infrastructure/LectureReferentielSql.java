package ht.dinasa.fact.referentiel.infrastructure;

import ht.dinasa.fact.referentiel.application.LectureReferentiel;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

@Repository
class LectureReferentielSql implements LectureReferentiel {

    private static final String TRANSPORTEURS = """
            SELECT TOP (500) p.ID_Personne, RTRIM(p.Nom) AS NOM, RTRIM(p.Prenom) AS PRENOM, RTRIM(tp.Libelle) AS TYPE,
                   (SELECT COUNT(*) FROM dbo.Camion c WHERE c.CODE_TRANSPORTEUR = p.ID_Personne) AS CAMIONS
              FROM dbo.Personne p
              LEFT JOIN dbo.RefTypePersonne tp ON tp.Id_TypePersonne = p.CodeTypePersonne
             WHERE EXISTS (SELECT 1 FROM dbo.Camion c WHERE c.CODE_TRANSPORTEUR = p.ID_Personne)
               AND (:recherche = '' OR p.Nom LIKE :motif OR p.Prenom LIKE :motif
                    OR CAST(p.ID_Personne AS varchar(20)) = :recherche)
             ORDER BY p.Nom, p.Prenom
            """;

    private static final String CAMIONS = """
            SELECT TOP (1000) c.ID_Camion, RTRIM(LTRIM(c.CamionCode)) AS CODE, RTRIM(c.Immatriculation) AS IMMAT,
                   c.Marque, c.Modele, c.ANNEE, c.CAPACITE_TOTALE, c.CODE_TYPE_CAMION, RTRIM(t.LIBELLE) AS TYPE,
                   RTRIM(s.Libelle) AS STATUT, RTRIM(mt.METHODE) AS METHODE, c.CODE_TRANSPORTEUR,
                   RTRIM(CONCAT(p.Nom, ' ', p.Prenom)) AS TRANSPORTEUR,
                   (SELECT COUNT(*) FROM dbo.Camion x
                     WHERE RTRIM(LTRIM(x.CamionCode)) = RTRIM(LTRIM(c.CamionCode))) AS MEME_CODE
              FROM dbo.Camion c
              LEFT JOIN FACT.REF_TYPE_CAMION t ON t.ID_TYPE_CAMION = c.CODE_TYPE_CAMION
              LEFT JOIN dbo.RefStatutCamion s ON s.ID_RefStatutCamion = c.CodeStatutCamion
              LEFT JOIN FACT.METHODE_TARIFICATION mt ON mt.ID_METHODE_TARIFICATION = c.CODE_METHODE_TARIFICATION
              LEFT JOIN dbo.Personne p ON p.ID_Personne = c.CODE_TRANSPORTEUR
             WHERE (:recherche = '' OR c.CamionCode LIKE :motif OR c.Immatriculation LIKE :motif)
               AND (:idTransporteur = 0 OR c.CODE_TRANSPORTEUR = :idTransporteur)
             ORDER BY CODE, c.ID_Camion
            """;

    private static final String CLIENTS = """
            SELECT TOP (1000) cl.ID_CLIENT, RTRIM(LTRIM(cl.CODE_CLIENT)) AS CODE, RTRIM(cl.NOM_CLIENT) AS NOM,
                   cl.CODE_POINT_LIVRAISON, RTRIM(pl.LIBELLE) AS POINT, pl.DISTANCE, cl.TELEPHONE, cl.ADRESSE,
                   (SELECT COUNT(*) FROM FACT.CLIENT x
                     WHERE RTRIM(LTRIM(x.CODE_CLIENT)) = RTRIM(LTRIM(cl.CODE_CLIENT))) AS MEME_CODE
              FROM FACT.CLIENT cl
              LEFT JOIN FACT.REF_POINT_LIVRAISON pl ON pl.ID_POINT_LIVRAISON = cl.CODE_POINT_LIVRAISON
             WHERE (:recherche = '' OR cl.CODE_CLIENT LIKE :motif OR cl.NOM_CLIENT LIKE :motif)
             ORDER BY NOM, cl.ID_CLIENT
            """;

    private static final String POINTS = """
            SELECT pl.ID_POINT_LIVRAISON, RTRIM(pl.LIBELLE) AS LIBELLE, pl.DISTANCE, pl.CODE_ZONE,
                   (SELECT COUNT(*) FROM FACT.CLIENT c WHERE c.CODE_POINT_LIVRAISON = pl.ID_POINT_LIVRAISON) AS CLIENTS
              FROM FACT.REF_POINT_LIVRAISON pl
             ORDER BY pl.LIBELLE
            """;

    private static final String TYPES = """
            SELECT t.ID_TYPE_CAMION, RTRIM(t.LIBELLE) AS LIBELLE, t.MIN_YEAR, t.MAX_YEAR,
                   (SELECT COUNT(*) FROM dbo.Camion c WHERE c.CODE_TYPE_CAMION = t.ID_TYPE_CAMION) AS CAMIONS,
                   (SELECT COUNT(*) FROM FACT.TARIFICATION ta
                     WHERE ta.CODE_TYPE_CAMION = t.ID_TYPE_CAMION AND ta.IS_ACTIF = 1) AS TARIFS_ACTIFS
              FROM FACT.REF_TYPE_CAMION t
             ORDER BY t.ID_TYPE_CAMION
            """;

    private static final String TARIFS = """
            SELECT ta.ID_TARIFICATION, RTRIM(ta.LIBELLE) AS LIBELLE, ta.CODE_TYPE_CAMION, RTRIM(t.LIBELLE) AS TYPE,
                   ta.DATE_EFFECTIVE, ta.DATE_FIN, ISNULL(ta.IS_ACTIF, 0) AS ACTIF, ta.SIGLE_MONNAIE,
                   (SELECT COUNT(*) FROM FACT.DETAIL_TARIFICATION d WHERE d.CODE_TARIFICATION = ta.ID_TARIFICATION) AS PRIX
              FROM FACT.TARIFICATION ta
              LEFT JOIN FACT.REF_TYPE_CAMION t ON t.ID_TYPE_CAMION = ta.CODE_TYPE_CAMION
             WHERE (:actifs = 0 OR ta.IS_ACTIF = 1)
             ORDER BY ACTIF DESC, ta.CODE_TYPE_CAMION, ta.DATE_EFFECTIVE DESC
            """;

    private static final String PRIX = """
            SELECT d.CODE_POINT_LIVRAISON, RTRIM(pl.LIBELLE) AS POINT, pl.DISTANCE, d.PRIX_TRANSP_GALLON
              FROM FACT.DETAIL_TARIFICATION d
              LEFT JOIN FACT.REF_POINT_LIVRAISON pl ON pl.ID_POINT_LIVRAISON = d.CODE_POINT_LIVRAISON
             WHERE d.CODE_TARIFICATION = :id
             ORDER BY pl.DISTANCE, POINT
            """;

    private final JdbcClient jdbc;

    LectureReferentielSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Transporteur> transporteurs(String recherche) {
        String r = nettoyer(recherche);
        return jdbc.sql(TRANSPORTEURS).param("recherche", r).param("motif", motif(r))
                .query((rs, n) -> new Transporteur(rs.getLong("ID_Personne"), rs.getString("NOM"),
                        rs.getString("PRENOM"), rs.getString("TYPE"), rs.getInt("CAMIONS")))
                .list();
    }

    @Override
    public List<Camion> camions(String recherche, Long idTransporteur) {
        String r = nettoyer(recherche);
        return jdbc.sql(CAMIONS).param("recherche", r).param("motif", motif(r))
                .param("idTransporteur", idTransporteur == null ? 0L : idTransporteur)
                .query((rs, n) -> new Camion(rs.getInt("ID_Camion"), rs.getString("CODE"), rs.getString("IMMAT"),
                        rs.getString("Marque"), rs.getString("Modele"), entier(rs, "ANNEE"),
                        rs.getBigDecimal("CAPACITE_TOTALE"), entier(rs, "CODE_TYPE_CAMION"), rs.getString("TYPE"),
                        rs.getString("STATUT"), rs.getString("METHODE"), grandEntier(rs, "CODE_TRANSPORTEUR"),
                        rs.getString("TRANSPORTEUR"), rs.getInt("MEME_CODE")))
                .list();
    }

    @Override
    public List<Client> clients(String recherche) {
        String r = nettoyer(recherche);
        return jdbc.sql(CLIENTS).param("recherche", r).param("motif", motif(r))
                .query((rs, n) -> new Client(rs.getLong("ID_CLIENT"), rs.getString("CODE"), rs.getString("NOM"),
                        entier(rs, "CODE_POINT_LIVRAISON"), rs.getString("POINT"), rs.getBigDecimal("DISTANCE"),
                        rs.getString("TELEPHONE"), rs.getString("ADRESSE"), rs.getInt("MEME_CODE")))
                .list();
    }

    @Override
    public List<PointLivraison> pointsLivraison() {
        return jdbc.sql(POINTS)
                .query((rs, n) -> new PointLivraison(rs.getInt("ID_POINT_LIVRAISON"), rs.getString("LIBELLE"),
                        rs.getBigDecimal("DISTANCE"), rs.getInt("CODE_ZONE"), rs.getInt("CLIENTS")))
                .list();
    }

    @Override
    public List<TypeCamion> typesCamion() {
        return jdbc.sql(TYPES).query((rs, n) -> {
            int camions = rs.getInt("CAMIONS");
            int actifs = rs.getInt("TARIFS_ACTIFS");
            String alerte = null;
            if (actifs == 0 && camions > 0) {
                alerte = "Aucun tarif actif : les livraisons de ces camions sont rejetées à la facturation.";
            } else if (actifs > 1) {
                alerte = "Plusieurs tarifs actifs : la génération des factures est bloquée.";
            }
            return new TypeCamion(rs.getInt("ID_TYPE_CAMION"), rs.getString("LIBELLE"), entier(rs, "MIN_YEAR"),
                    entier(rs, "MAX_YEAR"), camions, actifs, alerte);
        }).list();
    }

    @Override
    public List<Tarif> tarifs(boolean actifsSeulement) {
        return jdbc.sql(TARIFS).param("actifs", actifsSeulement ? 1 : 0)
                .query((rs, n) -> new Tarif(rs.getInt("ID_TARIFICATION"), rs.getString("LIBELLE"),
                        entier(rs, "CODE_TYPE_CAMION"), rs.getString("TYPE"),
                        rs.getObject("DATE_EFFECTIVE", LocalDate.class), rs.getObject("DATE_FIN", LocalDate.class),
                        rs.getBoolean("ACTIF"), rs.getString("SIGLE_MONNAIE"), rs.getInt("PRIX")))
                .list();
    }

    @Override
    public List<PrixTarif> prixDuTarif(int idTarif) {
        return jdbc.sql(PRIX).param("id", idTarif)
                .query((rs, n) -> new PrixTarif(rs.getInt("CODE_POINT_LIVRAISON"), rs.getString("POINT"),
                        rs.getBigDecimal("DISTANCE"), rs.getBigDecimal("PRIX_TRANSP_GALLON")))
                .list();
    }

    private static String nettoyer(String recherche) {
        return recherche == null ? "" : recherche.trim();
    }

    /** Les caractères spéciaux de LIKE (%, _, [) sont cherchés tels quels. */
    private static String motif(String r) {
        return "%" + r.replace("[", "[[]").replace("%", "[%]").replace("_", "[_]") + "%";
    }

    private static Integer entier(ResultSet rs, String colonne) throws SQLException {
        int v = rs.getInt(colonne);
        return rs.wasNull() ? null : v;
    }

    private static Long grandEntier(ResultSet rs, String colonne) throws SQLException {
        long v = rs.getLong(colonne);
        return rs.wasNull() ? null : v;
    }
}