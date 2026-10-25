package ht.dinasa.fact.securite;

import ht.dinasa.fact.securite.application.AdministrationSecurite;
import ht.dinasa.fact.securite.application.Authentification;
import ht.dinasa.fact.securite.application.ConnexionRefusee;
import ht.dinasa.fact.securite.application.ConnexionRefusee.Raison;
import ht.dinasa.fact.securite.application.DepotPermissions;
import ht.dinasa.fact.securite.domain.Permission;
import ht.dinasa.fact.securite.infrastructure.ConvertisseurJeton;
import ht.dinasa.fact.shared.erreurs.ErreurMetier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tout est annulé à la fin (@Transactional). */
@SpringBootTest
@Transactional
class SecuriteTests {

    private static final String NOUVEAU = "Nouveau#2026a";
    private static final String IP = "127.0.0.1";

    @Autowired Authentification authentification;
    @Autowired AdministrationSecurite administration;
    @Autowired DepotPermissions permissions;
    @Autowired ConvertisseurJeton convertisseur;
    @Autowired JwtDecoder decodeur;
    @Autowired JdbcClient jdbc;

    /** Un utilisateur actif dont le login n'est pas en double. */
    private long utilisateurActif() {
        return jdbc.sql("""
                SELECT TOP 1 u.ID FROM dbo.tbl_S_User u
                 WHERE u.ActifYN = 1
                   AND (SELECT COUNT(*) FROM dbo.tbl_S_User x
                         WHERE RTRIM(LTRIM(x.USER_ID)) = RTRIM(LTRIM(u.USER_ID))) = 1
                 ORDER BY u.ID""").query(Long.class).single();
    }

    private Raison raison(Throwable e) {
        return ((ConnexionRefusee) e).raison();
    }

    @Test
    void premiere_connexion_changement_puis_jeton_avec_les_droits_du_groupe() {
        long id = utilisateurActif();
        long groupe = jdbc.sql("SELECT Group_ID FROM dbo.tbl_S_User WHERE ID = :id").param("id", id)
                .query(Long.class).single();
        var droits = EnumSet.of(Permission.FACTURES_CONSULTER);
        droits.addAll(permissions.duGroupe(groupe));
        administration.modifierPermissions(groupe, droits, "Test");
        var temp = administration.motDePasseTemporaire(id);

        var r1 = authentification.connecter(temp.login(), temp.motDePasse(), IP);
        assertThat(r1.doitChangerMotDePasse()).isTrue();
        assertThat(r1.jeton()).isNull();

        var r2 = authentification.changerMotDePasse(temp.login(), temp.motDePasse(), NOUVEAU, IP);
        assertThat(r2.jeton()).isNotBlank();

        var auth = convertisseur.convert(decodeur.decode(r2.jeton()));
        assertThat(auth.getName()).isEqualTo(temp.login());
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).contains("FACTURES_CONSULTER");

        String stocke = jdbc.sql("SELECT MOT_DE_PASSE FROM APP.COMPTE_SECURITE WHERE ID_UTILISATEUR = :id")
                .param("id", id).query(String.class).single();
        assertThat(stocke).startsWith("{bcrypt}").doesNotContain(NOUVEAU);
    }

    @Test
    void cinq_echecs_bloquent_le_compte_meme_avec_le_bon_mot_de_passe() {
        var temp = administration.motDePasseTemporaire(utilisateurActif());
        for (int i = 1; i <= 4; i++) {
            assertThatThrownBy(() -> authentification.connecter(temp.login(), "Mauvais#123", IP))
                    .satisfies(e -> assertThat(raison(e)).isEqualTo(Raison.IDENTIFIANTS_INCORRECTS));
        }
        assertThatThrownBy(() -> authentification.connecter(temp.login(), "Mauvais#123", IP))
                .satisfies(e -> assertThat(raison(e)).isEqualTo(Raison.COMPTE_BLOQUE));
        assertThatThrownBy(() -> authentification.connecter(temp.login(), temp.motDePasse(), IP))
                .satisfies(e -> assertThat(raison(e)).isEqualTo(Raison.COMPTE_BLOQUE));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM APP.JOURNAL_CONNEXION WHERE LOGIN = :l AND RESULTAT LIKE 'REFUS_%'")
                .param("l", temp.login()).query(Integer.class).single()).isEqualTo(6);
    }

    @Test
    void un_login_inconnu_est_refuse() {
        assertThatThrownBy(() -> authentification.connecter("zz-inconnu-zz", "Peu#importe1", IP))
                .satisfies(e -> assertThat(raison(e)).isEqualTo(Raison.IDENTIFIANTS_INCORRECTS));
    }

    @Test
    void un_mot_de_passe_faible_est_refuse() {
        var temp = administration.motDePasseTemporaire(utilisateurActif());
        assertThatThrownBy(() -> authentification.changerMotDePasse(temp.login(), temp.motDePasse(), "faible", IP))
                .isInstanceOf(ErreurMetier.class)
                .extracting(e -> ((ErreurMetier) e).getCode()).isEqualTo("MOT_DE_PASSE_FAIBLE");
    }

    @Test
    void une_deconnexion_forcee_invalide_le_jeton() {
        long id = utilisateurActif();
        var temp = administration.motDePasseTemporaire(id);
        String jeton = authentification.changerMotDePasse(temp.login(), temp.motDePasse(), NOUVEAU, IP).jeton();

        administration.deconnecter(id);

        assertThatThrownBy(() -> convertisseur.convert(decodeur.decode(jeton)))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void on_ne_peut_pas_retirer_le_dernier_administrateur() {
        long id = utilisateurActif();
        long groupe = jdbc.sql("SELECT Group_ID FROM dbo.tbl_S_User WHERE ID = :id").param("id", id)
                .query(Long.class).single();
        jdbc.sql("DELETE FROM APP.GROUPE_PERMISSION WHERE PERMISSION = 'SECURITE_ADMINISTRER'").update();
        administration.modifierPermissions(groupe, EnumSet.of(Permission.SECURITE_ADMINISTRER), "Test");

        assertThatThrownBy(() -> administration.modifierPermissions(groupe, EnumSet.noneOf(Permission.class), "Test"))
                .isInstanceOf(ErreurMetier.class)
                .extracting(e -> ((ErreurMetier) e).getCode()).isEqualTo("DERNIER_ADMINISTRATEUR");
    }
}