package com.aegisnotify.notification.application.dto;

import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.Priority;
import java.util.Map;

public record CreateNotificationCommand(
    Channel channel,
    String recipient,
    String templateName,
    Map<String, Object> parameters,
    Priority priority
) {
}
