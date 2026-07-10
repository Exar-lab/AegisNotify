package com.aegisnotify.notification.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.domain.enums.Channel;
import org.junit.jupiter.api.Test;
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
        new TwilioSmsProviderAdapter(webClient, "AC-test-sid", "+34600000001");

    ProviderResult result = adapter.send(Channel.SMS, "+34600000002", "Hello", "Welcome");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.SENT);
    assertThat(result.providerName()).isEqualTo("Twilio");
    assertThat(result.errorDetail()).isNull();
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
        new TwilioSmsProviderAdapter(webClient, "AC-test-sid", "+34600000001");

    ProviderResult result = adapter.send(Channel.SMS, "+34600000002", "Hello", "Welcome");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.FAILED);
    assertThat(result.providerName()).isEqualTo("Twilio");
    assertThat(result.errorDetail()).isNotBlank();
  }
}
