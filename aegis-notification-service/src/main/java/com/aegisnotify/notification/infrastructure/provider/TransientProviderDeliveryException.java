package com.aegisnotify.notification.infrastructure.provider;

/**
 * A provider failure classified transient — eligible for retry on the same
 * provider before circuit-breaker failover is considered.
 */
public final class TransientProviderDeliveryException extends ProviderDeliveryException {

  TransientProviderDeliveryException(String message) {
    super(message);
  }
}
