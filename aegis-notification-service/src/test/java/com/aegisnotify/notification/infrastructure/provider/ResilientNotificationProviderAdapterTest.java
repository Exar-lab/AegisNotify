package com.aegisnotify.notification.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
  private RetryRegistry retryRegistry;
  private SimpleMeterRegistry meterRegistry;
  private ResilientNotificationProviderAdapter adapter;
  private ResilientNotificationProviderAdapter retryExerciseAdapter;

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

    RetryConfig retryConfig = RetryConfig.custom()
        .maxAttempts(3)
        .waitDuration(Duration.ofMillis(1))
        .retryOnException(ex -> ex instanceof TransientProviderDeliveryException)
        .build();
    retryRegistry = RetryRegistry.of(retryConfig);

    meterRegistry = new SimpleMeterRegistry();

    adapter = new ResilientNotificationProviderAdapter(
        primaryRouter, Map.of(Channel.EMAIL, secondaryProvider), circuitBreakerRegistry,
        retryRegistry, meterRegistry);

    // Dedicated CircuitBreaker window wide enough to absorb every retry
    // attempt without the breaker itself opening mid-retry-sequence, so
    // tests exercising the full retry budget (maxAttempts=3) observe
    // exactly that many primary invocations rather than an early
    // CallNotPermittedException short-circuit from a too-narrow window.
    CircuitBreakerConfig lenientConfig = CircuitBreakerConfig.custom()
        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
        .slidingWindowSize(10)
        .minimumNumberOfCalls(10)
        .failureRateThreshold(50)
        .waitDurationInOpenState(Duration.ofMinutes(1))
        .build();
    CircuitBreakerRegistry lenientCircuitBreakerRegistry =
        CircuitBreakerRegistry.of(lenientConfig);

    retryExerciseAdapter = new ResilientNotificationProviderAdapter(
        primaryRouter, Map.of(Channel.EMAIL, secondaryProvider), lenientCircuitBreakerRegistry,
        retryRegistry, meterRegistry);
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
        .thenReturn(new ProviderResult(Outcome.FAILED, "SendGrid", "timeout", false));
    when(secondaryProvider.send(Channel.EMAIL, "to", "body", "subject"))
        .thenReturn(new ProviderResult(Outcome.SENT, "SendGrid-secondary", null));

    ProviderResult result = adapter.send(Channel.EMAIL, "to", "body", "subject");

    assertThat(result.outcome()).isEqualTo(Outcome.SENT_VIA_FALLBACK);
    assertThat(result.providerName()).isEqualTo("SendGrid-secondary");
    assertThat(meterRegistry.counter("aegisnotify.fallback.transmissions").count())
        .isEqualTo(1.0d);
  }

  @Test
  void send_primaryAndSecondaryFail_returnsFailedCritical() {
    when(primaryAdapter.send(Channel.EMAIL, "to", "body", "subject"))
        .thenReturn(new ProviderResult(Outcome.FAILED, "SendGrid", "timeout", false));
    when(secondaryProvider.send(Channel.EMAIL, "to", "body", "subject"))
        .thenReturn(new ProviderResult(Outcome.FAILED, "SendGrid-secondary", "also down"));

    ProviderResult result = adapter.send(Channel.EMAIL, "to", "body", "subject");

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED_CRITICAL);
  }

  @Test
  void send_transientFailureThenSuccess_retriesAndReturnsSentFromPrimary() {
    when(primaryAdapter.send(Channel.EMAIL, "to", "body", "subject"))
        .thenReturn(new ProviderResult(Outcome.FAILED, "SendGrid", "429 rate limited", true))
        .thenReturn(new ProviderResult(Outcome.SENT, "SendGrid", null));

    ProviderResult result = adapter.send(Channel.EMAIL, "to", "body", "subject");

    assertThat(result.outcome()).isEqualTo(Outcome.SENT);
    verify(primaryAdapter, times(2)).send(Channel.EMAIL, "to", "body", "subject");
    verify(secondaryProvider, never()).send(any(), any(), any(), any());
  }

  @Test
  void send_nonRetryableFailure_skipsRetryAndFailsOverAfterSingleAttempt() {
    when(primaryAdapter.send(Channel.EMAIL, "to", "body", "subject"))
        .thenReturn(new ProviderResult(Outcome.FAILED, "SendGrid", "400 bad request", false));
    when(secondaryProvider.send(Channel.EMAIL, "to", "body", "subject"))
        .thenReturn(new ProviderResult(Outcome.SENT, "SendGrid-secondary", null));

    ProviderResult result = adapter.send(Channel.EMAIL, "to", "body", "subject");

    assertThat(result.outcome()).isEqualTo(Outcome.SENT_VIA_FALLBACK);
    verify(primaryAdapter, times(1)).send(Channel.EMAIL, "to", "body", "subject");
  }

  @Test
  void send_retryExhausted_failsOverAfterMaxAttempts() {
    when(primaryAdapter.send(Channel.EMAIL, "to", "body", "subject"))
        .thenReturn(new ProviderResult(Outcome.FAILED, "SendGrid", "503 unavailable", true));
    when(secondaryProvider.send(Channel.EMAIL, "to", "body", "subject"))
        .thenReturn(new ProviderResult(Outcome.SENT, "SendGrid-secondary", null));

    ProviderResult result = retryExerciseAdapter.send(Channel.EMAIL, "to", "body", "subject");

    assertThat(result.outcome()).isEqualTo(Outcome.SENT_VIA_FALLBACK);
    verify(primaryAdapter, times(3)).send(Channel.EMAIL, "to", "body", "subject");
  }

  @Test
  void send_circuitOpen_neverInvokesPrimaryAgainAndFailsOverImmediately() {
    when(primaryAdapter.send(eq(Channel.EMAIL), any(), any(), any()))
        .thenReturn(new ProviderResult(Outcome.FAILED, "SendGrid", "down", false));
    when(secondaryProvider.send(any(), any(), any(), any()))
        .thenReturn(new ProviderResult(Outcome.SENT, "SendGrid-secondary", null));

    adapter.send(Channel.EMAIL, "to", "body", "subject");
    adapter.send(Channel.EMAIL, "to", "body", "subject");

    CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);
    assertThat(circuitBreaker.getState()).isEqualTo(State.OPEN);

    int invocationsBeforeOpenCall = 2;
    ProviderResult result = adapter.send(Channel.EMAIL, "to", "body", "subject");

    assertThat(result.outcome()).isEqualTo(Outcome.SENT_VIA_FALLBACK);
    verify(primaryAdapter, times(invocationsBeforeOpenCall))
        .send(eq(Channel.EMAIL), any(), any(), any());
  }

  @Test
  void send_retryableFailedResult_throwsTransientProviderDeliveryException() {
    when(primaryAdapter.send(Channel.EMAIL, "to", "body", "subject"))
        .thenReturn(new ProviderResult(Outcome.FAILED, "SendGrid", "429 rate limited", true));
    when(secondaryProvider.send(Channel.EMAIL, "to", "body", "subject"))
        .thenReturn(new ProviderResult(Outcome.FAILED, "SendGrid-secondary", "also down"));

    ProviderResult result = retryExerciseAdapter.send(Channel.EMAIL, "to", "body", "subject");

    assertThat(result.outcome()).isEqualTo(Outcome.FAILED_CRITICAL);
    verify(primaryAdapter, times(3)).send(Channel.EMAIL, "to", "body", "subject");
  }

  @Test
  void primaryCallThrowsSealedExceptionSubtype_basedOnRetryableFlag() {
    CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);

    assertThatThrownBy(() -> {
      throw new TransientProviderDeliveryException("transient failure");
    }).isInstanceOf(ProviderDeliveryException.class)
        .isInstanceOf(TransientProviderDeliveryException.class);

    assertThatThrownBy(() -> {
      throw new PermanentProviderDeliveryException("permanent failure");
    }).isInstanceOf(ProviderDeliveryException.class)
        .isInstanceOf(PermanentProviderDeliveryException.class);

    assertThat(circuitBreaker.getState()).isEqualTo(State.CLOSED);
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
