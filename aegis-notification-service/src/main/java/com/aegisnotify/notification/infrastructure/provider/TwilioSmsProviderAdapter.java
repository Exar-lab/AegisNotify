package com.aegisnotify.notification.infrastructure.provider;

import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.domain.enums.Channel;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Twilio-based adapter for SMS delivery, called by {@link
 * NotificationProviderRouter} for {@link Channel#SMS}.
 *
 * <p>Sends messages via Twilio's Messages REST API using HTTP Basic auth
 * (Account SID / Auth Token), delegating the actual request to the shared
 * {@link TwilioMessageClient}. Never returns {@code SENT_VIA_FALLBACK} or
 * {@code FAILED_CRITICAL} — those outcomes belong to a resilience/failover
 * layer, not to an individual adapter.</p>
 */
public class TwilioSmsProviderAdapter {

  private static final String PROVIDER_NAME = "Twilio";

  private final TwilioMessageClient client;
  private final String fromNumber;

  public TwilioSmsProviderAdapter(WebClient webClient, String accountSid, String fromNumber) {
    this.client = new TwilioMessageClient(webClient, accountSid, PROVIDER_NAME);
    this.fromNumber = fromNumber;
  }

  public ProviderResult send(Channel channel, String recipient, String renderedContent,
      String subject) {
    return client.send(recipient, fromNumber, renderedContent);
  }
}
