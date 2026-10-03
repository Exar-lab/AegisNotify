package com.aegisnotify.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

/**
 * Rate-limiting beans for the notification submission route (design decision D3).
 *
 * <p>Redis failure posture is DEGRADE OPEN (design decision D1): Spring Cloud
 * Gateway's {@code RedisRateLimiter} admits traffic when Redis is unreachable, so a
 * Redis outage never blocks notification submission. Short Lettuce timeouts
 * ({@code spring.data.redis.timeout}) keep that degradation fast, and the Redis
 * health contributor makes it observable at {@code /actuator/health}.</p>
 */
@Configuration
public class RateLimiterConfig {

  /**
   * Resolves the rate-limit bucket key from the authenticated JWT subject,
   * falling back to the JWT ID ({@code jti}) when the subject claim is
   * absent. An unauthenticated or blank principal yields an empty {@code
   * Mono}, which the {@code RequestRateLimiter} filter rejects via its
   * {@code deny-empty-key} default (status 403) rather than sharing one
   * bucket across callers.
   *
   * <p>{@code sub} is the correct/intended key (one bucket per real user —
   * design decision D3's actual intent) and is what every standard-issue
   * Keycloak access token carries. This realm's dev tokens are observed
   * NOT to include {@code sub} at all (a minimal-claims access token — no
   * {@code sub}, no {@code preferred_username}), which made every real
   * authenticated POST deny-empty-key 403 even after authorization passed.
   * {@code jti} is always present and unique per token, so it keeps rate
   * limiting meaningful (one bucket per issued token) without that claim.
   * This is a resolver-side fallback, not a statement that {@code sub}
   * should go missing — if a realm change restores it, this still prefers
   * it first.</p>
   *
   * <p>Both {@code SecurityContext#getAuthentication()} and the subject/ID
   * lookups below can themselves return {@code null} — real, observed
   * cases against the live reactive filter chain, not just theoretical
   * ones (see the regression tests for this method). Reactor's {@code
   * .map()} forbids a null return and throws "The mapper ... returned a
   * null value" instead of propagating it, so every such step uses {@code
   * .flatMap(... Mono.justOrEmpty(...))}, which turns a null into an empty
   * {@code Mono} like every other "can't resolve a key" case here.</p>
   *
   * @return a reactive key resolver keyed on the JWT subject, or its ID when the subject is absent
   */
  @Bean
  public KeyResolver userKeyResolver() {
    return exchange -> ReactiveSecurityContextHolder.getContext()
        .flatMap(context -> Mono.justOrEmpty(context.getAuthentication()))
        .filter(Authentication::isAuthenticated)
        .flatMap(this::resolveSubjectOrTokenId)
        .filter(StringUtils::hasText);
  }

  private Mono<String> resolveSubjectOrTokenId(Authentication authentication) {
    if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
      var jwt = jwtAuthentication.getToken();
      String subjectOrId = jwt.getSubject() != null ? jwt.getSubject() : jwt.getId();
      return Mono.justOrEmpty(subjectOrId);
    }
    return Mono.justOrEmpty(authentication.getName());
  }
}
