package ht.dinasa.fact.reporting.infrastructure;

import ht.dinasa.fact.reporting.application.LectureTableauxDeBord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
class LectureTableauxDeBordSql implements LectureTableauxDeBord {

    /** Un bon est facturé s'il porte une ligne de TRANSPORT (les services sont aussi rattachés à un bon). */
    private static final String BONS_DU_MOIS = """
            SELECT m.ID_Mouvement, m.DateLivraison,
                   (SELECT SUM(md.VolumeLivre) FROM dbo.MouvementDetail md WHERE md.CodeMouvement = m.ID_Mouvement) AS VOLUME,
                   CASE WHEN (m.CodeFacture IS NOT NULL AND m.CodeFacture <> 0)
                          OR EXISTS (SELECT 1 FROM FACT.DETAIL_FACTURE df
                                      WHERE df.CODE_MOUVEMENT = m.ID_Mouvement AND df.CODE_TYPE_SERVICE_A_DEDUIRE IS NULL)
                        THEN 1 ELSE 0 END AS FACTURE,
                   CASE WHEN LEFT(c.CamionCode, 2) IN (:prefixes) THEN 1 ELSE 0 END AS EXCLU
              FROM dbo.Mouvement m
              LEFT JOIN dbo.Camion c ON c.ID_Camion = m.CodeCamion
             WHERE m.CodeTypeMouvement = 2 AND m.estRenverse = 0 AND m.CodeClient IS NOT NULL
               AND m.DateLivraison >= :debut AND m.DateLivraison < :fin
            """;

    private static final String LIVRAISONS = """
            SELECT COUNT(*) AS BONS, ISNULL(SUM(x.VOLUME), 0) AS VOLUME,
                   ISNULL(SUM(CASE WHEN x.FACTURE = 1 THEN 1 ELSE 0 END), 0) AS FACTURES,
                   ISNULL(SUM(CASE WHEN x.FACTURE = 0 AND x.EXCLU = 0 THEN 1 ELSE 0 END), 0) AS A_FACTURER,
                   ISNULL(SUM(CASE WHEN x.FACTURE = 0 AND x.EXCLU = 1 THEN 1 ELSE 0 END), 0) AS EXCLUS
              FROM (""" + BONS_DU_MOIS + ") x";

    private static final String LIGNES_TRANSPORT = """
              FROM FACT.DETAIL_FACTURE df
              JOIN dbo.Mouvement m ON m.ID_Mouvement = df.CODE_MOUVEMENT
             WHERE df.CODE_TYPE_SERVICE_A_DEDUIRE IS NULL
               AND m.DateLivraison >= :debut AND m.DateLivraison < :fin
            """;

    private static final String COUT = "SELECT ISNULL(SUM(df.QTE_COMMANDEE), 0) AS QTE, ISNULL(SUM(df.MONTANT), 0) AS MONTANT"
            + LIGNES_TRANSPORT;

    private static final String COUTS_PAR_TRANSPORTEUR = """
            SELECT df.CODE_TRANSPORTEUR, RTRIM(CONCAT(p.Nom, ' ', p.Prenom)) AS TRANSPORTEUR,
                   COUNT(DISTINCT df.CODE_MOUVEMENT) AS BONS,
                   ISNULL(SUM(df.QTE_COMMANDEE), 0) AS QTE, ISNULL(SUM(df.MONTANT), 0) AS MONTANT
              FROM FACT.DETAIL_FACTURE df
              JOIN dbo.Mouvement m ON m.ID_Mouvement = df.CODE_MOUVEMENT
              LEFT JOIN dbo.Personne p ON p.ID_Personne = df.CODE_TRANSPORTEUR
             WHERE df.CODE_TYPE_SERVICE_A_DEDUIRE IS NULL
               AND m.DateLivraison >= :debut AND m.DateLivraison < :fin
             GROUP BY df.CODE_TRANSPORTEUR, p.Nom, p.Prenom
             ORDER BY MONTANT DESC
            """;

    private static final String LIVRAISONS_PAR_MOIS = """
            SELECT MONTH(x.DateLivraison) AS MOIS, COUNT(*) AS BONS, ISNULL(SUM(x.VOLUME), 0) AS VOLUME
              FROM (""" + BONS_DU_MOIS + """
            ) x
             GROUP BY MONTH(x.DateLivraison)
            """;

    private static final String COUT_PAR_MOIS = """
            SELECT MONTH(m.DateLivraison) AS MOIS,
                   ISNULL(SUM(df.QTE_COMMANDEE), 0) AS QTE, ISNULL(SUM(df.MONTANT), 0) AS MONTANT
            """ + LIGNES_TRANSPORT + " GROUP BY MONTH(m.DateLivraison)";

    private static final String SOMME_LIGNES = """
              FROM FACT.FACTURE f
             OUTER APPLY (SELECT SUM(df.MONTANT) AS TOTAL FROM FACT.DETAIL_FACTURE df WHERE df.CODE_FACTURE = f.ID_FACTURE) l
            """;

    private static final String ATTENTE_POSTING = """
            SELECT ISNULL(f.SIGLE_MONNAIE, 'USD') AS DEVISE, COUNT(*) AS NB, ISNULL(SUM(f.MONTANT), 0) AS MONTANT,
                   SUM(CASE WHEN ABS(f.MONTANT - ISNULL(l.TOTAL, 0)) >= 0.01 THEN 1 ELSE 0 END) AS INCOHERENTES,
                   MIN(COALESCE(f.DATE_GENERER_FACTURE, CAST(f.DATECREATED AS date))) AS PLUS_ANCIENNE
            """ + SOMME_LIGNES + """
             WHERE f.POSTE_YN = 0
             GROUP BY ISNULL(f.SIGLE_MONNAIE, 'USD')
            """;

    private static final String FACTURES_EN_ATTENTE = """
            SELECT TOP (:limite) f.ID_FACTURE, f.NO_FACTURE, f.CODE_TRANSPORTEUR,
                   RTRIM(CONCAT(p.Nom, ' ', p.Prenom)) AS TRANSPORTEUR,
                   COALESCE(f.DATE_GENERER_FACTURE, CAST(f.DATECREATED AS date)) AS GENEREE,
                   DATEDIFF(day, COALESCE(f.DATE_GENERER_FACTURE, CAST(f.DATECREATED AS date)), :aujourdhui) AS JOURS,
                   ISNULL(f.SIGLE_MONNAIE, 'USD') AS DEVISE, f.MONTANT, ISNULL(l.TOTAL, 0) AS LIGNES
            """ + SOMME_LIGNES + """
              LEFT JOIN dbo.Personne p ON p.ID_Personne = f.CODE_TRANSPORTEUR
             WHERE f.POSTE_YN = 0
             ORDER BY GENEREE, f.ID_FACTURE
            """;

    private static final String ACCPAC_LIGNES = """
            SELECT COUNT(*) AS LIGNES,
                   ISNULL(SUM(x.OT), 0) AS OT,
                   ISNULL(SUM(x.SANS_CAMION), 0) AS SANS_CAMION,
                   ISNULL(SUM(x.CAMION_INCONNU), 0) AS CAMION_INCONNU
              FROM (SELECT CASE WHEN l.CodeCamion LIKE 'OT%' THEN 1 ELSE 0 END AS OT,
                           CASE WHEN l.CodeCamion IS NULL OR LTRIM(RTRIM(l.CodeCamion)) = '' THEN 1 ELSE 0 END AS SANS_CAMION,
                           CASE WHEN LTRIM(RTRIM(ISNULL(l.CodeCamion, ''))) <> '' AND l.CodeCamion NOT LIKE 'OT%'
                                     AND NOT EXISTS (SELECT 1 FROM dbo.Camion c
                                                      WHERE RTRIM(LTRIM(c.CamionCode)) = RTRIM(LTRIM(l.CodeCamion)))
                                THEN 1 ELSE 0 END AS CAMION_INCONNU
                      FROM dbo.Livraison_Acpacc l
                     WHERE l.IsRetreive = 0 AND l.DateMouvement >= :debut AND l.DateMouvement < :fin) x
            """;

    private static final String CODES_CAMION_EN_DOUBLE = """
            SELECT COUNT(*) AS DOUBLES, ISNULL(SUM(d.DIFF), 0) AS TYPES_DIFF
              FROM (SELECT CASE WHEN COUNT(DISTINCT ISNULL(c.CODE_TYPE_CAMION, 255)) > 1 THEN 1 ELSE 0 END AS DIFF
                      FROM dbo.Camion c
                     WHERE LTRIM(RTRIM(c.CamionCode)) <> ''
                     GROUP BY RTRIM(LTRIM(c.CamionCode))
                    HAVING COUNT(*) > 1) d
            """;

    private final JdbcClient jdbc;
    private final List<String> prefixesExclus;

    LectureTableauxDeBordSql(JdbcClient jdbc,
                             @Value("${dinasa.facturation.prefixes-camions-exclus}") List<String> prefixesExclus) {
        this.jdbc = jdbc;
        this.prefixesExclus = prefixesExclus;
    }

    @Override
    public Livraisons livraisons(LocalDate debut, LocalDate fin) {
        return jdbc.sql(LIVRAISONS).param("prefixes", prefixesExclus).param("debut", debut).param("fin", fin)
                .query((rs, n) -> new Livraisons(rs.getInt("BONS"), rs.getBigDecimal("VOLUME"),
                        rs.getInt("FACTURES"), rs.getInt("A_FACTURER"), rs.getInt("EXCLUS")))
                .single();
    }

    @Override
    public CoutTransport cout(LocalDate debut, LocalDate fin) {
        return jdbc.sql(COUT).param("debut", debut).param("fin", fin)
                .query((rs, n) -> new CoutTransport(rs.getBigDecimal("QTE"), rs.getBigDecimal("MONTANT"),
                        prixMoyen(rs.getBigDecimal("MONTANT"), rs.getBigDecimal("QTE"))))
                .single();
    }

    @Override
    public AttentePosting attentePosting() {
        List<MontantDevise> parDevise = new ArrayList<>();
        int[] incoherentes = {0};
        LocalDate[] plusAncienne = {null};
        jdbc.sql(ATTENTE_POSTING).query((rs, n) -> {
            parDevise.add(new MontantDevise(rs.getString("DEVISE"), rs.getInt("NB"), rs.getBigDecimal("MONTANT")));
            incoherentes[0] += rs.getInt("INCOHERENTES");
            LocalDate d = rs.getObject("PLUS_ANCIENNE", LocalDate.class);
            if (d != null && (plusAncienne[0] == null || d.isBefore(plusAncienne[0]))) {
                plusAncienne[0] = d;
            }
            return n;
        }).list();
        int total = parDevise.stream().mapToInt(MontantDevise::factures).sum();
        return new AttentePosting(total, incoherentes[0], plusAncienne[0], parDevise);
    }

    @Override
    public Accpac accpac(LocalDate debut, LocalDate fin) {
        int[] doubles = jdbc.sql(CODES_CAMION_EN_DOUBLE)
                .query((rs, n) -> new int[]{rs.getInt("DOUBLES"), rs.getInt("TYPES_DIFF")}).single();
        return jdbc.sql(ACCPAC_LIGNES).param("debut", debut).param("fin", fin)
                .query((rs, n) -> new Accpac(rs.getInt("LIGNES"), rs.getInt("OT"), rs.getInt("SANS_CAMION"),
                        rs.getInt("CAMION_INCONNU"), doubles[0], doubles[1]))
                .single();
    }

    @Override
    public List<VolumeMois> volumesParMois(int annee) {
        LocalDate debut = LocalDate.of(annee, 1, 1);
        LocalDate fin = debut.plusYears(1);
        Map<Integer, Object[]> livraisons = new HashMap<>();
        jdbc.sql(LIVRAISONS_PAR_MOIS).param("prefixes", prefixesExclus).param("debut", debut).param("fin", fin)
                .query((rs, n) -> livraisons.put(rs.getInt("MOIS"),
                        new Object[]{rs.getInt("BONS"), rs.getBigDecimal("VOLUME")}))
                .list();
        Map<Integer, BigDecimal[]> couts = new HashMap<>();
        jdbc.sql(COUT_PAR_MOIS).param("debut", debut).param("fin", fin)
                .query((rs, n) -> couts.put(rs.getInt("MOIS"),
                        new BigDecimal[]{rs.getBigDecimal("QTE"), rs.getBigDecimal("MONTANT")}))
                .list();

        List<VolumeMois> resultat = new ArrayList<>();
        for (int mois = 1; mois <= 12; mois++) {
            Object[] l = livraisons.getOrDefault(mois, new Object[]{0, BigDecimal.ZERO});
            BigDecimal[] c = couts.getOrDefault(mois, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            resultat.add(new VolumeMois(mois, (Integer) l[0], (BigDecimal) l[1], c[0], c[1]));
        }
        return resultat;
    }

    @Override
    public List<CoutTransporteur> coutsParTransporteur(LocalDate debut, LocalDate fin) {
        return jdbc.sql(COUTS_PAR_TRANSPORTEUR).param("debut", debut).param("fin", fin)
                .query((rs, n) -> new CoutTransporteur(rs.getLong("CODE_TRANSPORTEUR"), rs.getString("TRANSPORTEUR"),
                        rs.getInt("BONS"), rs.getBigDecimal("QTE"), rs.getBigDecimal("MONTANT"),
                        prixMoyen(rs.getBigDecimal("MONTANT"), rs.getBigDecimal("QTE"))))
                .list();
    }

    @Override
    public List<FactureEnAttente> facturesEnAttente(int limite, LocalDate aujourdhui) {
        return jdbc.sql(FACTURES_EN_ATTENTE).param("limite", limite).param("aujourdhui", aujourdhui)
                .query((rs, n) -> {
                    BigDecimal montant = rs.getBigDecimal("MONTANT");
                    BigDecimal lignes = rs.getBigDecimal("LIGNES");
                    return new FactureEnAttente(rs.getLong("ID_FACTURE"), rs.getString("NO_FACTURE"),
                            rs.getLong("CODE_TRANSPORTEUR"), rs.getString("TRANSPORTEUR"),
                            rs.getObject("GENEREE", LocalDate.class), rs.getInt("JOURS"), rs.getString("DEVISE"),
                            montant, lignes, montant.subtract(lignes).abs().compareTo(new BigDecimal("0.01")) >= 0);
                })
                .list();
    }

    private static BigDecimal prixMoyen(BigDecimal montant, BigDecimal quantite) {
        return quantite == null || quantite.signum() == 0 ? null : montant.divide(quantite, 4, RoundingMode.HALF_UP);
    }
}