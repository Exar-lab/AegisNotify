package com.aegisnotify.notification.infrastructure.provider;

import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.application.dto.ProviderResult.Outcome;
import com.aegisnotify.notification.domain.enums.Channel;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Firebase Cloud Messaging (FCM) based adapter for push notifications,
 * called by {@link NotificationProviderRouter} for {@link Channel#PUSH}.
 *
 * <p>Calls FCM's HTTP v1 API directly via {@link WebClient}. The recipient is
 * treated as the target device token. Authentication uses a statically
 * configured access token; minting OAuth2 service-account tokens is out of
 * scope for this adapter — the configured token must be refreshed externally
 * before it expires (FCM v1 tokens are short-lived, typically ~1 hour).
 * Never returns {@code SENT_VIA_FALLBACK} or {@code FAILED_CRITICAL} — those
 * outcomes belong to a resilience/failover layer, not to an individual
 * adapter.</p>
 */
public class FirebasePushProviderAdapter {

  private static final Logger log = LoggerFactory.getLogger(FirebasePushProviderAdapter.class);
  private static final String PROVIDER_NAME = "FCM";
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

  private final WebClient webClient;
  private final String projectId;
  private final String accessToken;

  public FirebasePushProviderAdapter(WebClient webClient, String projectId,
      String accessToken) {
    this.webClient = webClient;
    this.projectId = projectId;
    this.accessToken = accessToken;
  }

  public ProviderResult send(Channel channel, String recipient, String renderedContent,
      String subject) {
    FcmSendRequest request = new FcmSendRequest(
        new FcmMessage(recipient, new FcmNotification(subject, renderedContent)));

    try {
      webClient.post()
          .uri("/v1/projects/{projectId}/messages:send", projectId)
          .headers(headers -> headers.setBearerAuth(accessToken))
          .bodyValue(request)
          .retrieve()
          .toBodilessEntity()
          .timeout(REQUEST_TIMEOUT)
          .block();
      return new ProviderResult(Outcome.SENT, PROVIDER_NAME, null);
    } catch (WebClientResponseException ex) {
      log.warn("fcm_send_failed status={} body={}", ex.getStatusCode(),
          ex.getResponseBodyAsString());
      return new ProviderResult(Outcome.FAILED, PROVIDER_NAME, ex.getMessage());
    } catch (Exception ex) {
      log.warn("fcm_send_failed error={}", ex.getMessage());
      return new ProviderResult(Outcome.FAILED, PROVIDER_NAME, ex.getMessage());
    }
  }

  private record FcmNotification(String title, String body) {
  }

  private record FcmMessage(String token, FcmNotification notification) {
  }

  private record FcmSendRequest(FcmMessage message) {
  }
}
