package com.aegisnotify.notification.infrastructure.messaging.kafka;

/**
 * Signals that a {@link KafkaMessageBrokerAdapter} publish attempt failed.
 *
 * <p>Thrown so the failure is visible to the caller's transaction (K3):
 * {@code PublishOutboxEventTransactions#publishOne(OutboxEvent)} marks the
 * outbox row {@code PROCESSED} in the same transaction as the publish, so a swallowed
 * failure here would leave a row marked {@code PROCESSED} with nothing ever
 * published. Propagating rolls the transaction back to {@code UNPROCESSED}
 * for retry on the next relay poll.</p>
 */
public class MessageBrokerPublishException extends RuntimeException {

  public MessageBrokerPublishException(String message, Throwable cause) {
    super(message, cause);
  }
}
