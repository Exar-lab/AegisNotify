package com.aegisnotify.notification.infrastructure.provider;

import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.application.port.out.NotificationProviderPort;
import com.aegisnotify.notification.domain.enums.Channel;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * SendGrid-based adapter for email delivery, called by {@link
 * NotificationProviderRouter} for {@link Channel#EMAIL}.
 *
 * <p>Calls SendGrid's v3 Mail Send API via the shared {@link
 * EmailMessageClient} rather than a vendor SDK, matching this service's
 * established WebClient-adapter pattern. Never returns {@code
 * SENT_VIA_FALLBACK} or {@code FAILED_CRITICAL} — those outcomes belong to a
 * resilience/failover layer, not to an individual adapter.</p>
 */
public class SendGridEmailProviderAdapter implements NotificationProviderPort {

  private static final String PROVIDER_NAME = "SendGrid";

  private final EmailMessageClient client;
  private final String fromAddress;

  public SendGridEmailProviderAdapter(WebClient webClient, String apiKey, String fromAddress,
      MeterRegistry meterRegistry) {
    this.client = new EmailMessageClient(webClient, apiKey, PROVIDER_NAME, meterRegistry);
    this.fromAddress = fromAddress;
  }

  @Override
  public ProviderResult send(Channel channel, String recipient, String renderedContent,
      String subject) {
    SendGridMailRequest request = new SendGridMailRequest(
        List.of(new Personalization(List.of(new EmailAddress(recipient)))),
        new EmailAddress(fromAddress),
        subject,
        List.of(new Content("text/html", renderedContent))
    );
    return client.send("/v3/mail/send", request);
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
