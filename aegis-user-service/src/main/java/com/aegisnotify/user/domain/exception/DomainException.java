package com.aegisnotify.user.domain.exception;

/**
 * Base type for all {@code aegis-user-service} domain exceptions, matching
 * the {@code aegis-audit-service} convention of the same name.
 */
public class DomainException extends RuntimeException {

  public DomainException(String message) {
    super(message);
  }

  public DomainException(String message, Throwable cause) {
    super(message, cause);
  }
}
