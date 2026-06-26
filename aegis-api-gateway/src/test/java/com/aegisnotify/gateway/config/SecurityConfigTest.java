package com.aegisnotify.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class SecurityConfigTest {

  @Autowired
  WebTestClient webTestClient;

  @MockBean
  ReactiveJwtDecoder jwtDecoder;

  @Test
  void actuatorHealthIsPublic() {
    webTestClient.get().uri("/actuator/health")
        .exchange()
        .expectStatus().isOk();
  }

  @Test
  void actuatorInfoIsPublic() {
    webTestClient.get().uri("/actuator/info")
        .exchange()
        .expectStatus().isOk();
  }

  @Test
  void notificationSubmitRequiresAuth() {
    webTestClient.post().uri("/api/v1/notifications")
        .exchange()
        .expectStatus().isUnauthorized();
  }

  @Test
  void statusEndpointRequiresAuth() {
    webTestClient.get().uri("/api/v1/notifications/some-id/status")
        .exchange()
        .expectStatus().isUnauthorized();
  }
}
