package com.aegisnotify.user.domain.exception;

/**
 * Thrown when a create-user request collides with an existing Keycloak
 * username or email. Not consumed until Slice 5b's mutation endpoints;
 * created in Slice 5a per the task list so the full domain exception set
 * lands together.
 */
public final class UserAlreadyExistsException extends DomainException {

  public UserAlreadyExistsException(String username) {
    super("User already exists: " + username);
  }
}
