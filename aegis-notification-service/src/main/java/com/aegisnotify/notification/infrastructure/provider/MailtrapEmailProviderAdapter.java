package com.aegisnotify.notification.infrastructure.provider;

import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.application.port.out.NotificationProviderPort;
import com.aegisnotify.notification.domain.enums.Channel;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Mailtrap-based adapter for email delivery, called by {@link
 * NotificationProviderRouter} for {@link Channel#EMAIL}.
 *
 * <p>Calls Mailtrap's Send API via the shared {@link EmailMessageClient}
 * rather than a vendor SDK, matching this service's established
 * WebClient-adapter pattern. Never returns {@code SENT_VIA_FALLBACK} or
 * {@code FAILED_CRITICAL} — those outcomes belong to a resilience/failover
 * layer, not to an individual adapter.</p>
 */
public class MailtrapEmailProviderAdapter implements NotificationProviderPort {

  private static final String PROVIDER_NAME = "Mailtrap";

  private final EmailMessageClient client;
  private final String fromAddress;

  public MailtrapEmailProviderAdapter(WebClient webClient, String apiKey, String fromAddress,
      MeterRegistry meterRegistry) {
    this.client = new EmailMessageClient(webClient, apiKey, PROVIDER_NAME, meterRegistry);
    this.fromAddress = fromAddress;
  }

  @Override
  public ProviderResult send(Channel channel, String recipient, String renderedContent,
      String subject) {
    MailtrapMailRequest request = new MailtrapMailRequest(
        new EmailAddress(fromAddress),
        List.of(new EmailAddress(recipient)),
        subject,
        renderedContent
    );
    return client.send("/api/send", request);
  }

  private record EmailAddress(String email) {
  }

  private record MailtrapMailRequest(
      EmailAddress from,
      List<EmailAddress> to,
      String subject,
      String html) {
  }
}
