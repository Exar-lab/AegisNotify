package com.aegisnotify.notification.application.dto;

import java.util.Map;
import java.util.UUID;

/**
 * Notification event payload published to the Kafka priority topics.
 *
 * <p>The JSON field names mirror the producer payload exactly: {@code id},
 * {@code channel}, {@code recipient}, {@code templateName}, {@code parameters},
 * and {@code priority}.</p>
 *
 * @param id the notification identifier
 * @param channel the notification channel
 * @param recipient the notification recipient
 * @param templateName the template used to render the notification
 * @param parameters template variables
 * @param priority the notification priority
 */
public record NotificationEvent(
    UUID id,
    String channel,
    String recipient,
    String templateName,
    Map<String, Object> parameters,
    String priority
) {
}
