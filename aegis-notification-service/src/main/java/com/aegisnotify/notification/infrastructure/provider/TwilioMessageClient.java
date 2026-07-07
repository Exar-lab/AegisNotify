package com.aegisnotify.notification.infrastructure.provider;

import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.application.dto.ProviderResult.Outcome;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Shared HTTP client for Twilio's Messages REST API, used by both the SMS
 * and WhatsApp provider adapters so the request/response handling logic is
 * not duplicated across the two channels.
 *
 * <p>Each caller supplies its own {@link WebClient} (already configured with
 * the Twilio base URL and Basic auth credentials) and a provider name used to
 * build the resulting {@link ProviderResult}. Only maps to {@code SENT} or
 * {@code FAILED} outcomes — fallback and critical-failure semantics belong
 * to a higher-level resilience layer, not to this adapter.</p>
 */
class TwilioMessageClient {

  private static final Logger log = LoggerFactory.getLogger(TwilioMessageClient.class);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

  private final WebClient webClient;
  private final String accountSid;
  private final String providerName;

  TwilioMessageClient(WebClient webClient, String accountSid, String providerName) {
    this.webClient = webClient;
    this.accountSid = accountSid;
    this.providerName = providerName;
  }

  ProviderResult send(String to, String from, String body) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("To", to);
    form.add("From", from);
    form.add("Body", body);

    try {
      webClient.post()
          .uri("/2010-04-01/Accounts/{accountSid}/Messages.json", accountSid)
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .bodyValue(form)
          .retrieve()
          .toBodilessEntity()
          .timeout(REQUEST_TIMEOUT)
          .block();
      return new ProviderResult(Outcome.SENT, providerName, null);
    } catch (WebClientResponseException ex) {
      log.warn("twilio_send_failed provider={} status={} body={}", providerName,
          ex.getStatusCode(), ex.getResponseBodyAsString());
      return new ProviderResult(Outcome.FAILED, providerName, ex.getMessage());
    } catch (Exception ex) {
      log.warn("twilio_send_failed provider={} error={}", providerName, ex.getMessage());
      return new ProviderResult(Outcome.FAILED, providerName, ex.getMessage());
    }
  }
}
