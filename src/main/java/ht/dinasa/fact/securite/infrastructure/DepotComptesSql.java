package ht.dinasa.fact.securite.infrastructure;

import ht.dinasa.fact.securite.application.DepotComptes;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
class DepotComptesSql implements DepotComptes {

    private static final String COMPTE = """
            SELECT u.ID, RTRIM(LTRIM(u.USER_ID)) AS LOGIN, u.Nom, u.Prenom, u.Group_ID, u.ActifYN,
                   c.MOT_DE_PASSE, ISNULL(c.DOIT_CHANGER, 1) AS DOIT_CHANGER, ISNULL(c.ECHECS, 0) AS ECHECS,
                   c.BLOQUE_JUSQU_A, ISNULL(c.VERSION_JETON, 0) AS VERSION_JETON
              FROM dbo.tbl_S_User u
              LEFT JOIN APP.COMPTE_SECURITE c ON c.ID_UTILISATEUR = u.ID
            """;

    private static final String UTILISATEURS = """
            SELECT u.ID, RTRIM(LTRIM(u.USER_ID)) AS LOGIN, u.Nom, u.Prenom, u.Group_ID, RTRIM(g.GroupDesc) AS GROUPE,
                   u.ActifYN, CASE WHEN c.ID_UTILISATEUR IS NULL THEN 0 ELSE 1 END AS COMPTE_ACTIVE,
                   ISNULL(c.DOIT_CHANGER, 0) AS DOIT_CHANGER, c.BLOQUE_JUSQU_A, c.DERNIERE_CONNEXION
              FROM dbo.tbl_S_User u
              LEFT JOIN dbo.tbl_S_Group g ON g.Group_ID = u.Group_ID
              LEFT JOIN APP.COMPTE_SECURITE c ON c.ID_UTILISATEUR = u.ID
             ORDER BY u.ActifYN DESC, u.Nom, u.Prenom
            """;

    private static final String ENREGISTRER_MOT_DE_PASSE = """
            MERGE APP.COMPTE_SECURITE WITH (HOLDLOCK) AS c
            USING (SELECT :id AS ID) AS s ON c.ID_UTILISATEUR = s.ID
            WHEN MATCHED THEN UPDATE SET
                 MOT_DE_PASSE = :mdp, DOIT_CHANGER = :doit, ECHECS = 0, BLOQUE_JUSQU_A = NULL,
                 VERSION_JETON = c.VERSION_JETON + 1, MODIFIE_LE = :le, MODIFIE_PAR = :par
            WHEN NOT MATCHED THEN INSERT
                 (ID_UTILISATEUR, MOT_DE_PASSE, DOIT_CHANGER, ECHECS, VERSION_JETON, MODIFIE_LE, MODIFIE_PAR)
                 VALUES (s.ID, :mdp, :doit, 0, 1, :le, :par);
            """;

    private static final String ECHEC = """
            UPDATE APP.COMPTE_SECURITE
               SET ECHECS = :echecs, BLOQUE_JUSQU_A = CASE WHEN :bloquer = 1 THEN :jusqua ELSE NULL END
             WHERE ID_UTILISATEUR = :id
            """;

    private static final String SUCCES = """
            UPDATE APP.COMPTE_SECURITE
               SET ECHECS = 0, BLOQUE_JUSQU_A = NULL, DERNIERE_CONNEXION = :le, DERNIERE_IP = :ip
             WHERE ID_UTILISATEUR = :id
            """;

    private static final String REVOQUER = """
            UPDATE APP.COMPTE_SECURITE SET VERSION_JETON = VERSION_JETON + 1 WHERE ID_UTILISATEUR = :id
            """;

    private static final String JOURNAL = """
            INSERT INTO APP.JOURNAL_CONNEXION (DATE_CONNEXION, LOGIN, RESULTAT, IP) VALUES (:le, :login, :resultat, :ip)
            """;

    private final JdbcClient jdbc;

    DepotComptesSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Compte> parLogin(String login) {
        List<Compte> trouves = jdbc.sql(COMPTE + " WHERE RTRIM(LTRIM(u.USER_ID)) = :login")
                .param("login", login).query(this::versCompte).list();
        return trouves.size() == 1 ? Optional.of(trouves.getFirst()) : Optional.empty();
    }

    @Override
    public Optional<Compte> parId(long id) {
        return jdbc.sql(COMPTE + " WHERE u.ID = :id").param("id", id).query(this::versCompte).optional();
    }

    @Override
    public List<UtilisateurVue> utilisateurs() {
        return jdbc.sql(UTILISATEURS).query((rs, n) -> new UtilisateurVue(rs.getLong("ID"), rs.getString("LOGIN"),
                rs.getString("Nom"), rs.getString("Prenom"), rs.getLong("Group_ID"), rs.getString("GROUPE"),
                rs.getBoolean("ActifYN"), rs.getInt("COMPTE_ACTIVE") == 1, rs.getBoolean("DOIT_CHANGER"),
                rs.getObject("BLOQUE_JUSQU_A", LocalDateTime.class),
                rs.getObject("DERNIERE_CONNEXION", LocalDateTime.class))).list();
    }

    @Override
    public void enregistrerMotDePasse(long id, String empreinte, boolean doitChanger, LocalDateTime le, String par) {
        jdbc.sql(ENREGISTRER_MOT_DE_PASSE).param("id", id).param("mdp", empreinte).param("doit", doitChanger)
                .param("le", le).param("par", par).update();
    }

    @Override
    public void enregistrerEchec(long id, int echecs, boolean bloquer, LocalDateTime bloqueJusqua) {
        jdbc.sql(ECHEC).param("id", id).param("echecs", echecs).param("bloquer", bloquer ? 1 : 0)
                .param("jusqua", bloqueJusqua).update();
    }

    @Override
    public void enregistrerSucces(long id, LocalDateTime le, String ip) {
        jdbc.sql(SUCCES).param("id", id).param("le", le).param("ip", ip).update();
    }

    @Override
    public int revoquer(long id) {
        return jdbc.sql(REVOQUER).param("id", id).update();
    }

    @Override
    public void journaliserConnexion(String login, String resultat, String ip, LocalDateTime le) {
        jdbc.sql(JOURNAL).param("le", le).param("login", login).param("resultat", resultat).param("ip", ip).update();
    }

    private Compte versCompte(ResultSet rs, int n) throws SQLException {
        return new Compte(rs.getLong("ID"), rs.getString("LOGIN"), rs.getString("Nom"), rs.getString("Prenom"),
                rs.getLong("Group_ID"), rs.getBoolean("ActifYN"), rs.getString("MOT_DE_PASSE"),
                rs.getBoolean("DOIT_CHANGER"), rs.getInt("ECHECS"),
                rs.getObject("BLOQUE_JUSQU_A", LocalDateTime.class), rs.getInt("VERSION_JETON"));
    }
}