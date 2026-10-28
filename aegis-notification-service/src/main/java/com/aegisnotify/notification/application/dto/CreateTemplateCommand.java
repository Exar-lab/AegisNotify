package com.aegisnotify.notification.application.dto;

import com.aegisnotify.notification.domain.enums.Channel;
import java.util.List;

public record CreateTemplateCommand(
    String name,
    Channel channel,
    String subject,
    String body,
    List<String> variables
) {
}
