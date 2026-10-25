package ht.dinasa.fact.securite.infrastructure;

import ht.dinasa.fact.securite.application.EmetteurJetons;
import ht.dinasa.fact.securite.application.ProprietesSecurite;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
class EmetteurJetonsJwt implements EmetteurJetons {

    static final String VERSION = "ver";

    private final JwtEncoder encodeur;
    private final ProprietesSecurite proprietes;

    EmetteurJetonsJwt(JwtEncoder encodeur, ProprietesSecurite proprietes) {
        this.encodeur = encodeur;
        this.proprietes = proprietes;
    }

    @Override
    public Jeton emettre(String login, int version, Instant maintenant) {
        Instant expire = maintenant.plus(proprietes.jwt().duree());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ConfigurationSecurite.EMETTEUR)
                .subject(login)
                .issuedAt(maintenant)
                .expiresAt(expire)
                .id(UUID.randomUUID().toString())
                .claim(VERSION, version)
                .build();
        String valeur = encodeur.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new Jeton(valeur, expire);
    }
}