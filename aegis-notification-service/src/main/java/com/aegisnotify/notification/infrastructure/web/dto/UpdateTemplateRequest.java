package com.aegisnotify.notification.infrastructure.web.dto;

import com.aegisnotify.notification.application.dto.UpdateTemplateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request body for {@code PUT /api/v1/templates/{id}}. There are no
 * {@code name}/{@code channel} fields: both are immutable after creation,
 * and unknown JSON properties are ignored. A missing {@code variables} list
 * defaults to empty.
 *
 * @param subject optional subject line, max 255 chars (matches the column)
 * @param body template body
 * @param variables declared variable names, each non-blank
 */
public record UpdateTemplateRequest(
    @Size(max = 255) String subject,
    @NotBlank String body,
    List<@NotBlank String> variables
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
