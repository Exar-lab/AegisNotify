package com.aegisnotify.notification.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.domain.enums.Channel;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class TwilioWhatsAppProviderAdapterTest {

  @Test
  void sendReturnsSentOnSuccessfulResponse() {
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> Mono.just(
            ClientResponse.create(HttpStatus.CREATED).build()))
        .build();

    TwilioWhatsAppProviderAdapter adapter =
        new TwilioWhatsAppProviderAdapter(webClient, "AC-test-sid", "+34600000001");

    ProviderResult result = adapter.send(Channel.WHATSAPP, "+34600000002", "Hello", "Welcome");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.SENT);
    assertThat(result.providerName()).isEqualTo("Twilio");
    assertThat(result.errorDetail()).isNull();
  }

  @Test
  void sendPrefixesToAndFromWithWhatsAppScheme() {
    AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> {
          capturedRequest.set(request);
          return Mono.just(ClientResponse.create(HttpStatus.CREATED).build());
        })
        .build();

    TwilioWhatsAppProviderAdapter adapter =
        new TwilioWhatsAppProviderAdapter(webClient, "AC-test-sid", "+34600000001");

    adapter.send(Channel.WHATSAPP, "+34600000002", "Hello", "Welcome");

    assertThat(capturedRequest.get().url().toString())
        .contains("/2010-04-01/Accounts/AC-test-sid/Messages.json");
    String body = readBody(capturedRequest.get());
    assertThat(body).contains("To=whatsapp%3A%2B34600000002");
    assertThat(body).contains("From=whatsapp%3A%2B34600000001");
  }

  private static String readBody(ClientRequest request) {
    MockClientHttpRequest httpRequest = new MockClientHttpRequest(HttpMethod.POST, "/test");
    BodyInserter.Context context = new BodyInserter.Context() {
      @Override
      public List<HttpMessageWriter<?>> messageWriters() {
        return ExchangeStrategies.withDefaults().messageWriters();
      }

      @Override
      public Optional<ServerHttpRequest> serverRequest() {
        return Optional.empty();
      }

      @Override
      public Map<String, Object> hints() {
        return Collections.emptyMap();
      }
    };
    request.body().insert(httpRequest, context).block();
    return httpRequest.getBodyAsString().block();
  }

  @Test
  void sendReturnsFailedOnErrorResponse() {
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> Mono.just(
            ClientResponse.create(HttpStatus.UNAUTHORIZED)
                .body("{\"message\":\"invalid credentials\"}")
                .build()))
        .build();

    TwilioWhatsAppProviderAdapter adapter =
        new TwilioWhatsAppProviderAdapter(webClient, "AC-test-sid", "+34600000001");

    ProviderResult result = adapter.send(Channel.WHATSAPP, "+34600000002", "Hello", "Welcome");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.FAILED);
    assertThat(result.providerName()).isEqualTo("Twilio");
    assertThat(result.errorDetail()).isNotBlank();
  }
}
