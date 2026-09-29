package ht.dinasa.fact.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class Horloge {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}