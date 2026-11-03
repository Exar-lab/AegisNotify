package com.aegisnotify.notification.infrastructure.web.dto;

import com.aegisnotify.notification.application.dto.RenderedTemplateResponse;

/**
 * Response body for {@code POST /api/v1/templates/render}.
 *
 * @param name the template name
 * @param subject the template subject, null for channels without one
 * @param body the rendered body
 */
public record RenderedTemplateHttpResponse(String name, String subject, String body) {

  public static RenderedTemplateHttpResponse from(RenderedTemplateResponse rendered) {
    return new RenderedTemplateHttpResponse(
        rendered.templateName(), rendered.subject(), rendered.renderedBody());
  }
}
