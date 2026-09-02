package com.aegisnotify.user.domain.model;

import java.time.Instant;

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
 */
public record ManagedUser(
    String id,
    String username,
    String email,
    String firstName,
    String lastName,
    boolean enabled,
    Instant createdAt) {
}
