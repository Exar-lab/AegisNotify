package com.aegisnotify.notification.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.application.dto.ProviderResult.Outcome;
import com.aegisnotify.notification.application.port.out.NotificationProviderPort;
import com.aegisnotify.notification.domain.enums.Channel;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResilientNotificationProviderAdapterTest {

  private static final String CIRCUIT_BREAKER_NAME = "email-provider";

  private final SendGridEmailProviderAdapter primaryAdapter =
      mock(SendGridEmailProviderAdapter.class);
  private final NotificationProviderPort secondaryProvider = mock(NotificationProviderPort.class);

  private NotificationProviderRouter primaryRouter;
  private CircuitBreakerRegistry circuitBreakerRegistry;
  private ResilientNotificationProviderAdapter adapter;

  @BeforeEach
  void setUp() {
    primaryRouter = new NotificationProviderRouter(
        primaryAdapter, mock(TwilioSmsProviderAdapter.class),
        mock(TwilioWhatsAppProviderAdapter.class), mock(FirebasePushProviderAdapter.class));

    CircuitBreakerConfig config = CircuitBreakerConfig.custom()
        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
        .slidingWindowSize(2)
        .minimumNumberOfCalls(2)
        .failureRateThreshold(50)
        .waitDurationInOpenState(Duration.ofMinutes(1))
        .permittedNumberOfCallsInHalfOpenState(1)
        .automaticTransitionFromOpenToHalfOpenEnabled(false)
        .build();
    circuitBreakerRegistry = CircuitBreakerRegistry.of(config);

    adapter = new ResilientNotificationProviderAdapter(
        primaryRouter, Map.of(Channel.EMAIL, secondaryProvider), circuitBreakerRegistry);
  }

  @Test
  void send_primarySucceeds_returnsSentAndNeverCallsSecondary() {
    when(primaryAdapter.send(Channel.EMAIL, "to", "body", "subject"))
        .thenReturn(new ProviderResult(Outcome.SENT, "SendGrid", null));

    ProviderResult result = adapter.send(Channel.EMAIL, "to", "body", "subject");

    assertThat(result.outcome()).isEqualTo(Outcome.SENT);
    verify(secondaryProvider, never()).send(any(), any(), any(), any());
  }

  @Test
  void send_primaryFailsSecondarySucceeds_returnsSentViaFallback() {
    when(primaryAdapter.send(Channel.EMAIL, "to", "body", "subject"))
        .thenReturn(new ProviderResult(Outcome.FAILED, "SendGrid", "timeout"));
    when(secondaryProvider.send(Channel.EMAIL, "to", "body", "subject"))
        .thenReturn(new ProviderResult(Outcome.SENT, "SendGrid-secondary", null));

    ProviderResult result = adapter.send(Channel.EMAIL, "to", "body", "subject");

    assertThat(result.outcome()).isEqualTo(Outcome.SENT_VIA_FALLBACK);
    assertThat(result.providerName()).isEqualTo("SendGrid-secondary");
  }

  @Test
  void send_primaryAndSecondaryFail_returnsFailedCritical() {
    when(primaryAdapter.send(Channel.EMAIL, "to", "body", "subject"))
        .thenReturn(new ProviderResult(Outcome.FAILED, "SendGrid", "timeout"));
    when(secondaryProvider.send(Channel.EMAIL, "to", "body", "subject"))
        .thenReturn(new ProviderResult(Outcome.FAILED, "SendGrid-secondary", "also down"));

    ProviderResult result = adapter.send(Channel.EMAIL, "to", "body", "subject");

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED_CRITICAL);
  }

  @Test
  void send_repeatedPrimaryFailures_opensCircuitAndStopsCallingPrimary() {
    when(primaryAdapter.send(eq(Channel.EMAIL), any(), any(), any()))
        .thenReturn(new ProviderResult(Outcome.FAILED, "SendGrid", "down"));
    when(secondaryProvider.send(any(), any(), any(), any()))
        .thenReturn(new ProviderResult(Outcome.SENT, "SendGrid-secondary", null));

    adapter.send(Channel.EMAIL, "to", "body", "subject");
    adapter.send(Channel.EMAIL, "to", "body", "subject");

    CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);
    assertThat(circuitBreaker.getState()).isEqualTo(State.OPEN);

    ProviderResult result = adapter.send(Channel.EMAIL, "to", "body", "subject");

    assertThat(result.outcome()).isEqualTo(Outcome.SENT_VIA_FALLBACK);
    verify(primaryAdapter, times(2)).send(eq(Channel.EMAIL), any(), any(), any());
  }

  @Test
  void send_afterHalfOpenSuccess_transitionsToClosed() {
    when(primaryAdapter.send(eq(Channel.EMAIL), any(), any(), any()))
        .thenReturn(new ProviderResult(Outcome.FAILED, "SendGrid", "down"));
    when(secondaryProvider.send(any(), any(), any(), any()))
        .thenReturn(new ProviderResult(Outcome.SENT, "SendGrid-secondary", null));

    adapter.send(Channel.EMAIL, "to", "body", "subject");
    adapter.send(Channel.EMAIL, "to", "body", "subject");

    CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);
    assertThat(circuitBreaker.getState()).isEqualTo(State.OPEN);

    circuitBreaker.transitionToHalfOpenState();
    assertThat(circuitBreaker.getState()).isEqualTo(State.HALF_OPEN);

    when(primaryAdapter.send(eq(Channel.EMAIL), any(), any(), any()))
        .thenReturn(new ProviderResult(Outcome.SENT, "SendGrid", null));

    ProviderResult result = adapter.send(Channel.EMAIL, "to", "body", "subject");

    assertThat(result.outcome()).isEqualTo(Outcome.SENT);
    assertThat(circuitBreaker.getState()).isEqualTo(State.CLOSED);
  }
}
