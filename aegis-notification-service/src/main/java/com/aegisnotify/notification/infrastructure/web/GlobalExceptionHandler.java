package com.aegisnotify.notification.infrastructure.web;

import com.aegisnotify.notification.domain.exception.InvalidRecipientException;
import com.aegisnotify.notification.domain.exception.NotificationNotCancellableException;
import com.aegisnotify.notification.domain.exception.NotificationNotFoundException;
import com.aegisnotify.notification.domain.exception.NotificationNotRetryableException;
import com.aegisnotify.notification.domain.exception.TemplateNotFoundException;
import com.aegisnotify.notification.domain.exception.TemplateRenderingException;
import com.aegisnotify.notification.infrastructure.web.dto.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleValidation(
      MethodArgumentNotValidException ex) {
    String message = ex.getBindingResult().getFieldErrors().stream()
        .findFirst()
        .map(error -> error.getField() + ": " + error.getDefaultMessage())
        .orElse("Validation failed");
    return ResponseEntity.badRequest()
        .body(ApiErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            message));
  }

  @ExceptionHandler(InvalidRecipientException.class)
  public ResponseEntity<ApiErrorResponse> handleInvalidRecipient(
      InvalidRecipientException ex) {
    return ResponseEntity.badRequest()
        .body(ApiErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            ex.getMessage()));
  }

  @ExceptionHandler(TemplateNotFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleTemplateNotFound(
      TemplateNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiErrorResponse.of(
            HttpStatus.NOT_FOUND.value(),
            "Not Found",
            ex.getMessage()));
  }

  @ExceptionHandler(NotificationNotFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleNotificationNotFound(
      NotificationNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiErrorResponse.of(
            HttpStatus.NOT_FOUND.value(),
            "Not Found",
            ex.getMessage()));
  }

  @ExceptionHandler(TemplateRenderingException.class)
  public ResponseEntity<ApiErrorResponse> handleTemplateRendering(
      TemplateRenderingException ex) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(ApiErrorResponse.of(
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            "Unprocessable Entity",
            ex.getMessage()));
  }

  @ExceptionHandler(NotificationNotCancellableException.class)
  public ResponseEntity<ApiErrorResponse> handleNotificationNotCancellable(
      NotificationNotCancellableException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiErrorResponse.of(
            HttpStatus.CONFLICT.value(),
            "Conflict",
            ex.getMessage()));
  }

  @ExceptionHandler(NotificationNotRetryableException.class)
  public ResponseEntity<ApiErrorResponse> handleNotificationNotRetryable(
      NotificationNotRetryableException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiErrorResponse.of(
            HttpStatus.CONFLICT.value(),
            "Conflict",
            ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex) {
    return ResponseEntity.badRequest()
        .body(ApiErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            "Invalid value for parameter: " + ex.getName()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleGeneral(Exception ex) {
    log.error("Unexpected error", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiErrorResponse.of(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            "An unexpected error occurred"));
  }
}
