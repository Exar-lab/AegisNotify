package com.aegisnotify.user.infrastructure.config;

import com.aegisnotify.user.infrastructure.security.SecurityScopes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
            // Path-level (not method-specific) match: every method under
            // /api/v1/users requires at least user:read in this slice, since
            // only GET routes exist. This deliberately lets an
            // otherwise-authorized DELETE request reach Spring MVC's
            // dispatcher and fail with 405 (no matching handler) instead of
            // being blocked earlier with a misleading 403 from denyAll() -
            // proving no delete route exists, not that scopes are wrong
            // (D4). Slice 5b splits this into GET->user:read / mutating
            // methods->user:admin once those routes exist (D3).
            .requestMatchers("/api/v1/users", "/api/v1/users/**")
            .hasAuthority(SecurityScopes.authority(SecurityScopes.USER_READ))
            .anyRequest().denyAll())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
    return http.build();
  }
}
