package com.aegisnotify.gateway.config;

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

    submitAs("burst-subject").expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  void distinctSubjectsHaveIndependentBuckets() {
    for (int i = 0; i < BURST_CAPACITY; i++) {
      submitAs("subject-a").expectStatus().is5xxServerError();
    }
    submitAs("subject-a").expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

    submitAs("subject-b").expectStatus().is5xxServerError();
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
