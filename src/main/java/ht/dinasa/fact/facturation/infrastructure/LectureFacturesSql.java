package ht.dinasa.fact.facturation.infrastructure;

import ht.dinasa.fact.facturation.application.CriteresFactures;
import ht.dinasa.fact.facturation.application.FicheFacture;
import ht.dinasa.fact.facturation.application.FicheFacture.Ligne;
import ht.dinasa.fact.facturation.application.FicheFacture.TypeLigne;
import ht.dinasa.fact.facturation.application.LectureFactures;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
class LectureFacturesSql implements LectureFactures {

    private static final String SELECT_EN_TETE = """
            SELECT f.ID_FACTURE, f.NO_FACTURE, f.CODE_TRANSPORTEUR,
                   LTRIM(RTRIM(ISNULL(p.Nom, '') + ' ' + ISNULL(p.Prenom, ''))) AS NOM_TRANSPORTEUR,
                   f.DATE_GENERER_FACTURE, f.SIGLE_MONNAIE, f.MONTANT,
                   ISNULL(f.MONTANT_TRANSACTION, 0) + ISNULL(f.MONTANT_FRAIS_FIXE, 0) AS MONTANT_TRANSPORT,
                   ISNULL(f.MONTANT_SERVICE, 0) AS MONTANT_SERVICE,
                   f.POSTE_YN,
                   (SELECT COUNT(*) FROM FACT.DETAIL_FACTURE d WHERE d.CODE_FACTURE = f.ID_FACTURE) AS NB_LIGNES
              FROM FACT.FACTURE f
              LEFT JOIN dbo.Personne p ON p.ID_Personne = f.CODE_TRANSPORTEUR
            """;

    private static final String SELECT_LIGNES = """
            SELECT d.ID_DETAIL_FACTURE, d.CODE_MOUVEMENT, m.NoCommande, d.CAMION_CODE, m.DateLivraison,
                   c.NOM_CLIENT, d.QTE_COMMANDEE, d.PRIX_TRANSP_GALON, d.MONTANT,
                   d.CODE_TYPE_SERVICE_A_DEDUIRE, d.DESCRIPTION_LIGNE, d.DATEMODIF
              FROM FACT.DETAIL_FACTURE d
              LEFT JOIN dbo.Mouvement m ON m.ID_Mouvement = d.CODE_MOUVEMENT
              LEFT JOIN FACT.CLIENT c   ON c.ID_CLIENT = m.CodeClient
             WHERE d.CODE_FACTURE = :id
             ORDER BY CASE WHEN d.CODE_TYPE_SERVICE_A_DEDUIRE IS NULL THEN 0 ELSE 1 END,
                      m.DateLivraison, d.ID_DETAIL_FACTURE
            """;

    private final JdbcClient jdbc;

    LectureFacturesSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<FicheFacture> rechercher(CriteresFactures c) {
        StringBuilder sql = new StringBuilder(SELECT_EN_TETE).append(" WHERE 1 = 1");
        Map<String, Object> params = new HashMap<>();
        if (c.du() != null) {
            sql.append(" AND f.DATE_GENERER_FACTURE >= :du");
            params.put("du", c.du());
        }
        if (c.au() != null) {
            sql.append(" AND f.DATE_GENERER_FACTURE <= :au");
            params.put("au", c.au());
        }
        if (c.codeTransporteur() != null) {
            sql.append(" AND f.CODE_TRANSPORTEUR = :transporteur");
            params.put("transporteur", c.codeTransporteur());
        }
        if (c.postee() != null) {
            sql.append(" AND f.POSTE_YN = :postee");
            params.put("postee", c.postee());
        }
        sql.append(" ORDER BY f.DATE_GENERER_FACTURE DESC, f.ID_FACTURE DESC")
                .append(" OFFSET :decalage ROWS FETCH NEXT :taille ROWS ONLY");
        params.put("decalage", c.page() * c.taille());
        params.put("taille", c.taille());

        return jdbc.sql(sql.toString()).params(params).query(this::versEnTete).list();
    }

    @Override
    public Optional<FicheFacture> parId(long id) {
        return jdbc.sql(SELECT_EN_TETE + " WHERE f.ID_FACTURE = :id")
                .param("id", id)
                .query(this::versEnTete)
                .optional()
                .map(entete -> entete.avecLignes(
                        jdbc.sql(SELECT_LIGNES).param("id", id).query(this::versLigne).list()));
    }

    private FicheFacture versEnTete(ResultSet rs, int n) throws SQLException {
        return new FicheFacture(
                rs.getLong("ID_FACTURE"),
                rs.getString("NO_FACTURE"),
                rs.getLong("CODE_TRANSPORTEUR"),
                rs.getString("NOM_TRANSPORTEUR"),
                rs.getObject("DATE_GENERER_FACTURE", LocalDate.class),
                rs.getString("SIGLE_MONNAIE"),
                rs.getBigDecimal("MONTANT"),
                rs.getBigDecimal("MONTANT_TRANSPORT"),
                rs.getBigDecimal("MONTANT_SERVICE"),
                rs.getBoolean("POSTE_YN"),
                rs.getInt("NB_LIGNES"),
                List.of());
    }

    private Ligne versLigne(ResultSet rs, int n) throws SQLException {
        long mouvement = rs.getLong("CODE_MOUVEMENT");
        Long idMouvement = rs.wasNull() ? null : mouvement;
        rs.getLong("CODE_TYPE_SERVICE_A_DEDUIRE");
        TypeLigne type = rs.wasNull() ? TypeLigne.TRANSPORT : TypeLigne.SERVICE;
        return new Ligne(
                rs.getLong("ID_DETAIL_FACTURE"),
                type,
                idMouvement,
                rs.getString("NoCommande"),
                rs.getString("CAMION_CODE"),
                rs.getObject("DateLivraison", LocalDate.class),
                rs.getString("NOM_CLIENT"),
                rs.getBigDecimal("QTE_COMMANDEE"),
                rs.getBigDecimal("PRIX_TRANSP_GALON"),
                rs.getBigDecimal("MONTANT"),
                rs.getString("DESCRIPTION_LIGNE"),
                rs.getTimestamp("DATEMODIF") != null);
    }
}