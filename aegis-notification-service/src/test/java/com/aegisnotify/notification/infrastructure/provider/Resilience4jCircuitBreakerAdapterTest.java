package com.aegisnotify.notification.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.notification.application.dto.ProviderCircuitBreakerStatus;
import com.aegisnotify.notification.domain.enums.Channel;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Resilience4jCircuitBreakerAdapterTest {

  private CircuitBreakerRegistry registry;
  private Resilience4jCircuitBreakerAdapter adapter;

  @BeforeEach
  void setUp() {
    registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults());
    adapter = new Resilience4jCircuitBreakerAdapter(registry);
  }

  @Test
  void getProviderCircuitBreakerStatuses_mapsConfiguredProvidersAndIgnoresInternal() {
    CircuitBreaker emailCb = registry.circuitBreaker("email-provider");
    CircuitBreaker smsCb = registry.circuitBreaker("sms-provider");
    CircuitBreaker whatsappCb = registry.circuitBreaker("whatsapp-provider");
    final CircuitBreaker pushCb = registry.circuitBreaker("push-provider");

    // Internal circuit breaker that must be ignored
    registry.circuitBreaker("aggregation-agent");

    whatsappCb.transitionToOpenState();
    whatsappCb.transitionToHalfOpenState();
    pushCb.transitionToOpenState();

    List<ProviderCircuitBreakerStatus> statuses = adapter.getProviderCircuitBreakerStatuses();

    assertThat(statuses).hasSize(4);
    assertThat(statuses).extracting(ProviderCircuitBreakerStatus::key)
        .containsExactly("email-provider", "sms-provider", "whatsapp-provider", "push-provider");
    assertThat(statuses).noneMatch(s -> "aggregation-agent".equals(s.key()));

    ProviderCircuitBreakerStatus email = statuses.get(0);
    assertThat(email.name()).isEqualTo("SendGrid");
    assertThat(email.channel()).isEqualTo(Channel.EMAIL);
    assertThat(email.role()).isEqualTo("PRIMARY");
    assertThat(email.fallbackProvider()).isEqualTo("SendGrid Secondary");
    assertThat(email.state()).isEqualTo("CLOSED");

    ProviderCircuitBreakerStatus sms = statuses.get(1);
    assertThat(sms.name()).isEqualTo("Twilio SMS");
    assertThat(sms.channel()).isEqualTo(Channel.SMS);
    assertThat(sms.role()).isEqualTo("PRIMARY");
    assertThat(sms.fallbackProvider()).isEqualTo("Twilio SMS Secondary");
    assertThat(sms.state()).isEqualTo("CLOSED");

    ProviderCircuitBreakerStatus whatsapp = statuses.get(2);
    assertThat(whatsapp.state()).isEqualTo("HALF_OPEN");

    ProviderCircuitBreakerStatus push = statuses.get(3);
    assertThat(push.state()).isEqualTo("OPEN");
  }

  @Test
  void getProviderCircuitBreakerStatuses_unregisteredProvider_returnsUnknownState() {
    // Registry is empty, so none of the 4 providers are created yet
    List<ProviderCircuitBreakerStatus> statuses = adapter.getProviderCircuitBreakerStatuses();

    assertThat(statuses).hasSize(4);
    assertThat(statuses).allMatch(s -> "UNKNOWN".equals(s.state()));
  }
}

