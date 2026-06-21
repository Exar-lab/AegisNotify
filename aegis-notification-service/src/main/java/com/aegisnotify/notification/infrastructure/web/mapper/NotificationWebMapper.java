package com.aegisnotify.notification.infrastructure.web.mapper;

import com.aegisnotify.notification.application.dto.CreateNotificationCommand;
import com.aegisnotify.notification.infrastructure.web.dto.CreateNotificationRequest;
import org.springframework.stereotype.Component;

@Component
public class NotificationWebMapper {

  public CreateNotificationCommand toCommand(CreateNotificationRequest request) {
    return new CreateNotificationCommand(
        request.channel(),
        request.recipient(),
        request.templateName(),
        request.parameters(),
        request.priority()
    );
  }
}
