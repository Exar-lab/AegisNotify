package com.aegisnotify.gateway.config;

import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;

/**
 * Binds a route matcher to the OAuth2 scope required to access it.
 *
 * <p>This table is the single source of truth consumed twice: once to build
 * the reactive {@code authorizeExchange} matchers in {@link SecurityConfig},
 * and once by {@link ScopeAwareAccessDeniedHandler} to name the required
 * scope in a denial response.</p>
 *
 * @param matcher the exchange matcher identifying requests this rule governs
 * @param scope the unprefixed scope literal required, e.g. {@code "audit:read"}
 */
public record RouteScopeRule(ServerWebExchangeMatcher matcher, String scope) {
}
