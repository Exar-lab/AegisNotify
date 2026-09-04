import { Injectable, inject } from '@angular/core';
import Keycloak from 'keycloak-js';
import { KeycloakService } from 'keycloak-angular';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly keycloakJs = inject(Keycloak, { optional: true });
  private readonly keycloakService = inject(KeycloakService, { optional: true });

  isAuthenticated(): boolean {
    if (this.keycloakJs) {
      return !!this.keycloakJs.authenticated;
    }
    if (this.keycloakService) {
      return this.keycloakService.isLoggedIn();
    }
    return false;
  }

  async login(): Promise<void> {
    if (this.keycloakJs) {
      await this.keycloakJs.login();
    } else if (this.keycloakService) {
      await this.keycloakService.login();
    }
  }

  async logout(): Promise<void> {
    const redirectUri = window.location.origin;
    if (this.keycloakJs) {
      await this.keycloakJs.logout({ redirectUri });
    } else if (this.keycloakService) {
      await this.keycloakService.logout(redirectUri);
    }
  }

  getUsername(): string {
    if (this.keycloakJs?.tokenParsed) {
      return (this.keycloakJs.tokenParsed['preferred_username'] as string) ?? '';
    }
    if (this.keycloakService) {
      return this.keycloakService.getUsername() || '';
    }
    return '';
  }

  getDisplayName(): string {
    const token = this.keycloakJs?.tokenParsed ?? this.keycloakService?.getKeycloakInstance()?.tokenParsed;
    if (!token) {
      return this.getUsername();
    }
    return (token['name'] as string) || (token['preferred_username'] as string) || '';
  }
}
