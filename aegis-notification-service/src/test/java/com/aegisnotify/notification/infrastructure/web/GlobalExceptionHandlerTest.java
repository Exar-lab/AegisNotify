package com.aegisnotify.notification.infrastructure.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aegisnotify.notification.domain.exception.TemplateRenderingException;
import com.aegisnotify.notification.infrastructure.web.dto.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void handleTemplateRendering_singleMissingVariable_returns422() {
    TemplateRenderingException ex =
        new TemplateRenderingException("Missing required template variables: [orderId]");

    ResponseEntity<ApiErrorResponse> response = handler.handleTemplateRendering(ex);

    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), response.getBody().status());
    assertEquals("Unprocessable Entity", response.getBody().error());
    assertEquals("Missing required template variables: [orderId]", response.getBody().message());
  }

  @Test
  void handleTemplateRendering_multipleMissingVariables_returns422WithFullMessage() {
    TemplateRenderingException ex =
        new TemplateRenderingException("Missing required template variables: [name, total]");

    ResponseEntity<ApiErrorResponse> response = handler.handleTemplateRendering(ex);

    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
    assertEquals("Missing required template variables: [name, total]",
        response.getBody().message());
  }
}
