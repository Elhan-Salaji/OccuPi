package com.occupi.feature.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the class-level {@code @PreAuthorize} on {@link MetricsProviderController} in
 * isolation: the imported filter chain permits every request at the URL level, so a 403
 * can only come from the controller's method-level role check — proving the metrics read
 * API is admin-only regardless of the production URL rules in {@code SecurityConfig}.
 */
@WebMvcTest(MetricsProviderController.class)
@Import(MetricsProviderControllerMethodSecurityTest.MethodSecurityConfig.class)
@DisplayName("MetricsProviderController method security (@PreAuthorize)")
class MetricsProviderControllerMethodSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MetricsProviderService providerService;

    // Satisfies OAuth2ResourceServerAutoConfiguration; the test injects auth via jwt().
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("blocks the all-metrics list for a non-admin token (403)")
    void getAll_nonAdmin_returns403() throws Exception {
        mvc.perform(get("/api/metrics").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("allows the all-metrics list for an admin token (200)")
    void getAll_admin_returns200() throws Exception {
        when(providerService.getLatestForAllSensors()).thenReturn(List.of());

        mvc.perform(get("/api/metrics")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("blocks a single-sensor read for a non-admin token (403)")
    void getOne_nonAdmin_returns403() throws Exception {
        mvc.perform(get("/api/metrics/sensor-A").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("blocks a history read for a non-admin token (403)")
    void getHistory_nonAdmin_returns403() throws Exception {
        mvc.perform(get("/api/metrics/sensor-A/history")
                        .param("since", "2026-06-14T00:00:00Z").with(jwt()))
                .andExpect(status().isForbidden());
    }

    /**
     * Method security enabled over a deliberately permissive URL layer, so the only
     * thing that can deny a request is {@code @PreAuthorize} on the controller.
     */
    @TestConfiguration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }
    }
}
