package com.aegisnotify.notification.application.dto;

import java.util.List;

/**
 * Editable template content. {@code name} and {@code channel} are
 * deliberately absent: they are immutable after creation.
 *
 * @param subject optional subject line, may be {@code null}
 * @param body template body
 * @param variables declared variable names
 */
public record UpdateTemplateCommand(
    String subject,
    String body,
    List<String> variables
) {
}
