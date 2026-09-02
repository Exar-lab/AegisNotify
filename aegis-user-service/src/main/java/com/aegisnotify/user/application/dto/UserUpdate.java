package com.aegisnotify.user.application.dto;

/**
 * Vendor-neutral request to update a managed user's profile fields.
 * Username is intentionally not included — this port does not support
 * renaming a user's login username, matching Keycloak's own convention of
 * treating username changes as a distinct, more sensitive operation.
 * Lifecycle state ({@code enabled}) is handled exclusively by {@code
 * UserDirectoryPort#setEnabled(String, boolean)}, not by this update.
 *
 * @param email the user's new email address
 * @param firstName the user's new first name
 * @param lastName the user's new last name
 */
public record UserUpdate(String email, String firstName, String lastName) {
}
