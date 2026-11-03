package com.aegisnotify.notification.infrastructure.web.dto;

import com.aegisnotify.notification.application.dto.RenderTemplateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Request body for {@code POST /api/v1/templates/render}. Renders an active
 * template with the given parameters without sending anything. A missing
 * {@code parameters} is treated as an empty map.
 *
 * @param templateName the name of an active template
 * @param parameters the values for the template variables
 */
public record RenderTemplateRequest(
    @NotBlank @Size(max = 120) String templateName,
    Map<String, Object> parameters) {

  public RenderTemplateRequest {
    parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
  }

  public RenderTemplateCommand toCommand() {
    return new RenderTemplateCommand(templateName, parameters);
  }
}
