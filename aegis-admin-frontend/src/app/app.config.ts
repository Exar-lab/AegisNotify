import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  provideKeycloak,
  includeBearerTokenInterceptor,
  withAutoRefreshToken,
  AutoRefreshTokenService,
  UserActivityService,
  INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
} from 'keycloak-angular';

import { environment } from '../environments/environment';
import { routes } from './app.routes';

const normalizedApiBaseUrl = environment.apiBaseUrl.replace(/\/+$/, '');
const apiBaseEndpoint = normalizedApiBaseUrl.endsWith('/api')
  ? normalizedApiBaseUrl
  : `${normalizedApiBaseUrl}/api`;
const escapedBaseUrl = normalizedApiBaseUrl.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const escapedApiEndpoint = apiBaseEndpoint.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
// Covers both the REST API (/api/**, gateway-routed) and the actuator
// endpoints the Providers/Dashboard screens read directly (e.g.
// /actuator/circuitbreakers) — both sit behind the same gateway and the
// same JWT, so both need the bearer token attached.
const bearerTokenUrlPattern = new RegExp(
  `^(${escapedApiEndpoint}|${escapedBaseUrl}/actuator)(\\/.*)?$`, 'i'
);

/**
 * Main application configuration block for Angular.
 * Sets up routing, HTTP interceptors, and Keycloak integration for authentication.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(
      withInterceptors([includeBearerTokenInterceptor]),
    ),
    provideKeycloak({
      config: {
        url: environment.keycloakUrl,
        realm: environment.keycloakRealm,
        clientId: environment.keycloakClientId,
      },
      initOptions: {
        onLoad: 'check-sso',
        silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
        flow: 'standard'
      },
      features: [
        withAutoRefreshToken({
          onInactivityTimeout: 'none',
          sessionTimeout: 300000,
        }),
      ],
      providers: [
        AutoRefreshTokenService,
        UserActivityService,
        {
          provide: INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
          useValue: [
            {
              urlPattern: bearerTokenUrlPattern,
              bearerPrefix: 'Bearer',
            },
          ],
        },
      ],
    }),
  ],
};