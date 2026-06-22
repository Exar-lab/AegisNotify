package com.aegisnotify.notification.application.dto;

import java.util.Map;

public record RenderTemplateCommand(
    String templateName,
    Map<String, Object> parameters
) {
}
