package com.aegisnotify.gateway.config;

import com.aegisnotify.gateway.security.SecurityScopes;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

  /**
   * CORS is enforced here, not via {@code spring.cloud.gateway.globalcors}.
   * Gateway's own CORS filter runs after route-to-request-url resolution has
   * already rewritten the exchange's request URI toward the (lb://) backend,
   * so {@code CorsUtils.isSameOrigin()}'s read of the request's own scheme/
   * host/port throws (surfaces as "Reject: origin is malformed", a bare 403
   * with no body — easy to mistake for a bad allowed-origins value, it isn't
   * one). Spring Security's own {@code .cors()} integration runs as an early
   * WebFilter, well before Gateway's routing filters, and doesn't hit this.
   */
  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${CORS_ALLOWED_ORIGINS:http://localhost:4200}") String allowedOrigin) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of(allowedOrigin));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  SecurityWebFilterChain securityWebFilterChain(
      ServerHttpSecurity http,
      CorsConfigurationSource corsConfigurationSource,
      RouteScopeRules routeScopeRules,
      ScopeAwareAccessDeniedHandler scopeAwareAccessDeniedHandler) {
    http
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .authorizeExchange(exchanges -> {
          // CORS preflight requests never carry credentials — gating them behind
          // JWT auth makes Spring Security reject the OPTIONS request itself,
          // which the browser then reads as the whole CORS handshake failing
          // (the real GET/POST never even gets sent). Must be the first rule
          // registered, same ordering requirement as the comment below.
          exchanges.pathMatchers(HttpMethod.OPTIONS, "/**").permitAll();
          exchanges.pathMatchers("/actuator/health", "/actuator/info").permitAll();
          // Registration order is load-bearing: reactive authorizeExchange matches
          // rules in the order they're registered, first-match-wins. The per-route
          // scope rules MUST be registered before the generic /api/v1/** catch-all
          // below, or a scoped route silently downgrades to authentication-only —
          // with no compile/test failure unless that exact route+missing-scope case
          // is covered.
          routeScopeRules.rules().forEach(rule -> exchanges.matchers(rule.matcher())
              .hasAuthority(SecurityScopes.authority(rule.scope())));
          exchanges.pathMatchers("/api/v1/**").authenticated();
          exchanges.anyExchange().authenticated();
        })
        .exceptionHandling(exceptions -> exceptions
            .accessDeniedHandler(scopeAwareAccessDeniedHandler))
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
    return http.build();
  }
}
