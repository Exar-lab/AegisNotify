package com.aegisnotify.notification.infrastructure.provider;

import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.application.dto.ProviderResult.Outcome;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Shared HTTP client for Bearer-token-authenticated email send APIs, used by
 * both the SendGrid and Mailtrap provider adapters so the request/response
 * handling logic is not duplicated across the two vendors.
 *
 * <p>Each caller supplies its own {@link WebClient} (already configured with
 * the vendor's base URL) and a provider name used to build the resulting
 * {@link ProviderResult}. Only maps to {@code SENT} or {@code FAILED}
 * outcomes — fallback and critical-failure semantics belong to a
 * higher-level resilience layer, not to this adapter.</p>
 */
class EmailMessageClient {

  private static final Logger log = LoggerFactory.getLogger(EmailMessageClient.class);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

  private final WebClient webClient;
  private final String apiKey;
  private final String providerName;
  private final ProviderMetrics metrics;

  EmailMessageClient(WebClient webClient, String apiKey, String providerName,
      MeterRegistry meterRegistry) {
    this.webClient = webClient;
    this.apiKey = apiKey;
    this.providerName = providerName;
    this.metrics = new ProviderMetrics(meterRegistry);
  }

  ProviderResult send(String uri, Object requestBody) {
    Timer.Sample sample = metrics.startTimer();
    try {
      var response = webClient.post()
          .uri(uri)
          .headers(headers -> headers.setBearerAuth(apiKey))
          .bodyValue(requestBody)
          .retrieve()
          .toBodilessEntity()
          .timeout(REQUEST_TIMEOUT)
          .block();
      return new ProviderResult(Outcome.SENT, providerName, null).withCode(
          String.valueOf(response.getStatusCode().value()));
    } catch (WebClientResponseException ex) {
      log.warn("email_send_failed provider={} status={} body={}", providerName,
          ex.getStatusCode(), ex.getResponseBodyAsString());
      metrics.recordError(providerName, String.valueOf(ex.getStatusCode().value()));
      return new ProviderResult(Outcome.FAILED, providerName, ex.getMessage(),
          RetryClassification.isRetryable(ex.getStatusCode()))
          .withCode(String.valueOf(ex.getStatusCode().value()));
    } catch (Exception ex) {
      log.warn("email_send_failed provider={} error={}", providerName, ex.getMessage());
      metrics.recordError(providerName, ex.getClass().getSimpleName());
      return new ProviderResult(Outcome.FAILED, providerName, ex.getMessage(),
          RetryClassification.isRetryable(ex)).withCode(ex.getClass().getSimpleName());
    } finally {
      metrics.stopTimer(sample, providerName);
    }
  }
}
