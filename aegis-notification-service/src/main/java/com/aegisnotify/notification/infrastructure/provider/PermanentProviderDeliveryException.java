package com.aegisnotify.notification.infrastructure.provider;

/**
 * A provider failure classified permanent — a business or validation
 * rejection that must not be retried and should fail over immediately.
 */
public final class PermanentProviderDeliveryException extends ProviderDeliveryException {

  PermanentProviderDeliveryException(String message) {
    super(message);
  }
}
