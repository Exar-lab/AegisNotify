package com.aegisnotify.notification.infrastructure.provider;

import com.aegisnotify.notification.application.dto.ProviderCircuitBreakerStatus;
import com.aegisnotify.notification.application.port.out.CircuitBreakerInspectionPort;
import com.aegisnotify.notification.domain.enums.Channel;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
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

  public Resilience4jCircuitBreakerAdapter(CircuitBreakerRegistry circuitBreakerRegistry) {
    this.circuitBreakerRegistry = circuitBreakerRegistry;
  }

  @Override
  public List<ProviderCircuitBreakerStatus> getProviderCircuitBreakerStatuses() {
    return MONITORED_PROVIDERS.stream()
        .map(this::toStatus)
        .toList();
  }

  private ProviderCircuitBreakerStatus toStatus(ProviderDefinition def) {
    String state = circuitBreakerRegistry.find(def.key())
        .map(CircuitBreaker::getState)
        .map(CircuitBreaker.State::name)
        .orElse("UNKNOWN");

    return new ProviderCircuitBreakerStatus(
        def.key(),
        def.name(),
        def.channel(),
        def.role(),
        def.fallbackProvider(),
        state
    );
  }
}
