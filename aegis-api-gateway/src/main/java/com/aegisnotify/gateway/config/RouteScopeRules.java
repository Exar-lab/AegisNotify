package com.aegisnotify.gateway.config;

import static org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers.pathMatchers;

import com.aegisnotify.gateway.security.SecurityScopes;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

/**
 * The route pattern to required-scope mapping table (design decision A1).
 *
 * <p>{@code user:read} / {@code user:admin} rules are forward-looking: the
 * {@code aegis-user-service} routes they govern are not yet proxied by this
 * gateway (they ship in a later slice), but the scope-gating contract is
 * defined here up front so both the docs and the eventual realm import stay
 * in lockstep with the code.</p>
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
