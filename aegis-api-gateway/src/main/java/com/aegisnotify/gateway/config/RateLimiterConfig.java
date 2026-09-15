package com.aegisnotify.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.util.StringUtils;

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
   * Resolves the rate-limit bucket key from the authenticated JWT subject.
   *
   * <p>{@code Authentication#getName()} already returns the {@code sub} claim for a
   * {@code JwtAuthenticationToken}, so no cast to {@code Jwt} is needed. An
   * unauthenticated or blank principal yields an empty {@code Mono}, which the
   * {@code RequestRateLimiter} filter rejects via its {@code deny-empty-key} default
   * rather than sharing one bucket across callers.</p>
   *
   * @return a reactive key resolver keyed on the JWT subject
   */
  @Bean
  public KeyResolver userKeyResolver() {
    return exchange -> ReactiveSecurityContextHolder.getContext()
        .map(SecurityContext::getAuthentication)
        .filter(Authentication::isAuthenticated)
        .map(Authentication::getName)
        .filter(StringUtils::hasText);
  }
}
