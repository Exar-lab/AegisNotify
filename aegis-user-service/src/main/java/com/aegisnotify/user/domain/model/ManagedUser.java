package com.aegisnotify.user.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * A user managed through the Keycloak Admin REST API.
 *
 * <p>Plain record with zero framework imports, matching the ArchUnit rule
 * that forbids {@code org.springframework..} dependencies in {@code
 * ..domain..}. Mapped from/to the vendor-specific Keycloak representation
 * exclusively inside {@code infrastructure.keycloak}.</p>
 *
 * @param id Keycloak-assigned user id
 * @param username the login username
 * @param email the user's email address
 * @param firstName the user's first name
 * @param lastName the user's last name
 * @param enabled whether the user can currently authenticate
 * @param createdAt when the user was created in Keycloak
 * @param roles assigned Realm roles
 */
public record ManagedUser(
    String id,
    String username,
    String email,
    String firstName,
    String lastName,
    boolean enabled,
    Instant createdAt,
    List<String> roles) {

  /**
   * Compact constructor enforcing immutability of assigned roles.
   */
  public ManagedUser {
    roles = roles != null ? List.copyOf(roles) : List.of();
  }

  /**
   * Overloaded constructor for backwards compatibility without roles.
   */
  public ManagedUser(
      String id,
      String username,
      String email,
      String firstName,
      String lastName,
      boolean enabled,
      Instant createdAt) {
    this(id, username, email, firstName, lastName, enabled, createdAt, List.of());
  }
}
