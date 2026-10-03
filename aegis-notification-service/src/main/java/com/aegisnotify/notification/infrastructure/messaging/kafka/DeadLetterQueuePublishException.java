package com.aegisnotify.notification.infrastructure.messaging.kafka;

/**
 * Signals that a {@link KafkaDeadLetterQueueAdapter} publish attempt failed.
 *
 * <p>Thrown rather than swallowed (issue #30) so a failure to reach the
 * structured {@code notifications-dlq} topic is never silently lost: see
 * {@link KafkaDeadLetterQueueAdapter}'s class Javadoc for the full
 * failure-propagation reasoning and its fallback-of-last-resort interaction
 * with the Kafka listener's own per-topic {@code -dlt} error handler.</p>
 */
public class DeadLetterQueuePublishException extends RuntimeException {

  public DeadLetterQueuePublishException(String message, Throwable cause) {
    super(message, cause);
  }
}
