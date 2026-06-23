package com.aegisnotify.notification.application.port.out;

import com.aegisnotify.notification.application.dto.AuditEventMessage;

/**
 * Outbound port for publishing audit events on notification lifecycle transitions.
 *
 * <p>Implementations must be fire-and-forget: failures are logged but never propagated
 * to the caller. Publishing should occur after the transaction commits to prevent
 * ghost events on rollback.</p>
 */
public interface AuditEventPublisherPort {

  /**
   * Publishes an audit event message.
   *
   * @param event the audit event to publish
   */
  void publish(AuditEventMessage event);
}
