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
 * @param subject optional subject line
 * @param body template body
 * @param variables declared variable names
 */
public record CreateTemplateRequest(
    @NotBlank @Size(max = 120) String name,
    @NotNull Channel channel,
    String subject,
    @NotBlank String body,
    List<String> variables
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
