package com.aegisnotify.notification.infrastructure.provider;

import com.aegisnotify.notification.application.dto.ProviderCircuitBreakerStatus;
import com.aegisnotify.notification.application.port.out.CircuitBreakerInspectionPort;
import com.aegisnotify.notification.domain.enums.Channel;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class Resilience4jCircuitBreakerAdapter implements CircuitBreakerInspectionPort {

  private record ProviderDefinition(
      String key,
      String name,
      Channel channel,
      String role,
      String fallbackProvider
  ) {}

  private static final List<ProviderDefinition> MONITORED_PROVIDERS = List.of(
      new ProviderDefinition(
          "email-provider",
          "SendGrid",
          Channel.EMAIL,
          "PRIMARY",
          "SendGrid Secondary"
      ),
      new ProviderDefinition(
          "sms-provider",
          "Twilio SMS",
          Channel.SMS,
          "PRIMARY",
          "Twilio SMS Secondary"
      ),
      new ProviderDefinition(
          "whatsapp-provider",
          "Twilio WhatsApp",
          Channel.WHATSAPP,
          "PRIMARY",
          "Twilio WhatsApp Secondary"
      ),
      new ProviderDefinition(
          "push-provider",
          "Firebase Cloud Messaging",
          Channel.PUSH,
          "PRIMARY",
          "Firebase Secondary"
      )
  );

  private final CircuitBreakerRegistry circuitBreakerRegistry;
  private final Map<String, Instant> lastStateChanges = new ConcurrentHashMap<>();

  public Resilience4jCircuitBreakerAdapter(CircuitBreakerRegistry circuitBreakerRegistry) {
    this.circuitBreakerRegistry = circuitBreakerRegistry;
    circuitBreakerRegistry.getEventPublisher()
        .onEntryAdded(event -> observe(event.getAddedEntry()));
    circuitBreakerRegistry.getAllCircuitBreakers().forEach(this::observe);
  }

  private void observe(CircuitBreaker circuitBreaker) {
    circuitBreaker.getEventPublisher().onStateTransition(event ->
        lastStateChanges.put(circuitBreaker.getName(), event.getCreationTime().toInstant()));
  }

  @Override
  public List<ProviderCircuitBreakerStatus> getProviderCircuitBreakerStatuses() {
    return MONITORED_PROVIDERS.stream()
        .flatMap(def -> Stream.of(toStatus(def), new ProviderCircuitBreakerStatus(
            def.key() + "-fallback", def.fallbackProvider(), def.channel(), "FALLBACK",
            null, "NOT_PROTECTED")))
        .toList();
  }

  private ProviderCircuitBreakerStatus toStatus(ProviderDefinition def) {
    var circuitBreaker = circuitBreakerRegistry.find(def.key());
    String state = circuitBreaker.map(CircuitBreaker::getState)
        .map(CircuitBreaker.State::name).orElse("UNKNOWN");
    var metrics = circuitBreaker.map(CircuitBreaker::getMetrics);

    return new ProviderCircuitBreakerStatus(
        def.key(),
        def.name(),
        def.channel(),
        def.role(),
        def.fallbackProvider(),
        state,
        metrics.map(value -> availableRate(value.getFailureRate())).orElse(null),
        metrics.map(value -> availableRate(value.getSlowCallRate())).orElse(null),
        lastStateChanges.get(def.key()), null, null, null
    );
  }

  private Double availableRate(float rate) {
    return rate < 0 ? null : (double) rate;
  }
}
