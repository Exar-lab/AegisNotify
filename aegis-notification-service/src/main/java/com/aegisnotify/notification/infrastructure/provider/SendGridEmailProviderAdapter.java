package com.aegisnotify.notification.infrastructure.provider;

import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.application.dto.ProviderResult.Outcome;
import com.aegisnotify.notification.domain.enums.Channel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * SendGrid-based adapter for email delivery, called by {@link
 * NotificationProviderRouter} for {@link Channel#EMAIL}.
 *
 * <p>Calls SendGrid's v3 Mail Send API directly via {@link WebClient} rather
 * than a vendor SDK, matching this service's established WebClient-adapter
 * pattern. Never returns {@code SENT_VIA_FALLBACK} or {@code FAILED_CRITICAL}
 * — those outcomes belong to a resilience/failover layer, not to an
 * individual adapter.</p>
 */
public class SendGridEmailProviderAdapter {

  private static final Logger log = LoggerFactory.getLogger(SendGridEmailProviderAdapter.class);
  private static final String PROVIDER_NAME = "SendGrid";
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

  private final WebClient webClient;
  private final String apiKey;
  private final String fromAddress;
  private final ProviderMetrics metrics;

  public SendGridEmailProviderAdapter(WebClient webClient, String apiKey, String fromAddress,
      MeterRegistry meterRegistry) {
    this.webClient = webClient;
    this.apiKey = apiKey;
    this.fromAddress = fromAddress;
    this.metrics = new ProviderMetrics(meterRegistry);
  }

  public ProviderResult send(Channel channel, String recipient, String renderedContent,
      String subject) {
    SendGridMailRequest request = new SendGridMailRequest(
        List.of(new Personalization(List.of(new EmailAddress(recipient)))),
        new EmailAddress(fromAddress),
        subject,
        List.of(new Content("text/html", renderedContent))
    );

    Timer.Sample sample = metrics.startTimer();
    try {
      webClient.post()
          .uri("/v3/mail/send")
          .headers(headers -> headers.setBearerAuth(apiKey))
          .bodyValue(request)
          .retrieve()
          .toBodilessEntity()
          .timeout(REQUEST_TIMEOUT)
          .block();
      return new ProviderResult(Outcome.SENT, PROVIDER_NAME, null);
    } catch (WebClientResponseException ex) {
      log.warn("sendgrid_send_failed status={} body={}", ex.getStatusCode(),
          ex.getResponseBodyAsString());
      metrics.recordError(PROVIDER_NAME, String.valueOf(ex.getStatusCode().value()));
      return new ProviderResult(Outcome.FAILED, PROVIDER_NAME, ex.getMessage());
    } catch (Exception ex) {
      log.warn("sendgrid_send_failed error={}", ex.getMessage());
      metrics.recordError(PROVIDER_NAME, ex.getClass().getSimpleName());
      return new ProviderResult(Outcome.FAILED, PROVIDER_NAME, ex.getMessage());
    } finally {
      metrics.stopTimer(sample, PROVIDER_NAME);
    }
  }

  private record EmailAddress(String email) {
  }

  private record Personalization(List<EmailAddress> to) {
  }

  private record Content(String type, String value) {
  }

  private record SendGridMailRequest(
      List<Personalization> personalizations,
      EmailAddress from,
      String subject,
      List<Content> content) {
  }
}
