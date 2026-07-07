package com.aegisnotify.notification.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.domain.enums.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class FirebasePushProviderAdapterTest {

  @Test
  void sendReturnsSentOnSuccessfulResponse() {
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> Mono.just(
            ClientResponse.create(HttpStatus.OK).build()))
        .build();

    FirebasePushProviderAdapter adapter =
        new FirebasePushProviderAdapter(webClient, "aegis-project", "test-access-token");

    ProviderResult result = adapter.send(Channel.PUSH, "device-token-123", "Hello", "Welcome");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.SENT);
    assertThat(result.providerName()).isEqualTo("FCM");
    assertThat(result.errorDetail()).isNull();
  }

  @Test
  void sendReturnsFailedOnErrorResponse() {
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> Mono.just(
            ClientResponse.create(HttpStatus.BAD_REQUEST)
                .body("{\"error\":{\"message\":\"invalid token\"}}")
                .build()))
        .build();

    FirebasePushProviderAdapter adapter =
        new FirebasePushProviderAdapter(webClient, "aegis-project", "test-access-token");

    ProviderResult result = adapter.send(Channel.PUSH, "device-token-123", "Hello", "Welcome");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.FAILED);
    assertThat(result.providerName()).isEqualTo("FCM");
    assertThat(result.errorDetail()).isNotBlank();
  }
}
