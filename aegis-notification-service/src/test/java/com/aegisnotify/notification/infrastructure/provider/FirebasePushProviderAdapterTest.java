package com.aegisnotify.notification.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.domain.enums.Channel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    FirebasePushProviderAdapter adapter = new FirebasePushProviderAdapter(
        webClient, "aegis-project", "test-access-token", new SimpleMeterRegistry());

    ProviderResult result = adapter.send(Channel.PUSH, "device-token-123", "Hello", "Welcome");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.SENT);
    assertThat(result.providerName()).isEqualTo("FCM");
    assertThat(result.errorDetail()).isNull();
    assertThat(result.retryable()).isFalse();
  }

  @Test
  void sendReturnsFailedOnErrorResponse() {
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> Mono.just(
            ClientResponse.create(HttpStatus.BAD_REQUEST)
                .body("{\"error\":{\"message\":\"invalid token\"}}")
                .build()))
        .build();

    FirebasePushProviderAdapter adapter = new FirebasePushProviderAdapter(
        webClient, "aegis-project", "test-access-token", new SimpleMeterRegistry());

    ProviderResult result = adapter.send(Channel.PUSH, "device-token-123", "Hello", "Welcome");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.FAILED);
    assertThat(result.providerName()).isEqualTo("FCM");
    assertThat(result.errorDetail()).isNotBlank();
    assertThat(result.retryable()).isFalse();
  }

  @ParameterizedTest
  @CsvSource({
      "TOO_MANY_REQUESTS, true",
      "SERVICE_UNAVAILABLE, true",
      "BAD_REQUEST, false",
      "UNAUTHORIZED, false",
      "UNPROCESSABLE_ENTITY, false"
  })
  void sendClassifiesRetryableByHttpStatus(HttpStatus status, boolean expectedRetryable) {
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> Mono.just(
            ClientResponse.create(status).body("{}").build()))
        .build();

    FirebasePushProviderAdapter adapter = new FirebasePushProviderAdapter(
        webClient, "aegis-project", "test-access-token", new SimpleMeterRegistry());

    ProviderResult result = adapter.send(Channel.PUSH, "device-token-123", "Hello", "Welcome");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.FAILED);
    assertThat(result.retryable()).isEqualTo(expectedRetryable);
  }

  @Test
  void sendClassifiesTimeoutAsRetryable() {
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> Mono.error(
            new RuntimeException("simulated timeout", new TimeoutException("no response"))))
        .build();

    FirebasePushProviderAdapter adapter = new FirebasePushProviderAdapter(
        webClient, "aegis-project", "test-access-token", new SimpleMeterRegistry());

    ProviderResult result = adapter.send(Channel.PUSH, "device-token-123", "Hello", "Welcome");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.FAILED);
    assertThat(result.retryable()).isTrue();
  }

  @Test
  void sendClassifiesConnectionFailureAsRetryable() {
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> Mono.error(
            new IOException("connection refused")))
        .build();

    FirebasePushProviderAdapter adapter = new FirebasePushProviderAdapter(
        webClient, "aegis-project", "test-access-token", new SimpleMeterRegistry());

    ProviderResult result = adapter.send(Channel.PUSH, "device-token-123", "Hello", "Welcome");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.FAILED);
    assertThat(result.retryable()).isTrue();
  }
}
