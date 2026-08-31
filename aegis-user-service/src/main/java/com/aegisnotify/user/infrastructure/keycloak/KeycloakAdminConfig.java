package com.aegisnotify.user.infrastructure.keycloak;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * External configuration for the Keycloak Admin REST client used by {@link
 * KeycloakTokenProvider} and {@link KeycloakAdminClientAdapter}.
 *
 * <p>{@code clientSecret} (and every other text field) has no default and
 * fails fast at startup if left blank, matching {@code ProviderConfig}'s
 * credential-validation convention in aegis-notification-service and {@code
 * NotificationKafkaProperties}'s compact-constructor validation style.</p>
 *
 * @param baseUrl Keycloak base URL, e.g. {@code http://localhost:8088}
 * @param realm the Keycloak realm managed by this service
 * @param clientId the confidential client id used for client-credentials auth
 * @param clientSecret the confidential client secret; never logged
 * @param timeout the bounded response timeout applied to every WebClient
 *     call (token requests and Admin API calls); defaults to 5 seconds when
 *     unset so a hung/slow Keycloak can never block a caller thread forever
 */
@ConfigurationProperties(prefix = "keycloak.admin")
public record KeycloakAdminConfig(String baseUrl, String realm, String clientId,
    String clientSecret, Duration timeout) {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

  @ConstructorBinding
  public KeycloakAdminConfig {
    baseUrl = requireText(baseUrl, "keycloak.admin.base-url");
    realm = requireText(realm, "keycloak.admin.realm");
    clientId = requireText(clientId, "keycloak.admin.client-id");
    clientSecret = requireText(clientSecret, "keycloak.admin.client-secret");
    timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
  }

  private static String requireText(String value, String propertyName) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalStateException(propertyName + " must be configured");
    }
    return value;
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(KeycloakAdminConfig.class)
  static class Registration {
  }
}
