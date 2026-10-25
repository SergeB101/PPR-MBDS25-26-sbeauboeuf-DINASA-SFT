package ht.dinasa.fact.securite.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("dinasa.securite")
public record ProprietesSecurite(@DefaultValue Jwt jwt,
                                 @DefaultValue("5") int tentativesMax,
                                 @DefaultValue("15m") Duration dureeBlocage,
                                 @DefaultValue("http://localhost:5173") List<String> originesAutorisees,
                                 @DefaultValue Initialisation initialisation) {

    public record Jwt(String secret, @DefaultValue("8h") Duration duree) {
    }

    public record Initialisation(String login, String motDePasse) {
    }
}