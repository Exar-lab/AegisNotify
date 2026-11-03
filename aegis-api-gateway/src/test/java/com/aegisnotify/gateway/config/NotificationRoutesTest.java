package com.aegisnotify.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Verifies the routes declared in the production {@code application.yml} (not the test
 * profile's overrides, which replace the whole route list) cover the notification
 * cancel and retry endpoints that {@link RouteScopeRules} already protects (issue #214).
 */
class NotificationRoutesTest {

  private static Map<String, RouteDefinition> routesById;

  @BeforeAll
  static void loadProductionRoutes() throws IOException {
    List<PropertySource<?>> sources = new YamlPropertySourceLoader()
        .load("application", new ClassPathResource("application.yml"));
    MutablePropertySources propertySources = new MutablePropertySources();
    sources.forEach(propertySources::addLast);
    List<RouteDefinition> routes = new Binder(ConfigurationPropertySources.from(propertySources))
        .bind("spring.cloud.gateway.routes", Bindable.listOf(RouteDefinition.class))
        .get();
    routesById = routes.stream()
        .collect(Collectors.toMap(RouteDefinition::getId, route -> route));
  }

  @Test
  void cancelRoute_proxiesPatchToNotificationService() {
    RouteDefinition route = routesById.get("notification-cancel");

    assertThat(route).as("notification-cancel route").isNotNull();
    assertThat(route.getUri().toString()).isEqualTo("lb://aegis-notification-service");
    assertThat(predicateArgs(route, "Path")).containsExactly("/api/v1/notifications/*/cancel");
    assertThat(predicateArgs(route, "Method")).containsExactly("PATCH");
  }

  @Test
  void retryRoute_proxiesPostToNotificationService() {
    RouteDefinition route = routesById.get("notification-retry");

    assertThat(route).as("notification-retry route").isNotNull();
    assertThat(route.getUri().toString()).isEqualTo("lb://aegis-notification-service");
    assertThat(predicateArgs(route, "Path")).containsExactly("/api/v1/notifications/*/retry");
    assertThat(predicateArgs(route, "Method")).containsExactly("POST");
  }

  private static List<String> predicateArgs(RouteDefinition route, String predicateName) {
    return route.getPredicates().stream()
        .filter(predicate -> predicate.getName().equals(predicateName))
        .flatMap(predicate -> predicate.getArgs().values().stream())
        .toList();
  }
}
