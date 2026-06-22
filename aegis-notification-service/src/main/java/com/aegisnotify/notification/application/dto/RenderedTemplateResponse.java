package com.aegisnotify.notification.application.dto;

public record RenderedTemplateResponse(
    String templateName,
    String subject,
    String renderedBody
) {
}
