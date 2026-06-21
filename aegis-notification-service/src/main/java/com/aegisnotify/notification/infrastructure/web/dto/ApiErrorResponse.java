package com.aegisnotify.notification.infrastructure.web.dto;

import java.time.Instant;

public record ApiErrorResponse(
    int status,
    String error,
    String message,
    Instant timestamp
) {

  public static ApiErrorResponse of(int status, String error, String message) {
    return new ApiErrorResponse(status, error, message, Instant.now());
  }
}
