package com.aegisnotify.notification.infrastructure.provider;

import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.application.dto.ProviderResult.Outcome;
import com.aegisnotify.notification.application.port.out.NotificationProviderPort;
import com.aegisnotify.notification.domain.enums.Channel;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sole {@link NotificationProviderPort} implementation. Wraps the primary
 * {@link NotificationProviderRouter} with a per-channel Resilience4j
 * {@link CircuitBreaker} and fails over to a configured secondary provider.
 *
 * <p>Adapters never throw on delivery failure — they catch internally and
 * return {@link Outcome#FAILED}. Resilience4j only reacts to thrown
 * exceptions, so a {@code FAILED} primary outcome is translated into a
 * {@link ProviderDeliveryException} purely to drive the circuit breaker's
 * failure accounting; callers of this class never see that exception, only
 * {@link ProviderResult}.</p>
 *
 * <p>Every primary failure — whether the circuit is closed and the call was
 * attempted and failed, or the circuit is already open and the call was
 * rejected — triggers an immediate attempt against the secondary provider
 * for that channel. If the secondary also fails to send, the notification is
 * reported {@link Outcome#FAILED_CRITICAL} so the caller routes it to the
 * dead-letter queue.</p>
 */
public class ResilientNotificationProviderAdapter implements NotificationProviderPort {

  private static final Logger log =
      LoggerFactory.getLogger(ResilientNotificationProviderAdapter.class);

  private static final Map<Channel, String> CIRCUIT_BREAKER_NAMES = Map.of(
      Channel.EMAIL, "email-provider",
      Channel.SMS, "sms-provider",
      Channel.WHATSAPP, "whatsapp-provider",
      Channel.PUSH, "push-provider"
  );
  private static final String METER_FALLBACK_TRANSMISSIONS = "aegisnotify.fallback.transmissions";

  private final NotificationProviderRouter primaryRouter;
  private final Map<Channel, NotificationProviderPort> secondaryProvidersByChannel;
  private final CircuitBreakerRegistry circuitBreakerRegistry;
  private final MeterRegistry meterRegistry;

  public ResilientNotificationProviderAdapter(
      NotificationProviderRouter primaryRouter,
      Map<Channel, NotificationProviderPort> secondaryProvidersByChannel,
      CircuitBreakerRegistry circuitBreakerRegistry,
      MeterRegistry meterRegistry) {
    this.primaryRouter = primaryRouter;
    this.secondaryProvidersByChannel = secondaryProvidersByChannel;
    this.circuitBreakerRegistry = circuitBreakerRegistry;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public ProviderResult send(Channel channel, String recipient, String renderedContent,
      String subject) {
    CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(
        CIRCUIT_BREAKER_NAMES.get(channel));

    Supplier<ProviderResult> primaryCall = () -> {
      ProviderResult result = primaryRouter.send(channel, recipient, renderedContent, subject);
      if (result.outcome() == Outcome.FAILED) {
        throw new ProviderDeliveryException(result.errorDetail());
      }
      return result;
    };

    try {
      return circuitBreaker.decorateSupplier(primaryCall).get();
    } catch (CallNotPermittedException | ProviderDeliveryException primaryFailure) {
      log.warn("primary_provider_unavailable channel={} reason={}", channel,
          primaryFailure.getMessage());
      return failOver(channel, recipient, renderedContent, subject);
    }
  }

  private ProviderResult failOver(Channel channel, String recipient, String renderedContent,
      String subject) {
    NotificationProviderPort secondary = secondaryProvidersByChannel.get(channel);
    ProviderResult secondaryResult =
        secondary.send(channel, recipient, renderedContent, subject);

    if (secondaryResult.outcome() == Outcome.SENT) {
      meterRegistry.counter(METER_FALLBACK_TRANSMISSIONS).increment();
      return new ProviderResult(Outcome.SENT_VIA_FALLBACK, secondaryResult.providerName(), null);
    }

    return new ProviderResult(Outcome.FAILED_CRITICAL, null,
        "Primary and secondary providers both failed for channel " + channel + ": "
            + secondaryResult.errorDetail());
  }
}
