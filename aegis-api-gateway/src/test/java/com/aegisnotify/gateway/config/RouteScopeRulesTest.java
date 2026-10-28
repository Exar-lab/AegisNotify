package com.aegisnotify.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher.MatchResult;
import reactor.core.publisher.Flux;

/**
 * Exercises {@link RouteScopeRules} directly, independent of the full
 * security filter chain {@link SecurityConfigTest} covers end to end.
 *
 * <p>The rule-count assertion is load-bearing: a route added to the table
 * without an accompanying case in {@link #routeScopeCases()} still passes
 * every other test here, so {@link #rules_declaresExactlySixteenRoutes()}
 * is what actually catches that gap (issue #173).</p>
 */
class RouteScopeRulesTest {

  private final RouteScopeRules routeScopeRules = new RouteScopeRules();

  @Test
  void rules_declaresExactlySixteenRoutes() {
    assertThat(routeScopeRules.rules()).hasSize(16);
  }

  @ParameterizedTest(name = "{0} {1} requires scope {2}")
  @MethodSource("routeScopeCases")
  void firstMatchingRule_resolvesToExpectedScope(
      HttpMethod method, String path, String expectedScope) {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.method(method, path));

    String resolvedScope = firstMatchingScope(exchange);

    assertThat(resolvedScope).isEqualTo(expectedScope);
  }

  /**
   * Mirrors {@link ScopeAwareAccessDeniedHandler}'s own resolution: the first
   * rule (in declaration order) whose matcher matches the exchange, since
   * {@code SecurityConfig} registers them first-match-wins.
   */
  private String firstMatchingScope(MockServerWebExchange exchange) {
    return Flux.fromIterable(routeScopeRules.rules())
        .concatMap(rule -> rule.matcher().matches(exchange)
            .filter(MatchResult::isMatch)
            .map(match -> rule.scope()))
        .next()
        .block();
  }

  private static Stream<Arguments> routeScopeCases() {
    return Stream.of(
        Arguments.of(HttpMethod.POST, "/api/v1/notifications", "notification:write"),
        Arguments.of(
            HttpMethod.GET, "/api/v1/notifications/abc-123/status", "notification:read"),
        Arguments.of(HttpMethod.GET, "/api/v1/notifications", "notification:read"),
        Arguments.of(
            HttpMethod.PATCH, "/api/v1/notifications/abc-123/cancel", "notification:write"),
        Arguments.of(HttpMethod.POST, "/api/v1/notifications/abc-123/retry", "notification:write"),
        Arguments.of(HttpMethod.GET, "/api/v1/dashboard/summary", "notification:read"),
        Arguments.of(HttpMethod.GET, "/api/v1/metrics/summary", "notification:read"),
        Arguments.of(HttpMethod.GET, "/api/v1/templates", "notification:read"),
        Arguments.of(HttpMethod.GET, "/api/v1/templates/abc-123", "notification:read"),
        Arguments.of(HttpMethod.POST, "/api/v1/templates", "notification:write"),
        Arguments.of(HttpMethod.PUT, "/api/v1/templates/abc-123", "notification:write"),
        Arguments.of(
            HttpMethod.PATCH, "/api/v1/templates/abc-123/status", "notification:write"),
        Arguments.of(HttpMethod.GET, "/api/v1/audit/notifications/abc-123", "audit:read"),
        Arguments.of(HttpMethod.POST, "/api/v1/audit/notifications/abc-123", "audit:read"),
        Arguments.of(HttpMethod.GET, "/api/v1/users/123", "user:read"),
        Arguments.of(HttpMethod.POST, "/api/v1/users", "user:admin"),
        Arguments.of(HttpMethod.PUT, "/api/v1/users/123", "user:admin"),
        Arguments.of(HttpMethod.PATCH, "/api/v1/users/123", "user:admin"));
  }
}
