import { Injectable, inject } from '@angular/core';
import Keycloak from 'keycloak-js';
import { KeycloakService } from 'keycloak-angular';

/**
 * Service to handle authentication operations like login, logout,
 * and retrieving user information, wrapping Keycloak functionality.
 */
@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly keycloakJs = inject(Keycloak, { optional: true });
  private readonly keycloakService = inject(KeycloakService, { optional: true });

  /**
   * Checks whether the current user is authenticated.
   * Uses Keycloak JS directly or falls back to KeycloakService.
   */
  isAuthenticated(): boolean {
    if (this.keycloakJs) {
      return !!this.keycloakJs.authenticated;
    }
    if (this.keycloakService) {
      return this.keycloakService.isLoggedIn();
    }
    return false;
  }

  /**
   * Triggers the Keycloak login flow.
   */
  async login(): Promise<void> {
    if (this.keycloakJs) {
      await this.keycloakJs.login();
    } else if (this.keycloakService) {
      await this.keycloakService.login();
    }
  }

  /**
   * Triggers the Keycloak logout flow and redirects to the application root.
   */
  async logout(): Promise<void> {
    const redirectUri = window.location.origin;
    if (this.keycloakJs) {
      await this.keycloakJs.logout({ redirectUri });
    } else if (this.keycloakService) {
      await this.keycloakService.logout(redirectUri);
    }
  }

  /**
   * Retrieves the current user's username from the parsed token.
   */
  getUsername(): string {
    if (this.keycloakJs?.tokenParsed) {
      return (this.keycloakJs.tokenParsed['preferred_username'] as string) ?? '';
    }
    if (this.keycloakService) {
      return this.keycloakService.getUsername() || '';
    }
    return '';
  }

  /**
   * Retrieves the user's display name from the token, falling back to username.
   */
  getDisplayName(): string {
    const token = this.keycloakJs?.tokenParsed ?? this.keycloakService?.getKeycloakInstance()?.tokenParsed;
    if (!token) {
      return this.getUsername();
    }
    return (token['name'] as string) || (token['preferred_username'] as string) || '';
  }
}
