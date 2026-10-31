import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { loadRuntimeConfig, RUNTIME_CONFIG } from './runtime-config';

describe('runtime configuration', () => {
  const defaults = {
    apiBaseUrl: environment.apiBaseUrl,
    keycloakUrl: environment.keycloakUrl,
  };

  afterEach(() => vi.restoreAllMocks());

  it('keeps environment defaults available to services without bootstrap overrides', () => {
    expect(TestBed.inject(RUNTIME_CONFIG)).toEqual(defaults);
  });

  it('loads and normalizes deployment URLs without using the HTTP interceptor or cache', async () => {
    const fetchConfig = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          apiBaseUrl: ' https://gateway.example:9443/prefix/// ',
          keycloakUrl: 'https://identity.example/auth/',
        }),
      ),
    );

    expect(await loadRuntimeConfig(fetchConfig)).toEqual({
      apiBaseUrl: 'https://gateway.example:9443/prefix',
      keycloakUrl: 'https://identity.example/auth',
    });
    expect(fetchConfig).toHaveBeenCalledWith(
      new URL('config.json', document.baseURI).href,
      expect.objectContaining({ cache: 'no-store', signal: expect.any(AbortSignal) }),
    );
  });

  it('preserves defaults for absent fields and ignores unknown keys', async () => {
    const fetchConfig = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          apiBaseUrl: 'http://localhost:9090',
          keycloakRealm: 'ignored',
        }),
      ),
    );
    expect(await loadRuntimeConfig(fetchConfig)).toEqual({
      ...defaults,
      apiBaseUrl: 'http://localhost:9090',
    });
  });

  it.each([
    null,
    123,
    '',
    '   ',
    'javascript:alert(1)',
    '/relative',
    'https://user:password@example.com',
    'https://example.com?token=x',
    'https://example.com/#x',
  ])('falls back for invalid URL %j without losing valid fields', async (apiBaseUrl) => {
    const fetchConfig = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          apiBaseUrl,
          keycloakUrl: 'https://identity.example',
        }),
      ),
    );
    expect(await loadRuntimeConfig(fetchConfig)).toEqual({
      ...defaults,
      keycloakUrl: 'https://identity.example',
    });
  });

  it('quietly uses defaults when the optional file is missing', async () => {
    const warning = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const fetchConfig = vi
      .fn<typeof fetch>()
      .mockResolvedValue(new Response(null, { status: 404 }));
    expect(await loadRuntimeConfig(fetchConfig)).toEqual(defaults);
    expect(warning).not.toHaveBeenCalled();
  });

  it.each(['{}', 'null', '[]', '"text"', '{invalid', '<html>SPA fallback</html>'])(
    'handles empty or invalid configuration: %s',
    async (body) => {
      vi.spyOn(console, 'warn').mockImplementation(() => {});
      const fetchConfig = vi.fn<typeof fetch>().mockResolvedValue(new Response(body));
      expect(await loadRuntimeConfig(fetchConfig)).toEqual(defaults);
    },
  );

  it('uses defaults on server errors', async () => {
    vi.spyOn(console, 'warn').mockImplementation(() => {});
    const fetchConfig = vi
      .fn<typeof fetch>()
      .mockResolvedValue(new Response(null, { status: 500 }));
    expect(await loadRuntimeConfig(fetchConfig)).toEqual(defaults);
  });

  it.each([new TypeError('Network failure'), new DOMException('Timeout', 'TimeoutError')])(
    'uses defaults on network or timeout errors',
    async (error) => {
      vi.spyOn(console, 'warn').mockImplementation(() => {});
      const fetchConfig = vi.fn<typeof fetch>().mockRejectedValue(error);
      expect(await loadRuntimeConfig(fetchConfig)).toEqual(defaults);
    },
  );

  it('reads changed URLs on a new load without changing environment defaults', async () => {
    const fetchConfig = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(new Response('{"apiBaseUrl":"http://localhost:9090"}'))
      .mockResolvedValueOnce(new Response('{"apiBaseUrl":"http://localhost:9091"}'));
    expect((await loadRuntimeConfig(fetchConfig)).apiBaseUrl).toBe('http://localhost:9090');
    expect((await loadRuntimeConfig(fetchConfig)).apiBaseUrl).toBe('http://localhost:9091');
    expect(environment.apiBaseUrl).toBe(defaults.apiBaseUrl);
  });
});
