import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import Keycloak from 'keycloak-js';
import { provideKeycloak, INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG } from 'keycloak-angular';
import { environment } from '../environments/environment';
import { createAppConfig } from './app.config';
import { RUNTIME_CONFIG } from './core/config/runtime-config';

vi.mock('keycloak-angular', async (importOriginal) => {
  const original = await importOriginal<typeof import('keycloak-angular')>();
  return Object.assign({}, original, { provideKeycloak: vi.fn(original.provideKeycloak) });
});

describe('runtime authentication configuration', () => {
  afterEach(() => vi.clearAllMocks());

  it('initializes Keycloak and bearer matching with the effective deployment URLs', () => {
    const config = {
      apiBaseUrl: 'https://gateway.example:9443/admin',
      keycloakUrl: 'https://identity.example/auth',
    };
    createAppConfig(config);
    const options = vi.mocked(provideKeycloak).mock.calls[0][0];
    expect(options.config).toEqual({
      url: config.keycloakUrl,
      realm: environment.keycloakRealm,
      clientId: environment.keycloakClientId,
    });
    expect(options.initOptions).toEqual(
      expect.objectContaining({ onLoad: 'check-sso', flow: 'standard' }),
    );

    const tokenConfig = options.providers!.find(
      (provider) =>
        typeof provider === 'object' &&
        provider !== null &&
        'provide' in provider &&
        provider.provide === INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
    );
    expect(tokenConfig).toBeDefined();
  });

  it('attaches tokens only to the configured API and preserves the runtime injection value', async () => {
    const config = {
      apiBaseUrl: 'https://gateway.example:9443/admin',
      keycloakUrl: 'https://identity.example',
    };
    const appConfig = createAppConfig(config);
    TestBed.configureTestingModule({
      providers: [
        ...appConfig.providers,
        {
          provide: Keycloak,
          useValue: {
            authenticated: true,
            token: 'runtime-token',
            updateToken: vi.fn().mockResolvedValue(true),
          },
        },
        provideHttpClientTesting(),
      ],
    });
    expect(TestBed.inject(RUNTIME_CONFIG)).toBe(config);
    const http = TestBed.inject(HttpClient);
    const controller = TestBed.inject(HttpTestingController);
    for (const [url, authorized] of [
      [`${config.apiBaseUrl}/api/v1/notifications`, true],
      [`${config.apiBaseUrl}/api`, true],
      [`${config.apiBaseUrl}/api-other`, false],
      ['https://gatewayXexample:9443/admin/api/v1/notifications', false],
      ['http://localhost:8080/api/v1/notifications', false],
      ['https://unrelated.example/api/v1/notifications', false],
    ] as const) {
      http.get(url).subscribe();
      await vi.waitFor(() => {
        const request = controller.expectOne(url);
        expect(request.request.headers.get('Authorization')).toBe(
          authorized ? 'Bearer runtime-token' : null,
        );
        request.flush({});
      });
    }
    controller.verify();
  });
});
