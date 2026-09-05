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

class TwilioSmsProviderAdapterTest {

  @Test
  void sendReturnsSentOnSuccessfulResponse() {
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> Mono.just(
            ClientResponse.create(HttpStatus.CREATED).build()))
        .build();

    TwilioSmsProviderAdapter adapter =
        new TwilioSmsProviderAdapter(webClient, "AC-test-sid", "+34600000001",
            new SimpleMeterRegistry());

    ProviderResult result = adapter.send(Channel.SMS, "+34600000002", "Hello", "Welcome");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.SENT);
    assertThat(result.providerName()).isEqualTo("Twilio");
    assertThat(result.errorDetail()).isNull();
    assertThat(result.retryable()).isFalse();
  }

  @Test
  void sendReturnsFailedOnErrorResponse() {
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> Mono.just(
            ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"message\":\"twilio outage\"}")
                .build()))
        .build();

    TwilioSmsProviderAdapter adapter =
        new TwilioSmsProviderAdapter(webClient, "AC-test-sid", "+34600000001",
            new SimpleMeterRegistry());

    ProviderResult result = adapter.send(Channel.SMS, "+34600000002", "Hello", "Welcome");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.FAILED);
    assertThat(result.providerName()).isEqualTo("Twilio");
    assertThat(result.errorDetail()).isNotBlank();
    assertThat(result.retryable()).isTrue();
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

    TwilioSmsProviderAdapter adapter =
        new TwilioSmsProviderAdapter(webClient, "AC-test-sid", "+34600000001",
            new SimpleMeterRegistry());

    ProviderResult result = adapter.send(Channel.SMS, "+34600000002", "Hello", "Welcome");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.FAILED);
    assertThat(result.retryable()).isEqualTo(expectedRetryable);
  }

  @Test
  void sendClassifiesTimeoutAsRetryable() {
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> Mono.error(
            new RuntimeException("simulated timeout", new TimeoutException("no response"))))
        .build();

    TwilioSmsProviderAdapter adapter =
        new TwilioSmsProviderAdapter(webClient, "AC-test-sid", "+34600000001",
            new SimpleMeterRegistry());

    ProviderResult result = adapter.send(Channel.SMS, "+34600000002", "Hello", "Welcome");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.FAILED);
    assertThat(result.retryable()).isTrue();
  }

  @Test
  void sendClassifiesConnectionFailureAsRetryable() {
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> Mono.error(
            new IOException("connection refused")))
        .build();

    TwilioSmsProviderAdapter adapter =
        new TwilioSmsProviderAdapter(webClient, "AC-test-sid", "+34600000001",
            new SimpleMeterRegistry());

    ProviderResult result = adapter.send(Channel.SMS, "+34600000002", "Hello", "Welcome");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.FAILED);
    assertThat(result.retryable()).isTrue();
  }
}
