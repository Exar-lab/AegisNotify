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
   * Checks whether the access token's space-delimited `scope` claim contains the given scope.
   * Whole-scope match only; returns false when there is no token or no usable claim.
   */
  hasScope(scope: string): boolean {
    const claim: unknown = this.keycloak?.tokenParsed?.['scope'];
    return typeof claim === 'string' && claim.split(/\s+/).includes(scope);
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
