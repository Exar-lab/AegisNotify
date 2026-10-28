package com.aegisnotify.notification.application.dto;

import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.model.Template;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read model returned by every template endpoint.
 *
 * @param id template id
 * @param name unique template name
 * @param channel delivery channel
 * @param subject optional subject line, may be {@code null}
 * @param body template body
 * @param variables declared variable names
 * @param active whether the template can be used for new notifications
 * @param createdAt creation timestamp
 * @param updatedAt last modification timestamp
 */
public record TemplateResponse(
    UUID id,
    String name,
    Channel channel,
    String subject,
    String body,
    List<String> variables,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {

  public static TemplateResponse from(Template template) {
    return new TemplateResponse(
        template.getId(),
        template.getName(),
        template.getChannel(),
        template.getSubject(),
        template.getBody(),
        template.getVariables(),
        template.isActive(),
        template.getCreatedAt(),
        template.getUpdatedAt()
    );
  }
}
