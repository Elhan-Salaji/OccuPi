package com.occupi.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

// Active only for local development — all requests are permitted.
// In production the 'dev' profile must not be active.
@Slf4j
@Profile("dev")
@Configuration
public class DevSecurityConfig {

    @Bean
    public SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
        // The default profile is 'dev', so an unconfigured deployment lands here —
        // make that impossible to miss in the logs.
        log.warn("dev profile active: security is DISABLED, every endpoint is open. "
                + "Never expose this profile on a public host — set SPRING_PROFILES_ACTIVE=prod.");
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                );
        return http.build();
    }
}
