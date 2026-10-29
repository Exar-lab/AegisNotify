package com.aegisnotify.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

/**
 * Proxies each service's API docs under {@code /docs/<service>/**} and makes
 * those paths public. Active only under the {@code local} profile; routes are
 * declared in code (not in {@code application-local.yml}) because a profile's
 * {@code spring.cloud.gateway.routes} list would replace the whole base list.
 *
 * <p>{@code StripPrefix=2} maps {@code /docs/notification/v3/api-docs} to
 * {@code /v3/api-docs} on the target service.</p>
 */
@Configuration
@Profile("local")
public class ApiDocsConfig {

  @Bean
  RouteLocator apiDocsRouteLocator(RouteLocatorBuilder builder) {
    return builder.routes()
        .route("docs-notification", r -> r.path("/docs/notification/**")
            .filters(f -> f.stripPrefix(2))
            .uri("lb://aegis-notification-service"))
        .route("docs-user", r -> r.path("/docs/user/**")
            .filters(f -> f.stripPrefix(2))
            .uri("lb://aegis-user-service"))
        .route("docs-audit", r -> r.path("/docs/audit/**")
            .filters(f -> f.stripPrefix(2))
            .uri("lb://aegis-audit-service"))
        .build();
  }

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  SecurityWebFilterChain apiDocsSecurityWebFilterChain(ServerHttpSecurity http) {
    http
        .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/docs/**"))
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll());
    return http.build();
  }
}
