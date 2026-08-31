package com.aegisnotify.user.infrastructure.web;

import com.aegisnotify.user.domain.exception.UserAlreadyExistsException;
import com.aegisnotify.user.domain.exception.UserDirectoryUnavailableException;
import com.aegisnotify.user.domain.exception.UserNotFoundException;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for the user-service REST API, mirroring {@code
 * aegis-audit-service}'s {@code GlobalExceptionHandler} shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(UserNotFoundException.class)
  public ResponseEntity<Map<String, Object>> handleNotFound(UserNotFoundException ex) {
    return problem(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(UserAlreadyExistsException.class)
  public ResponseEntity<Map<String, Object>> handleAlreadyExists(UserAlreadyExistsException ex) {
    return problem(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(UserDirectoryUnavailableException.class)
  public ResponseEntity<Map<String, Object>> handleUnavailable(
      UserDirectoryUnavailableException ex) {
    // Deliberately generic: never surfaces Keycloak's response body,
    // service-account credentials, or a raw stack trace to the caller.
    log.warn("user_directory_unavailable reason={}", ex.getMessage());
    return problem(HttpStatus.BAD_GATEWAY, "The user directory is temporarily unavailable");
  }

  private ResponseEntity<Map<String, Object>> problem(HttpStatus status, String message) {
    Map<String, Object> body = Map.of(
        "message", message,
        "status", status.value(),
        "timestamp", Instant.now().toString()
    );
    return ResponseEntity.status(status).body(body);
  }
}
