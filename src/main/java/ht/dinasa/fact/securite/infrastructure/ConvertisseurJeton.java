package ht.dinasa.fact.securite.infrastructure;

import ht.dinasa.fact.securite.application.Authentification;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Relit le compte et les droits à chaque requête. */
@Component
public class ConvertisseurJeton implements Converter<Jwt, AbstractAuthenticationToken> {

    private final Authentification authentification;

    ConvertisseurJeton(Authentification authentification) {
        this.authentification = authentification;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Object v = jwt.getClaims().get(EmetteurJetonsJwt.VERSION);
        int version = v instanceof Number n ? n.intValue() : -1;
        var droits = authentification.permissionsSiJetonValide(jwt.getSubject(), version)
                .orElseThrow(() -> new InvalidBearerTokenException("Session expirée ou révoquée : reconnectez-vous."));
        return new JwtAuthenticationToken(jwt,
                droits.stream().map(p -> new SimpleGrantedAuthority(p.name())).toList(), jwt.getSubject());
    }
}