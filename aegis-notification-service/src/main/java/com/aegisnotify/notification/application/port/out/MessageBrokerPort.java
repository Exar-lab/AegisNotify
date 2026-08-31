package com.aegisnotify.notification.application.port.out;

import java.util.Map;

/**
 * Outbound port for publishing an outbox event's payload to the message
 * broker.
 *
 * <p>Implementations MUST be synchronous — or at least MUST NOT silently
 * swallow failures — and MUST throw on any publish failure (network error,
 * broker rejection, acknowledgement timeout, etc.) rather than returning
 * normally. This is the opposite contract of
 * {@link AuditEventPublisherPort}, which is deliberately fire-and-forget:
 * here, the caller marks the corresponding outbox row {@code PROCESSED} in
 * the same transaction as the call to {@link #publish}, so a swallowed
 * failure would leave a row marked {@code PROCESSED} with nothing ever
 * actually published. Propagating the failure lets that transaction roll
 * back to {@code UNPROCESSED} for retry on the next relay poll instead.</p>
 */
public interface MessageBrokerPort {

  /**
   * Publishes the payload to the given logical topic.
   *
   * @param topic   the logical topic name (implementations may alias it to a
   *                physically configured topic)
   * @param payload the outbox event payload to publish
   * @throws RuntimeException if the publish cannot be confirmed as
   *                          successful; implementations must never swallow
   *                          this failure
   */
  void publish(String topic, Map<String, Object> payload);
}
