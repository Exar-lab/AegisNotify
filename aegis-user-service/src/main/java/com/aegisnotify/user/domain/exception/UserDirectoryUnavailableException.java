package com.aegisnotify.user.domain.exception;

/**
 * Thrown when the Keycloak Admin REST API cannot be reached or rejects this
 * service's own service-account credentials (401/403 from Keycloak, or a
 * network-level failure). Maps to a generic 502 at the HTTP boundary — never
 * to the caller's own 403, and never carrying credentials or a raw stack
 * trace in its message.
 */
public final class UserDirectoryUnavailableException extends DomainException {

  public UserDirectoryUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
