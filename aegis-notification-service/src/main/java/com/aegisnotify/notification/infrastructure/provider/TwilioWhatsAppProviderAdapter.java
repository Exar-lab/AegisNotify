package com.aegisnotify.notification.infrastructure.provider;

import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.domain.enums.Channel;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Twilio-based adapter for WhatsApp delivery, called by {@link
 * NotificationProviderRouter} for {@link Channel#WHATSAPP}.
 *
 * <p>Sends messages via Twilio's Messages REST API using HTTP Basic auth
 * (Account SID / Auth Token), delegating the actual request to the shared
 * {@link TwilioMessageClient}. Both the recipient and the configured sender
 * number are prefixed with {@code whatsapp:} per Twilio's convention for
 * routing messages over the WhatsApp channel. Never returns
 * {@code SENT_VIA_FALLBACK} or {@code FAILED_CRITICAL} — those outcomes
 * belong to a resilience/failover layer, not to an individual adapter.</p>
 */
public class TwilioWhatsAppProviderAdapter {

  private static final String PROVIDER_NAME = "Twilio";
  private static final String WHATSAPP_PREFIX = "whatsapp:";

  private final TwilioMessageClient client;
  private final String fromNumber;

  public TwilioWhatsAppProviderAdapter(WebClient webClient, String accountSid,
      String fromNumber) {
    this.client = new TwilioMessageClient(webClient, accountSid, PROVIDER_NAME);
    this.fromNumber = fromNumber;
  }

  public ProviderResult send(Channel channel, String recipient, String renderedContent,
      String subject) {
    return client.send(WHATSAPP_PREFIX + recipient, WHATSAPP_PREFIX + fromNumber,
        renderedContent);
  }
}
