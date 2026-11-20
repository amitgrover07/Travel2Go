package com.travel2go.agent.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Scope-gated access to the agent facade. The published OpenAPI doc and health
 * probe are open; every other endpoint requires a valid agent key, and mutations
 * (Phase 2+) additionally require the {@code book} scope - so a read-only key can
 * never reach a booking/payment mutation (fail-closed).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ApiKeyRegistry registry;

    public SecurityConfig(ApiKeyRegistry registry) {
        this.registry = registry;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/agent/v1/openapi.yaml").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // Mutations require the book scope (Phase 2+ agent booking/payment).
                        .requestMatchers("/api/agent/v1/bookings/**").hasAuthority("SCOPE_book")
                        // All other agent endpoints require at least the read scope.
                        .requestMatchers("/api/agent/v1/**").hasAuthority("SCOPE_read")
                        .anyRequest().denyAll()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\":\"Unauthorized\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(403);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\":\"Forbidden - missing required scope\"}");
                        })
                )
                .addFilterBefore(new ApiKeyAuthFilter(registry), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
