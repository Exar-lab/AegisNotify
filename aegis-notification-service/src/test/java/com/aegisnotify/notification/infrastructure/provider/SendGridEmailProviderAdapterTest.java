package com.aegisnotify.notification.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.domain.enums.Channel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class SendGridEmailProviderAdapterTest {

  @Test
  void sendReturnsSentOnSuccessfulResponse() {
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> Mono.just(
            ClientResponse.create(HttpStatus.ACCEPTED).build()))
        .build();
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    SendGridEmailProviderAdapter adapter = new SendGridEmailProviderAdapter(
        webClient, "test-api-key", "noreply@aegisnotify.com", meterRegistry);

    ProviderResult result = adapter.send(Channel.EMAIL, "user@example.com", "<p>Hello</p>",
        "Welcome");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.SENT);
    assertThat(result.providerName()).isEqualTo("SendGrid");
    assertThat(result.errorDetail()).isNull();
    assertThat(meterRegistry.find("aegisnotify.delivery.latency.seconds")
        .tag("provider", "SendGrid").timer().count()).isEqualTo(1L);
  }

  @Test
  void sendReturnsFailedOnErrorResponse() {
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> Mono.just(
            ClientResponse.create(HttpStatus.UNAUTHORIZED)
                .body("{\"errors\":[{\"message\":\"invalid api key\"}]}")
                .build()))
        .build();
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    SendGridEmailProviderAdapter adapter = new SendGridEmailProviderAdapter(
        webClient, "bad-api-key", "noreply@aegisnotify.com", meterRegistry);

    ProviderResult result = adapter.send(Channel.EMAIL, "user@example.com", "<p>Hello</p>",
        "Welcome");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.FAILED);
    assertThat(result.providerName()).isEqualTo("SendGrid");
    assertThat(result.errorDetail()).isNotBlank();
    assertThat(meterRegistry.find("aegisnotify.provider.error.count")
        .tags("provider", "SendGrid", "error_code", "401")
        .counter().count()).isEqualTo(1.0d);
    assertThat(meterRegistry.find("aegisnotify.delivery.latency.seconds")
        .tag("provider", "SendGrid").timer().count()).isEqualTo(1L);
  }
}
