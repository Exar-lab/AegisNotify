package com.aegisnotify.notification.infrastructure.web.dto;

import com.aegisnotify.notification.application.dto.UpdateTemplateCommand;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Request body for {@code PUT /api/v1/templates/{id}}. There are no
 * {@code name}/{@code channel} fields: both are immutable after creation,
 * and unknown JSON properties are ignored. A missing {@code variables} list
 * defaults to empty.
 *
 * @param subject optional subject line
 * @param body template body
 * @param variables declared variable names
 */
public record UpdateTemplateRequest(
    String subject,
    @NotBlank String body,
    List<String> variables
) {

  public UpdateTemplateRequest {
    if (variables == null) {
      variables = List.of();
    }
  }

  public UpdateTemplateCommand toCommand() {
    return new UpdateTemplateCommand(subject, body, variables);
  }
}
