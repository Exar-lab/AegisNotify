package com.aegisnotify.user.infrastructure.config;

import com.aegisnotify.user.infrastructure.security.SecurityScopes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus")
            .permitAll()
            // GET (list/query) requires user:read only.
            .requestMatchers(HttpMethod.GET, "/api/v1/users", "/api/v1/users/**")
            .hasAuthority(SecurityScopes.authority(SecurityScopes.USER_READ))
            // POST/PUT/PATCH (create/update/disable/reset-password) require
            // user:admin only — non-hierarchical (D3): a user:read-only
            // token is rejected here, and a user:admin-only token needs no
            // user:read to use these routes. Path-level (not narrowed to a
            // fixed sub-path list), so an otherwise-authorized DELETE still
            // reaches Spring MVC's dispatcher and fails with 405 (no
            // handler), never a misleading 403 — proving no delete route
            // exists, not that scopes are wrong (D4).
            .requestMatchers(HttpMethod.POST, "/api/v1/users", "/api/v1/users/**")
            .hasAuthority(SecurityScopes.authority(SecurityScopes.USER_ADMIN))
            .requestMatchers(HttpMethod.PUT, "/api/v1/users", "/api/v1/users/**")
            .hasAuthority(SecurityScopes.authority(SecurityScopes.USER_ADMIN))
            .requestMatchers(HttpMethod.PATCH, "/api/v1/users", "/api/v1/users/**")
            .hasAuthority(SecurityScopes.authority(SecurityScopes.USER_ADMIN))
            // DELETE has no route anywhere in this controller (D4). It is
            // deliberately authorized here (same requirement as GET) so an
            // otherwise-fully-authorized DELETE request still reaches
            // Spring MVC's dispatcher and fails with a genuine 405 (no
            // handler) — proving no delete route exists, not that scopes
            // are wrong. If this matcher were removed, DELETE would fall
            // through to denyAll() below and produce a misleading 403
            // instead, which would prove nothing about D4.
            .requestMatchers(HttpMethod.DELETE, "/api/v1/users", "/api/v1/users/**")
            .hasAuthority(SecurityScopes.authority(SecurityScopes.USER_READ))
            .anyRequest().denyAll())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
    return http.build();
  }
}
