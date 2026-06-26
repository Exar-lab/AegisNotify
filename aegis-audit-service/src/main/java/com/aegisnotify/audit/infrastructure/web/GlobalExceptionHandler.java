package com.aegisnotify.audit.infrastructure.web;

import com.aegisnotify.audit.domain.exception.AuditTrailNotFoundException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for the audit-service REST API.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(AuditTrailNotFoundException.class)
  public ResponseEntity<Map<String, Object>> handleNotFound(
      AuditTrailNotFoundException ex) {
    Map<String, Object> body = Map.of(
        "message", ex.getMessage(),
        "status", HttpStatus.NOT_FOUND.value(),
        "timestamp", Instant.now().toString()
    );
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
  }
}
