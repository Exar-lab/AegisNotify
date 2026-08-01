package com.aegisnotify.notification.infrastructure.provider;

/**
 * Signals a {@link com.aegisnotify.notification.application.dto.ProviderResult.Outcome#FAILED}
 * outcome from a provider adapter as a thrown exception, so Resilience4j's
 * circuit breaker (which only reacts to exceptions) can count it as a failure.
 * Adapters themselves never throw this — it exists purely at the
 * {@link ResilientNotificationProviderAdapter} boundary.
 */
class ProviderDeliveryException extends RuntimeException {

  ProviderDeliveryException(String message) {
    super(message);
  }
}
