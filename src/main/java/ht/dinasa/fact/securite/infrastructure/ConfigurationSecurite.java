package ht.dinasa.fact.securite.infrastructure;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import ht.dinasa.fact.securite.application.ProprietesSecurite;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static ht.dinasa.fact.securite.domain.Permission.*;

@Configuration
@EnableConfigurationProperties(ProprietesSecurite.class)
class ConfigurationSecurite {

    static final String EMETTEUR = "dinasa-fact-api";

    @Bean
    SecurityFilterChain filtreSecurite(HttpSecurity http, ConvertisseurJeton convertisseur) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        // Public
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/connexion", "/api/v1/auth/changement-mot-de-passe").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Connecté
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/moi").authenticated()
                        // Administration des accès
                        .requestMatchers("/api/v1/securite/**").hasAuthority(SECURITE_ADMINISTRER.name())
                        // Facturation
                        .requestMatchers(HttpMethod.POST, "/api/v1/facturation/generation").hasAuthority(FACTURES_GENERER.name())
                        .requestMatchers(HttpMethod.POST, "/api/v1/factures/*/poster").hasAuthority(FACTURES_POSTER.name())
                        .requestMatchers(HttpMethod.POST, "/api/v1/factures/*/services/**").hasAuthority(FACTURES_MODIFIER.name())
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/factures/**").hasAuthority(FACTURES_MODIFIER.name())
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/factures/**").hasAuthority(FACTURES_MODIFIER.name())
                        .requestMatchers(HttpMethod.GET, "/api/v1/facturation/**", "/api/v1/factures/**", "/api/v1/services/**")
                        .hasAuthority(FACTURES_CONSULTER.name())
                        // ACCPAC
                        .requestMatchers(HttpMethod.GET, "/api/v1/accpac/**").hasAuthority(ACCPAC_CONSULTER.name())
                        .requestMatchers(HttpMethod.POST, "/api/v1/accpac/recuperation").hasAuthority(ACCPAC_RECUPERER.name())
                        .requestMatchers(HttpMethod.POST, "/api/v1/accpac/anomalies/*/correction").hasAuthority(ACCPAC_CORRIGER.name())
                        .requestMatchers(HttpMethod.POST, "/api/v1/accpac/annulation").hasAuthority(ACCPAC_ANNULER.name())
                        // Livraison C (à venir)
                        .requestMatchers(HttpMethod.GET, "/api/v1/referentiel/**").hasAuthority(REFERENTIEL_CONSULTER.name())
                        .requestMatchers(HttpMethod.GET, "/api/v1/tableaux-de-bord/**").hasAuthority(TABLEAU_DE_BORD_CONSULTER.name())
                        // Tout ce qui n'est pas listé est REFUSÉ
                        .requestMatchers(HttpMethod.GET, "/api/v1/exports/**").hasAuthority(FACTURES_CONSULTER.name())
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(convertisseur)));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(ProprietesSecurite proprietes) {
        NimbusJwtDecoder decodeur = NimbusJwtDecoder.withSecretKey(cle(proprietes)).macAlgorithm(MacAlgorithm.HS256).build();
        decodeur.setJwtValidator(JwtValidators.createDefaultWithIssuer(EMETTEUR));
        return decodeur;
    }

    @Bean
    JwtEncoder jwtEncoder(ProprietesSecurite proprietes) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(cle(proprietes)));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder(); // BCrypt
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(ProprietesSecurite proprietes) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(proprietes.originesAutorisees());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    private static SecretKey cle(ProprietesSecurite proprietes) {
        String secret = proprietes.jwt().secret();
        byte[] octets = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (octets.length < 32) {
            throw new IllegalStateException("dinasa.securite.jwt.secret doit contenir au moins 32 caractères.");
        }
        return new SecretKeySpec(octets, "HmacSHA256");
    }
}