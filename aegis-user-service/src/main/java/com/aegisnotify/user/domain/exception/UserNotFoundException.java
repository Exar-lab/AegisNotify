package com.aegisnotify.user.domain.exception;

/**
 * Thrown when a requested Keycloak user id has no matching user.
 */
public final class UserNotFoundException extends DomainException {

  public UserNotFoundException(String userId) {
    super("Managed user not found: " + userId);
  }
}
