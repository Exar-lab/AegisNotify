package com.aegisnotify.notification.infrastructure.web.dto;

import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateNotificationRequest(
    @NotNull Channel channel,
    @NotBlank @Size(max = 320) String recipient,
    @NotBlank @Size(max = 120) String templateName,
    Map<String, Object> parameters,
    Priority priority
) {

  public CreateNotificationRequest {
    if (parameters == null) {
      parameters = Map.of();
    }
    if (priority == null) {
      priority = Priority.MEDIUM;
    }
  }
}
