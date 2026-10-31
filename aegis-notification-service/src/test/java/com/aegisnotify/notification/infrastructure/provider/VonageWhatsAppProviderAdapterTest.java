package com.aegisnotify.notification.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.domain.enums.Channel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class VonageWhatsAppProviderAdapterTest {

  private static final String SANDBOX_NUMBER = "14157386102";

  @Test
  void sendReturnsSentOnAcceptedResponse() {
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> Mono.just(
            ClientResponse.create(HttpStatus.ACCEPTED)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"message_uuid\":\"aaaaaaaa-bbbb-cccc-dddd-0123456789ab\"}")
                .build()))
        .build();
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    VonageWhatsAppProviderAdapter adapter =
        new VonageWhatsAppProviderAdapter(webClient, SANDBOX_NUMBER, meterRegistry);

    ProviderResult result = adapter.send(Channel.WHATSAPP, "+50661480220", "Hello", "Welcome");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.SENT);
    assertThat(result.providerName()).isEqualTo("Vonage");
    assertThat(result.errorDetail()).isNull();
    assertThat(result.retryable()).isFalse();
    assertThat(meterRegistry.find("aegisnotify.delivery.latency.seconds")
        .tag("provider", "Vonage").timer().count()).isEqualTo(1L);
  }

  @Test
  void sendPostsWhatsAppTextMessageToMessagesEndpointStrippingLeadingPlus() throws Exception {
    AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
    VonageWhatsAppProviderAdapter adapter = new VonageWhatsAppProviderAdapter(
        capturingWebClient(capturedRequest), SANDBOX_NUMBER, new SimpleMeterRegistry());

    adapter.send(Channel.WHATSAPP, "+50661480220", "Your code is 1234", "Welcome");

    ClientRequest request = capturedRequest.get();
    assertThat(request.method()).isEqualTo(HttpMethod.POST);
    assertThat(request.url().getPath()).isEqualTo("/v1/messages");
    Map<String, String> body = parseJson(readBody(request));
    assertThat(body).containsExactlyInAnyOrderEntriesOf(Map.of(
        "from", SANDBOX_NUMBER,
        "to", "50661480220",
        "message_type", "text",
        "text", "Your code is 1234",
        "channel", "whatsapp"));
  }

  @Test
  void sendKeepsPlainDigitRecipientUnchanged() throws Exception {
    AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
    VonageWhatsAppProviderAdapter adapter = new VonageWhatsAppProviderAdapter(
        capturingWebClient(capturedRequest), SANDBOX_NUMBER, new SimpleMeterRegistry());

    adapter.send(Channel.WHATSAPP, "50661480220", "Hello", "Welcome");

    assertThat(parseJson(readBody(capturedRequest.get())).get("to")).isEqualTo("50661480220");
  }

  @Test
  void sendStripsOnlyTheLeadingPlus() throws Exception {
    AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
    VonageWhatsAppProviderAdapter adapter = new VonageWhatsAppProviderAdapter(
        capturingWebClient(capturedRequest), SANDBOX_NUMBER, new SimpleMeterRegistry());

    adapter.send(Channel.WHATSAPP, "+0034600000002", "Hello", "Welcome");

    assertThat(parseJson(readBody(capturedRequest.get())).get("to"))
        .isEqualTo("0034600000002");
  }

  @Test
  void sendCarriesBasicAuthHeaderFromConfiguredWebClient() {
    AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
    WebClient webClient = WebClient.builder()
        .defaultHeaders(headers -> headers.setBasicAuth("vonage-key", "vonage-secret"))
        .exchangeFunction(request -> {
          capturedRequest.set(request);
          return Mono.just(ClientResponse.create(HttpStatus.ACCEPTED).build());
        })
        .build();

    new VonageWhatsAppProviderAdapter(webClient, SANDBOX_NUMBER, new SimpleMeterRegistry())
        .send(Channel.WHATSAPP, "+50661480220", "Hello", "Welcome");

    String expected = "Basic " + Base64.getEncoder()
        .encodeToString("vonage-key:vonage-secret".getBytes(StandardCharsets.UTF_8));
    assertThat(capturedRequest.get().headers().getFirst(HttpHeaders.AUTHORIZATION))
        .isEqualTo(expected);
    assertThat(capturedRequest.get().headers().getContentType())
        .isEqualTo(MediaType.APPLICATION_JSON);
  }

  @Test
  void sendReturnsFailedOnErrorResponse() {
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> Mono.just(
            ClientResponse.create(HttpStatus.UNAUTHORIZED)
                .body("{\"title\":\"Unauthorized\"}")
                .build()))
        .build();
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    VonageWhatsAppProviderAdapter adapter =
        new VonageWhatsAppProviderAdapter(webClient, SANDBOX_NUMBER, meterRegistry);

    ProviderResult result = adapter.send(Channel.WHATSAPP, "+50661480220", "Hello", "Welcome");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.FAILED);
    assertThat(result.providerName()).isEqualTo("Vonage");
    assertThat(result.errorDetail()).isNotBlank();
    assertThat(result.retryable()).isFalse();
    assertThat(meterRegistry.find("aegisnotify.provider.error.count")
        .tags("provider", "Vonage", "error_code", "401")
        .counter().count()).isEqualTo(1.0d);
    assertThat(meterRegistry.find("aegisnotify.delivery.latency.seconds")
        .tag("provider", "Vonage").timer().count()).isEqualTo(1L);
  }

  @ParameterizedTest
  @CsvSource({
      "TOO_MANY_REQUESTS, true",
      "SERVICE_UNAVAILABLE, true",
      "INTERNAL_SERVER_ERROR, true",
      "BAD_REQUEST, false",
      "UNAUTHORIZED, false",
      "UNPROCESSABLE_ENTITY, false"
  })
  void sendClassifiesRetryableByHttpStatus(HttpStatus status, boolean expectedRetryable) {
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> Mono.just(
            ClientResponse.create(status).body("{}").build()))
        .build();

    VonageWhatsAppProviderAdapter adapter = new VonageWhatsAppProviderAdapter(
        webClient, SANDBOX_NUMBER, new SimpleMeterRegistry());

    ProviderResult result = adapter.send(Channel.WHATSAPP, "+50661480220", "Hello", "Welcome");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.FAILED);
    assertThat(result.retryable()).isEqualTo(expectedRetryable);
    assertThat(result.code()).isEqualTo(String.valueOf(status.value()));
  }

  @Test
  void sendClassifiesTimeoutAsRetryable() {
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> Mono.error(
            new RuntimeException("simulated timeout", new TimeoutException("no response"))))
        .build();

    VonageWhatsAppProviderAdapter adapter = new VonageWhatsAppProviderAdapter(
        webClient, SANDBOX_NUMBER, new SimpleMeterRegistry());

    ProviderResult result = adapter.send(Channel.WHATSAPP, "+50661480220", "Hello", "Welcome");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.FAILED);
    assertThat(result.retryable()).isTrue();
  }

  @Test
  void sendClassifiesConnectionFailureAsRetryable() {
    WebClient webClient = WebClient.builder()
        .exchangeFunction(request -> Mono.error(new IOException("connection refused")))
        .build();

    VonageWhatsAppProviderAdapter adapter = new VonageWhatsAppProviderAdapter(
        webClient, SANDBOX_NUMBER, new SimpleMeterRegistry());

    ProviderResult result = adapter.send(Channel.WHATSAPP, "+50661480220", "Hello", "Welcome");

    assertThat(result.outcome()).isEqualTo(ProviderResult.Outcome.FAILED);
    assertThat(result.retryable()).isTrue();
  }

  private static WebClient capturingWebClient(AtomicReference<ClientRequest> capturedRequest) {
    return WebClient.builder()
        .exchangeFunction(request -> {
          capturedRequest.set(request);
          return Mono.just(ClientResponse.create(HttpStatus.ACCEPTED).build());
        })
        .build();
  }

  private static Map<String, String> parseJson(String body) throws Exception {
    return new ObjectMapper().readValue(body, new TypeReference<Map<String, String>>() {});
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
