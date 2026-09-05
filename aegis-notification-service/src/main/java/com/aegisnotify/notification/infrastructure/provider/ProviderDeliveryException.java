package com.aegisnotify.notification.infrastructure.provider;

/**
 * Signals a {@link com.aegisnotify.notification.application.dto.ProviderResult.Outcome#FAILED}
 * outcome from a provider adapter as a thrown exception, so Resilience4j's
 * circuit breaker (which only reacts to exceptions) can count it as a failure.
 * Adapters themselves never throw this — it exists purely at the
 * {@link ResilientNotificationProviderAdapter} boundary.
 *
 * <p>Sealed to exactly two subtypes so the Retry decorator can use a strict,
 * type-based allow-list predicate: only {@link TransientProviderDeliveryException}
 * is eligible for retry, {@link PermanentProviderDeliveryException} never is.</p>
 */
public sealed class ProviderDeliveryException extends RuntimeException
    permits TransientProviderDeliveryException, PermanentProviderDeliveryException {

  ProviderDeliveryException(String message) {
    super(message);
  }
}
