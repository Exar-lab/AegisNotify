package com.aegisnotify.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import reactor.test.StepVerifier;

/**
 * Verifies {@link RateLimiterConfig#userKeyResolver()} (design decision D3): it resolves the
 * rate-limit key from the authenticated JWT subject, and denies (empty {@code Mono}) rather than
 * bucketing anonymous or blank-principal traffic under one shared key.
 */
class RateLimiterConfigTest {

  private final RateLimiterConfig rateLimiterConfig = new RateLimiterConfig();
  private final KeyResolver keyResolver = rateLimiterConfig.userKeyResolver();

  @Test
  void resolvesKeyFromAuthenticatedSubject() {
    Authentication authenticated = authenticationNamed("user-123", true);

    StepVerifier.create(keyResolver.resolve(exchange())
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authenticated)))
        .expectNext("user-123")
        .verifyComplete();
  }

  @Test
  void emitsEmpty_whenUnauthenticated() {
    Authentication unauthenticated = authenticationNamed("user-123", false);

    StepVerifier.create(keyResolver.resolve(exchange())
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(unauthenticated)))
        .verifyComplete();
  }

  @Test
  void emitsEmpty_whenPrincipalNameIsBlank() {
    Authentication blankName = authenticationNamed("   ", true);

    StepVerifier.create(keyResolver.resolve(exchange())
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(blankName)))
        .verifyComplete();
  }

  private ServerWebExchange exchange() {
    return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/notifications"));
  }

  private Authentication authenticationNamed(String name, boolean authenticated) {
    TestingAuthenticationToken token = new TestingAuthenticationToken(name, "credentials");
    token.setAuthenticated(authenticated);
    return token;
  }
}
