package com.aegisnotify.user.domain.exception;

/**
 * Thrown when attempting to assign a role that does not exist in the Keycloak realm.
 */
public final class RoleNotFoundException extends DomainException {

  public RoleNotFoundException(String roleName) {
    super("Role not found in realm: " + roleName);
  }
}
