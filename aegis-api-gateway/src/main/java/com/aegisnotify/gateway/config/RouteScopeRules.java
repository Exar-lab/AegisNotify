package com.aegisnotify.gateway.config;

import static org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers.pathMatchers;

import com.aegisnotify.gateway.security.SecurityScopes;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

/**
 * The route pattern to required-scope mapping table (design decision A1).
 *
 * <p>{@code user:read} / {@code user:admin} rules govern the
 * {@code aegis-user-service} routes ({@code /api/v1/users/**}) proxied by this
 * gateway.</p>
 */
@Component
public class RouteScopeRules {

  private final List<RouteScopeRule> rules = List.of(
      new RouteScopeRule(
          pathMatchers(HttpMethod.POST, "/api/v1/notifications"),
          SecurityScopes.NOTIFICATION_WRITE),
      new RouteScopeRule(
          pathMatchers(HttpMethod.GET, "/api/v1/notifications/*/status"),
          SecurityScopes.NOTIFICATION_READ),
      new RouteScopeRule(
          pathMatchers(HttpMethod.GET, "/api/v1/notifications"),
          SecurityScopes.NOTIFICATION_READ),
      new RouteScopeRule(
          pathMatchers(HttpMethod.PATCH, "/api/v1/notifications/*/cancel"),
          SecurityScopes.NOTIFICATION_WRITE),
      new RouteScopeRule(
          pathMatchers(HttpMethod.POST, "/api/v1/notifications/*/retry"),
          SecurityScopes.NOTIFICATION_WRITE),
      new RouteScopeRule(
          pathMatchers(HttpMethod.GET, "/api/v1/dashboard/**"),
          SecurityScopes.NOTIFICATION_READ),
      new RouteScopeRule(
          pathMatchers(HttpMethod.GET, "/api/v1/metrics/**"),
          SecurityScopes.NOTIFICATION_READ),
      new RouteScopeRule(
          pathMatchers(HttpMethod.GET, "/api/v1/templates", "/api/v1/templates/*"),
          SecurityScopes.NOTIFICATION_READ),
      new RouteScopeRule(
          pathMatchers(HttpMethod.POST, "/api/v1/templates"),
          SecurityScopes.NOTIFICATION_WRITE),
      new RouteScopeRule(
          pathMatchers(HttpMethod.PUT, "/api/v1/templates/*"),
          SecurityScopes.NOTIFICATION_WRITE),
      new RouteScopeRule(
          pathMatchers(HttpMethod.PATCH, "/api/v1/templates/*/status"),
          SecurityScopes.NOTIFICATION_WRITE),
      new RouteScopeRule(
          pathMatchers("/api/v1/audit/**"),
          SecurityScopes.AUDIT_READ),
      new RouteScopeRule(
          pathMatchers(HttpMethod.GET, "/api/v1/users/**"),
          SecurityScopes.USER_READ),
      new RouteScopeRule(
          pathMatchers(HttpMethod.POST, "/api/v1/users"),
          SecurityScopes.USER_ADMIN),
      new RouteScopeRule(
          pathMatchers(HttpMethod.PUT, "/api/v1/users/**"),
          SecurityScopes.USER_ADMIN),
      new RouteScopeRule(
          pathMatchers(HttpMethod.PATCH, "/api/v1/users/**"),
          SecurityScopes.USER_ADMIN));

  public List<RouteScopeRule> rules() {
    return rules;
  }
}
