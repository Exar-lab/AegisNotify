package com.aegisnotify.notification.infrastructure.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aegisnotify.notification.domain.exception.TemplateRenderingException;
import com.aegisnotify.notification.infrastructure.web.dto.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
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

  @Test
  void handleOptimisticLockingFailure_returns409WithRetryMessage() {
    OptimisticLockingFailureException ex =
        new OptimisticLockingFailureException("Row was updated or deleted by another transaction");

    ResponseEntity<ApiErrorResponse> response = handler.handleOptimisticLockingFailure(ex);

    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    assertEquals(HttpStatus.CONFLICT.value(), response.getBody().status());
    assertEquals("Conflict", response.getBody().error());
    assertEquals("This template was modified by someone else. Reload it and try again.",
        response.getBody().message());
  }
}
