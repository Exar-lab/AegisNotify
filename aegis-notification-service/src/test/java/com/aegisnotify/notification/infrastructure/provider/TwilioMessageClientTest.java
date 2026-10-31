package com.aegisnotify.notification.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.notification.application.dto.ProviderResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

class TwilioMessageClientTest {

  @Test
  void sendTemplatedReturnsSentOnSuccessfulResponse() {
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> Mono.just(
            ClientResponse.create(HttpStatus.CREATED).build()))
        .build();

    TwilioMessageClient client = newClient(webClient);

    ProviderResult result = client.sendTemplated("whatsapp:+34600000002",
        "whatsapp:+34600000001", "HX-test-sid", "{\"1\":\"Hello\"}");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.SENT);
    assertThat(result.providerName()).isEqualTo("Twilio");
    assertThat(result.errorDetail()).isNull();
    assertThat(result.retryable()).isFalse();
    assertThat(result.code()).isEqualTo("201");
  }

  @Test
  void sendTemplatedPostsContentSidAndContentVariablesInsteadOfBody() {
    AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> {
          capturedRequest.set(request);
          return Mono.just(ClientResponse.create(HttpStatus.CREATED).build());
        })
        .build();

    newClient(webClient).sendTemplated("whatsapp:+34600000002", "whatsapp:+34600000001",
        "HX-test-sid", "{\"1\":\"Hello\"}");

    assertThat(capturedRequest.get().url().toString())
        .contains("/2010-04-01/Accounts/AC-test-sid/Messages.json");
    String body = URLDecoder.decode(readBody(capturedRequest.get()), StandardCharsets.UTF_8);
    assertThat(body).contains("To=whatsapp:+34600000002");
    assertThat(body).contains("From=whatsapp:+34600000001");
    assertThat(body).contains("ContentSid=HX-test-sid");
    assertThat(body).contains("ContentVariables={\"1\":\"Hello\"}");
    assertThat(body).doesNotContain("Body=");
  }

  @Test
  void sendTemplatedOmitsContentVariablesWhenBlank() {
    AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> {
          capturedRequest.set(request);
          return Mono.just(ClientResponse.create(HttpStatus.CREATED).build());
        })
        .build();

    newClient(webClient).sendTemplated("whatsapp:+34600000002", "whatsapp:+34600000001",
        "HX-test-sid", " ");

    String body = readBody(capturedRequest.get());
    assertThat(body).contains("ContentSid=HX-test-sid");
    assertThat(body).doesNotContain("ContentVariables");
  }

  @Test
  void sendTemplatedReturnsFailedOnContentSidRequiredError() {
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> Mono.just(
            ClientResponse.create(HttpStatus.BAD_REQUEST)
                .body("{\"code\":21654,\"message\":\"ContentSid Required\"}")
                .build()))
        .build();

    ProviderResult result = newClient(webClient).sendTemplated("whatsapp:+34600000002",
        "whatsapp:+34600000001", "HX-test-sid", "{\"1\":\"Hello\"}");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.FAILED);
    assertThat(result.providerName()).isEqualTo("Twilio");
    assertThat(result.errorDetail()).isNotBlank();
    assertThat(result.retryable()).isFalse();
    assertThat(result.code()).isEqualTo("400");
  }

  @Test
  void sendTemplatedReturnsFailedOnServerError() {
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> Mono.just(
            ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"message\":\"twilio outage\"}")
                .build()))
        .build();

    ProviderResult result = newClient(webClient).sendTemplated("whatsapp:+34600000002",
        "whatsapp:+34600000001", "HX-test-sid", "{\"1\":\"Hello\"}");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.FAILED);
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
  void sendTemplatedClassifiesRetryableByHttpStatus(HttpStatus status,
      boolean expectedRetryable) {
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> Mono.just(
            ClientResponse.create(status).body("{}").build()))
        .build();

    ProviderResult result = newClient(webClient).sendTemplated("whatsapp:+34600000002",
        "whatsapp:+34600000001", "HX-test-sid", "{\"1\":\"Hello\"}");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.FAILED);
    assertThat(result.retryable()).isEqualTo(expectedRetryable);
  }

  @Test
  void sendTemplatedClassifiesConnectionFailureAsRetryable() {
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> Mono.error(new IOException("connection refused")))
        .build();

    ProviderResult result = newClient(webClient).sendTemplated("whatsapp:+34600000002",
        "whatsapp:+34600000001", "HX-test-sid", "{\"1\":\"Hello\"}");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.FAILED);
    assertThat(result.retryable()).isTrue();
  }

  @Test
  void sendPostsBodyWithoutContentSid() {
    AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> {
          capturedRequest.set(request);
          return Mono.just(ClientResponse.create(HttpStatus.CREATED).build());
        })
        .build();

    newClient(webClient).send("+34600000002", "+34600000001", "Hello");

    String body = readBody(capturedRequest.get());
    assertThat(body).contains("Body=Hello");
    assertThat(body).doesNotContain("ContentSid");
    assertThat(body).doesNotContain("ContentVariables");
  }

  private static TwilioMessageClient newClient(WebClient webClient) {
    return new TwilioMessageClient(webClient, "AC-test-sid", "Twilio",
        new SimpleMeterRegistry());
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
}
