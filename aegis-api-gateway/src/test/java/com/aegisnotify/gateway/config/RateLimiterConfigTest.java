package com.aegisnotify.gateway.config;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
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

  @Test
  void emitsEmpty_whenSecurityContextHasNullAuthentication() {
    // The actual bug this guards: a real SecurityContext with a null
    // Authentication reached this resolver against the live reactive filter
    // chain (first real authenticated-POST exercise of this route — see
    // SecurityConfigTest's CORS fix for the sibling first-real-exercise bug).
    // `.map(SecurityContext::getAuthentication)` doesn't tolerate that -
    // Reactor's map() rejects a null return with "the mapper ... returned a
    // null value" instead of treating it as "no key to resolve", a
    // NullPointerException the request rate limiter has no empty-key
    // recovery for. The synthetic `withAuthentication(...)` helper the other
    // tests use can't reproduce this: it always builds a fully-populated
    // SecurityContext, never a null-Authentication one.
    StepVerifier.create(keyResolver.resolve(exchange())
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                Mono.just(new SecurityContextImpl(null)))))
        .verifyComplete();
  }

  @Test
  void emitsEmpty_whenAuthenticationNameIsNull() {
    // Sibling of the null-SecurityContext regression above, same real bug
    // class: an authenticated principal whose getName() itself returns null
    // (e.g. a JWT missing its sub claim) hit the next .map() in the chain
    // with the identical "mapper returned a null value" crash.
    // TestingAuthenticationToken can't produce this (its getName() always
    // derives from a non-null principal), hence the hand-rolled stub —
    // Mockito's inline mock maker can't instantiate on this module's JDK.
    StepVerifier.create(keyResolver.resolve(exchange())
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                new NullNamedAuthentication())))
        .verifyComplete();
  }

  @Test
  void resolvesKeyFromJwtSubject_whenPresent() {
    Jwt jwt = jwtWithClaims(Map.of("sub", "user-456", "jti", "token-id-1"));

    StepVerifier.create(keyResolver.resolve(exchange())
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                new JwtAuthenticationToken(jwt, List.of()))))
        .expectNext("user-456")
        .verifyComplete();
  }

  @Test
  void resolvesKeyFromJwtId_whenSubjectClaimIsAbsent() {
    // The actual bug: this realm's dev access tokens carry no `sub` claim at
    // all (observed against the real running app, not a synthetic case —
    // see RateLimiterConfig#resolveSubjectOrTokenId's javadoc). A
    // JwtAuthenticationToken's getName() always reads `sub`, so every real
    // authenticated POST resolved an empty key and got denied with 403 by
    // RequestRateLimiter's deny-empty-key default, even after authorization
    // had already passed. `jti` is always present, so it's the fallback.
    Jwt jwt = jwtWithClaims(Map.of("jti", "token-id-2"));

    StepVerifier.create(keyResolver.resolve(exchange())
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                new JwtAuthenticationToken(jwt, List.of()))))
        .expectNext("token-id-2")
        .verifyComplete();
  }

  private Jwt jwtWithClaims(Map<String, Object> claims) {
    Jwt.Builder builder = Jwt.withTokenValue("token-value")
        .header("alg", "none")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60));
    claims.forEach(builder::claim);
    return builder.build();
  }

  private static final class NullNamedAuthentication implements Authentication {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
      return List.of();
    }

    @Override
    public Object getCredentials() {
      return null;
    }

    @Override
    public Object getDetails() {
      return null;
    }

    @Override
    public Object getPrincipal() {
      return null;
    }

    @Override
    public boolean isAuthenticated() {
      return true;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) {
      // Unused by this test — Authentication requires it be implemented.
    }

    @Override
    public String getName() {
      return null;
    }
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
