package com.aegisnotify.notification.infrastructure.messaging.kafka;

import com.aegisnotify.notification.application.dto.AuditEventMessage;
import com.aegisnotify.notification.application.port.out.AuditEventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * No-op fallback implementation of {@link AuditEventPublisherPort}.
 *
 * <p>Activated when audit publishing is explicitly disabled via
 * {@code audit.publishing.enabled=false}. Logs each event at DEBUG level
 * without sending to Kafka.</p>
 */
@Component
@ConditionalOnProperty(name = "audit.publishing.enabled", havingValue = "false")
public class LoggingAuditEventPublisher implements AuditEventPublisherPort {

  private static final Logger log =
      LoggerFactory.getLogger(LoggingAuditEventPublisher.class);

  @Override
  public void publish(AuditEventMessage event) {
    log.debug("audit_event_skipped notificationId={} status={} (publishing disabled)",
        event.notificationId(), event.status());
  }
}
