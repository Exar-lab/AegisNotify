package com.aegisnotify.notification.infrastructure.provider;

import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.application.port.out.NotificationProviderPort;
import com.aegisnotify.notification.domain.enums.Channel;
import java.util.EnumMap;
import java.util.Map;

/**
 * Dispatches notification delivery to the channel-specific provider adapter.
 *
 * <p>This is the sole implementation of {@link NotificationProviderPort} —
 * the concrete channel adapters ({@code SendGridEmailProviderAdapter},
 * {@code TwilioSmsProviderAdapter}, {@code TwilioWhatsAppProviderAdapter},
 * {@code FirebasePushProviderAdapter}) are plain classes injected here by
 * concrete type and indexed by {@link Channel} for dispatch, so there is no
 * ambiguity for Spring to resolve wherever {@link NotificationProviderPort}
 * itself is injected (e.g. in {@code ProcessNotificationService}).</p>
 */
public class NotificationProviderRouter implements NotificationProviderPort {

  private final Map<Channel, ChannelSender> adaptersByChannel;

  public NotificationProviderRouter(
      SendGridEmailProviderAdapter emailAdapter,
      TwilioSmsProviderAdapter smsAdapter,
      TwilioWhatsAppProviderAdapter whatsAppAdapter,
      FirebasePushProviderAdapter pushAdapter) {
    this.adaptersByChannel = new EnumMap<>(Channel.class);
    adaptersByChannel.put(Channel.EMAIL, emailAdapter::send);
    adaptersByChannel.put(Channel.SMS, smsAdapter::send);
    adaptersByChannel.put(Channel.WHATSAPP, whatsAppAdapter::send);
    adaptersByChannel.put(Channel.PUSH, pushAdapter::send);
  }

  @Override
  public ProviderResult send(Channel channel, String recipient, String renderedContent,
      String subject) {
    ChannelSender adapter = adaptersByChannel.get(channel);
    if (adapter == null) {
      throw new IllegalStateException("No provider adapter configured for channel: " + channel);
    }
    return adapter.send(channel, recipient, renderedContent, subject);
  }

  @FunctionalInterface
  private interface ChannelSender {
    ProviderResult send(Channel channel, String recipient, String renderedContent,
        String subject);
  }
}
