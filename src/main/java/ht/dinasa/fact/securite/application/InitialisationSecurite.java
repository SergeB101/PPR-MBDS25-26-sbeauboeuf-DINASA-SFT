package ht.dinasa.fact.securite.application;

import ht.dinasa.fact.securite.domain.Permission;
import ht.dinasa.fact.securite.domain.PolitiqueMotDePasse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;

@Component
class InitialisationSecurite implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(InitialisationSecurite.class);

    private final ProprietesSecurite proprietes;
    private final DepotComptes comptes;
    private final DepotPermissions permissions;
    private final PasswordEncoder encodeur;
    private final Clock horloge;

    InitialisationSecurite(ProprietesSecurite proprietes, DepotComptes comptes, DepotPermissions permissions,
                           PasswordEncoder encodeur, Clock horloge) {
        this.proprietes = proprietes;
        this.comptes = comptes;
        this.permissions = permissions;
        this.encodeur = encodeur;
        this.horloge = horloge;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String login = proprietes.initialisation().login();
        if (login == null || login.isBlank() || !permissions.aucune()) {
            return;
        }
        var compte = comptes.parLogin(login.trim()).orElse(null);
        if (compte == null) {
            LOG.warn("Initialisation sécurité : utilisateur « {} » introuvable (ou en double) dans tbl_S_User.", login);
            return;
        }
        LocalDateTime le = LocalDateTime.now(horloge).truncatedTo(ChronoUnit.SECONDS);
        permissions.remplacer(compte.idGroupe(), EnumSet.allOf(Permission.class), le, "initialisation");
        if (compte.motDePasse() == null) {
            PolitiqueMotDePasse.valider(proprietes.initialisation().motDePasse());
            comptes.enregistrerMotDePasse(compte.id(), encodeur.encode(proprietes.initialisation().motDePasse()),
                    true, le, "initialisation");
        }
        LOG.warn("Sécurité initialisée : le groupe {} a reçu tous les droits ; {} devra changer son mot de passe. "
                + "Retirez maintenant DINASA_INIT_LOGIN et DINASA_INIT_MDP.", compte.idGroupe(), compte.login());
    }
}