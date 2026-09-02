package com.aegisnotify.user.application.dto;

/**
 * Vendor-neutral request to create a managed user. New users are always
 * created enabled — the adapter never sets {@code enabled=false} at
 * creation; disabling happens exclusively through {@code
 * UserDirectoryPort#setEnabled(String, boolean)}.
 *
 * @param username the login username
 * @param email the user's email address
 * @param firstName the user's first name
 * @param lastName the user's last name
 */
public record NewUser(String username, String email, String firstName, String lastName) {
}
