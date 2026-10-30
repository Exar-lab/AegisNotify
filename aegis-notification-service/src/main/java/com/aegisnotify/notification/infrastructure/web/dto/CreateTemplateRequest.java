package com.aegisnotify.notification.infrastructure.web.dto;

import com.aegisnotify.notification.application.dto.CreateTemplateCommand;
import com.aegisnotify.notification.domain.enums.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request body for {@code POST /api/v1/templates}. A missing
 * {@code variables} list defaults to empty.
 *
 * @param name unique template name, max 120 chars (matches the column)
 * @param channel delivery channel
 * @param subject optional subject line, max 255 chars (matches the column)
 * @param body template body
 * @param variables declared variable names, each non-blank
 */
public record CreateTemplateRequest(
    @NotBlank @Size(max = 120) String name,
    @NotNull Channel channel,
    @Size(max = 255) String subject,
    @NotBlank String body,
    List<@NotBlank String> variables
) {

  public CreateTemplateRequest {
    if (variables == null) {
      variables = List.of();
    }
  }

  public CreateTemplateCommand toCommand() {
    return new CreateTemplateCommand(name, channel, subject, body, variables);
  }
}
