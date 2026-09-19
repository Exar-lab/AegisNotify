import { Injectable, inject } from '@angular/core';
import Keycloak from 'keycloak-js';

/**
 * Service to handle authentication operations like login, logout,
 * and retrieving user information, wrapping Keycloak functionality.
 */
@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly keycloak = inject(Keycloak, { optional: true });

  /**
   * Checks whether the current user is authenticated.
   */
  isAuthenticated(): boolean {
    return !!this.keycloak?.authenticated;
  }

  /**
   * Triggers the Keycloak login flow.
   */
  async login(): Promise<void> {
    await this.keycloak?.login();
  }

  /**
   * Triggers the Keycloak logout flow and redirects to the application root.
   */
  async logout(): Promise<void> {
    const redirectUri = window.location.origin;
    await this.keycloak?.logout({ redirectUri });
  }

  /**
   * Retrieves the current user's username from the parsed token.
   */
  getUsername(): string {
    if (this.keycloak?.tokenParsed) {
      return (this.keycloak.tokenParsed['preferred_username'] as string) ?? '';
    }
    return '';
  }

  /**
   * Retrieves the user's display name from the token, falling back to username.
   */
  getDisplayName(): string {
    const token = this.keycloak?.tokenParsed;
    if (!token) {
      return this.getUsername();
    }
    return (token['name'] as string) || (token['preferred_username'] as string) || '';
  }
}
