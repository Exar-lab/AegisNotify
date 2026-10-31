package com.aegisnotify.notification.infrastructure.provider;

import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.application.port.out.NotificationProviderPort;
import com.aegisnotify.notification.domain.enums.Channel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Twilio-based adapter for WhatsApp delivery, called by {@link
 * NotificationProviderRouter} for {@link Channel#WHATSAPP}.
 *
 * <p>Sends messages via Twilio's Messages REST API using HTTP Basic auth
 * (Account SID / Auth Token), delegating the actual request to the shared
 * {@link TwilioMessageClient}. Both the recipient and the configured sender
 * number are prefixed with {@code whatsapp:} per Twilio's convention for
 * routing messages over the WhatsApp channel. When a Content Template SID
 * ({@code HX...}) is configured, the rendered content is sent as that
 * template's single {@code {{1}}} variable via {@code ContentSid} /
 * {@code ContentVariables} instead of a free-form {@code Body}; a blank SID
 * keeps the plain-{@code Body} request. Never returns
 * {@code SENT_VIA_FALLBACK} or {@code FAILED_CRITICAL} — those outcomes
 * belong to a resilience/failover layer, not to an individual adapter.</p>
 */
public class TwilioWhatsAppProviderAdapter implements NotificationProviderPort {

  private static final String PROVIDER_NAME = "Twilio";
  private static final String WHATSAPP_PREFIX = "whatsapp:";
  private static final String TEMPLATE_VARIABLE_KEY = "1";

  private final TwilioMessageClient client;
  private final String fromNumber;
  private final String contentSid;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public TwilioWhatsAppProviderAdapter(WebClient webClient, String accountSid,
      String fromNumber, MeterRegistry meterRegistry) {
    this(webClient, accountSid, fromNumber, null, meterRegistry);
  }

  public TwilioWhatsAppProviderAdapter(WebClient webClient, String accountSid,
      String fromNumber, String contentSid, MeterRegistry meterRegistry) {
    this.client = new TwilioMessageClient(webClient, accountSid, PROVIDER_NAME, meterRegistry);
    this.fromNumber = fromNumber;
    this.contentSid = contentSid;
  }

  @Override
  public ProviderResult send(Channel channel, String recipient, String renderedContent,
      String subject) {
    String to = WHATSAPP_PREFIX + recipient;
    String from = WHATSAPP_PREFIX + fromNumber;
    if (contentSid == null || contentSid.isBlank()) {
      return client.send(to, from, renderedContent);
    }
    return client.sendTemplated(to, from, contentSid, toContentVariables(renderedContent));
  }

  private String toContentVariables(String renderedContent) {
    try {
      return objectMapper.writeValueAsString(Map.of(TEMPLATE_VARIABLE_KEY, renderedContent));
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize Twilio ContentVariables", ex);
    }
  }
}
