package com.aegisnotify.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

/**
 * Loads the real, non-{@code test}-profile Spring context so the {@code notification-submit}
 * route binds exactly as production sees it in {@code application.yml}. The {@code test}
 * profile intentionally replaces this route with a filter-free, closed-port stand-in (design
 * decision D5), so no other test in this suite ever exercises the documented default rate-limit
 * placeholders (spec requirement "Configurable Rate Limit Values", scenario "Defaults apply
 * when unset").
 *
 * <p>Eureka self-registration is disabled purely to keep this test hermetic — no Eureka server
 * is reachable here — and has no bearing on how {@code GatewayProperties} binds the route and
 * filter argument placeholders.</p>
 */
@SpringBootTest(properties = {
    "eureka.client.enabled=false",
    "eureka.client.register-with-eureka=false",
    "eureka.client.fetch-registry=false"
})
class DefaultRateLimitPropertiesBindingTest {

  @Autowired
  RouteDefinitionLocator routeDefinitionLocator;

  @Test
  void notificationSubmitRouteBindsDocumentedDefaultRateLimitValues() {
    List<RouteDefinition> routes =
        routeDefinitionLocator.getRouteDefinitions().collectList().block();

    RouteDefinition notificationSubmit = routes.stream()
        .filter(route -> "notification-submit".equals(route.getId()))
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException("notification-submit route not found"));

    FilterDefinition rateLimiter = notificationSubmit.getFilters().stream()
        .filter(filter -> "RequestRateLimiter".equals(filter.getName()))
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException("RequestRateLimiter filter not found"));

    assertThat(rateLimiter.getArgs())
        .containsEntry("redis-rate-limiter.replenishRate", "100")
        .containsEntry("redis-rate-limiter.burstCapacity", "1200")
        .containsEntry("redis-rate-limiter.requestedTokens", "60");
  }
}
