package ht.dinasa.fact.facturation.infrastructure;

import ht.dinasa.fact.facturation.application.LectureDocuments;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
class LectureDocumentsSql implements LectureDocuments {

    private static final String COLONNES = """
             f.ID_FACTURE, f.NO_FACTURE, COALESCE(f.DATE_GENERER_FACTURE, CAST(f.DATECREATED AS date)) AS GENEREE,
                   ISNULL(f.SIGLE_MONNAIE, 'USD') AS DEVISE, f.POSTE_YN, f.CODE_TRANSPORTEUR,
                   RTRIM(CONCAT(p.Nom, ' ', p.Prenom)) AS TRANSPORTEUR, f.QTE_COMMANDEE, f.MONTANT
              FROM FACT.FACTURE f
              LEFT JOIN dbo.Personne p ON p.ID_Personne = f.CODE_TRANSPORTEUR
            """;

    private static final String ENTETE = "SELECT" + COLONNES + " WHERE f.ID_FACTURE = :id";

    private static final String LISTE = "SELECT TOP (:limite)" + COLONNES + """
             WHERE COALESCE(f.DATE_GENERER_FACTURE, CAST(f.DATECREATED AS date)) BETWEEN :du AND :au
               AND (:idTransporteur = 0 OR f.CODE_TRANSPORTEUR = :idTransporteur)
               AND (:postee = -1 OR f.POSTE_YN = :postee)
             ORDER BY GENEREE, f.ID_FACTURE
            """;

    /** Transport d'abord, puis services ; client = client FACTURÉ (règle du chargement partagé). */
    private static final String LIGNES = """
            SELECT df.ID_DETAIL_FACTURE,
                   CASE WHEN df.CODE_TYPE_SERVICE_A_DEDUIRE IS NULL THEN 0 ELSE 1 END AS SERVICE,
                   RTRIM(m.NoCommande) AS BON, CAST(m.DateLivraison AS date) AS DATE_LIVRAISON,
                   RTRIM(df.CAMION_CODE) AS CAMION, RTRIM(cl.NOM_CLIENT) AS CLIENT, RTRIM(pl.LIBELLE) AS POINT,
                   df.QTE_COMMANDEE, COALESCE(df.PRIX_TRANSP_GALON, df.PRIX_SERVICE) AS PRIX, df.MONTANT,
                   RTRIM(df.DESCRIPTION_LIGNE) AS DESCRIPTION
              FROM FACT.DETAIL_FACTURE df
              LEFT JOIN dbo.Mouvement m ON m.ID_Mouvement = df.CODE_MOUVEMENT
              LEFT JOIN FACT.CLIENT cl ON cl.ID_CLIENT = COALESCE(NULLIF(m.CodeClientFact, 0), m.CodeClient)
              LEFT JOIN FACT.REF_POINT_LIVRAISON pl ON pl.ID_POINT_LIVRAISON = cl.CODE_POINT_LIVRAISON
             WHERE df.CODE_FACTURE = :id
             ORDER BY SERVICE, m.DateLivraison, df.ID_DETAIL_FACTURE
            """;

    private final JdbcClient jdbc;

    LectureDocumentsSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<EnTete> entete(long idFacture) {
        return jdbc.sql(ENTETE).param("id", idFacture).query(this::versEnTete).optional();
    }

    @Override
    public List<Ligne> lignes(long idFacture) {
        return jdbc.sql(LIGNES).param("id", idFacture)
                .query((rs, n) -> new Ligne(rs.getLong("ID_DETAIL_FACTURE"), rs.getInt("SERVICE") == 1,
                        rs.getString("BON"), rs.getObject("DATE_LIVRAISON", LocalDate.class), rs.getString("CAMION"),
                        rs.getString("CLIENT"), rs.getString("POINT"), rs.getBigDecimal("QTE_COMMANDEE"),
                        rs.getBigDecimal("PRIX"), rs.getBigDecimal("MONTANT"), rs.getString("DESCRIPTION")))
                .list();
    }

    @Override
    public List<EnTete> factures(LocalDate du, LocalDate au, Long idTransporteur, Boolean postee, int limite) {
        return jdbc.sql(LISTE).param("limite", limite).param("du", du).param("au", au)
                .param("idTransporteur", idTransporteur == null ? 0L : idTransporteur)
                .param("postee", postee == null ? -1 : (postee ? 1 : 0))
                .query(this::versEnTete).list();
    }

    @Override
    public Optional<String> transporteur(long idTransporteur) {
        return jdbc.sql("SELECT RTRIM(CONCAT(Nom, ' ', Prenom)) FROM dbo.Personne WHERE ID_Personne = :id")
                .param("id", idTransporteur).query(String.class).optional();
    }

    private EnTete versEnTete(ResultSet rs, int n) throws SQLException {
        return new EnTete(rs.getLong("ID_FACTURE"), rs.getString("NO_FACTURE"),
                rs.getObject("GENEREE", LocalDate.class), rs.getString("DEVISE"), rs.getBoolean("POSTE_YN"),
                rs.getLong("CODE_TRANSPORTEUR"), rs.getString("TRANSPORTEUR"), rs.getBigDecimal("QTE_COMMANDEE"),
                rs.getBigDecimal("MONTANT"));
    }
}