package com.aegisnotify.gateway.config;

import com.aegisnotify.gateway.security.SecurityScopes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

  @Bean
  SecurityWebFilterChain securityWebFilterChain(
      ServerHttpSecurity http,
      RouteScopeRules routeScopeRules,
      ScopeAwareAccessDeniedHandler scopeAwareAccessDeniedHandler) {
    http
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(exchanges -> {
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
