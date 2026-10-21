package com.aegisnotify.gateway.config;

import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Proves the {@code notification-submit} route enforces the Redis-backed token-bucket limit
 * (design decision D2/D4): a burst up to {@code burstCapacity} is admitted, the request beyond
 * it receives {@code 429}, and distinct JWT subjects hold independent buckets
 * ({@code userKeyResolver}, design decision D3).
 *
 * <p>Overlay values are deliberately tiny ({@code replenishRate=1}, {@code burstCapacity=2},
 * {@code requestedTokens=1}) to keep the drain loop fast and avoid the one-token-per-second refill
 * window (design decision D5 — rate-limiter route config lives in this test class, not
 * {@code application-test.yml}).</p>
 *
 * <p>The route is defined here as one fully self-contained property source (own
 * {@code id}/{@code uri}/{@code predicates}/{@code filters}), not a partial override of index 0
 * from {@code application-test.yml}: Spring Boot's relaxed binder resolves the entire
 * {@code spring.cloud.gateway.routes} list from whichever property source first supplies it, so a
 * higher-priority source defining only {@code routes[0].filters[...]} leaves {@code uri} and
 * {@code predicates} unbound and fails {@code GatewayProperties} validation — the same binder
 * limitation documented in {@link TokenRelayPropagationTest}.</p>
 *
 * <p><b>Confirmed root cause of this test's intermittent failures</b> (decompiled
 * {@code RedisRateLimiter.isAllowed} directly to verify, not guessed): the Java client always
 * passes an empty string for the script's {@code now} argument, so {@code
 * request_rate_limiter.lua} falls back to {@code redis.call('TIME')[1]} — Redis server time
 * truncated to whole SECONDS, discarding the microsecond component entirely. With {@code
 * replenishRate=1}, if this burst of requests happens to straddle a real wall-clock second
 * boundary (not "waiting a second" — just the test running at, say, x.998s then x+1.002s), the
 * script computes a 1-second delta and refills one bonus token it shouldn't have, so the call
 * expected to be the first rejection goes through instead. This has nothing to do with Docker,
 * Testcontainers, or {@code userKeyResolver} (which resolves correctly every time) — it is
 * inherent to this algorithm's whole-second granularity combined with a sub-second burst, and it
 * can happen on any run, local or CI, regardless of machine speed.</p>
 *
 * <p>Fix: don't assert the rejection lands on a specific call index. Burn exactly {@code
 * burstCapacity} requests (always admitted — a boundary-crossing bonus token only ever adds
 * capacity, never removes it, so this part is unaffected), then poll for the rejection across up
 * to {@link #BOUNDARY_TOLERANCE_ATTEMPTS} extra requests instead of exactly one. One bonus token
 * from a single boundary crossing delays the rejection by at most one call; tolerating a few
 * covers that with margin to spare.</p>
 */
@SpringBootTest(properties = {
    "spring.cloud.gateway.routes[0].id=rate-limit-probe",
    "spring.cloud.gateway.routes[0].uri=lb://aegis-notification-service",
    "spring.cloud.gateway.routes[0].predicates[0]=Path=/api/v1/notifications",
    "spring.cloud.gateway.routes[0].predicates[1]=Method=POST",
    "spring.cloud.gateway.routes[0].filters[0].name=RequestRateLimiter",
    "spring.cloud.gateway.routes[0].filters[0].args.redis-rate-limiter.replenishRate=1",
    "spring.cloud.gateway.routes[0].filters[0].args.redis-rate-limiter.burstCapacity=2",
    "spring.cloud.gateway.routes[0].filters[0].args.redis-rate-limiter.requestedTokens=1",
    "spring.cloud.gateway.routes[0].filters[0].args.key-resolver=#{@userKeyResolver}",
    "management.health.redis.enabled=true"
})
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Testcontainers
class RateLimiterIntegrationTest {

  private static final int BURST_CAPACITY = 2;
  private static final int BOUNDARY_TOLERANCE_ATTEMPTS = 3;

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  @Autowired
  WebTestClient webTestClient;

  @MockBean
  ReactiveJwtDecoder jwtDecoder;

  @Test
  void submitWithinBurstCapacityIsAllowed_thenReturns429() {
    for (int i = 0; i < BURST_CAPACITY; i++) {
      submitAs("burst-subject").expectStatus().is5xxServerError();
    }

    expectEventuallyRateLimited("burst-subject");
  }

  @Test
  void distinctSubjectsHaveIndependentBuckets() {
    for (int i = 0; i < BURST_CAPACITY; i++) {
      submitAs("subject-a").expectStatus().is5xxServerError();
    }
    expectEventuallyRateLimited("subject-a");

    submitAs("subject-b").expectStatus().is5xxServerError();
  }

  /**
   * Keeps submitting as {@code subject} until a {@code 429} is observed or {@link
   * #BOUNDARY_TOLERANCE_ATTEMPTS} extra requests have been sent without one — see the class
   * javadoc for why exactly one extra attempt can legitimately be needed.
   */
  private void expectEventuallyRateLimited(String subject) {
    for (int attempt = 1; attempt <= BOUNDARY_TOLERANCE_ATTEMPTS; attempt++) {
      var status = submitAs(subject).returnResult(Void.class).getStatus();
      if (status.equals(HttpStatus.TOO_MANY_REQUESTS)) {
        return;
      }
    }
    fail(subject + " was not rate limited within " + BOUNDARY_TOLERANCE_ATTEMPTS
        + " extra requests beyond burst capacity");
  }

  private WebTestClient.ResponseSpec submitAs(String subject) {
    return webTestClient
        .mutateWith(mockJwt()
            .jwt(jwt -> jwt.subject(subject))
            .authorities(() -> "SCOPE_notification:write"))
        .post().uri("/api/v1/notifications")
        .exchange();
  }
}
