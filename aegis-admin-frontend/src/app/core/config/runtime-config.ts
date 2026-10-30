import { InjectionToken } from '@angular/core';
import { environment } from '../../../environments/environment';

export interface RuntimeConfig {
  readonly apiBaseUrl: string;
  readonly keycloakUrl: string;
}

const defaults: RuntimeConfig = Object.freeze({
  apiBaseUrl: environment.apiBaseUrl.replace(/\/+$/, ''),
  keycloakUrl: environment.keycloakUrl.replace(/\/+$/, ''),
});

export const RUNTIME_CONFIG = new InjectionToken<RuntimeConfig>('Runtime configuration', {
  providedIn: 'root',
  factory: () => defaults,
});

function resolveUrl(value: unknown, fallback: string): string {
  if (typeof value !== 'string' || !value.trim()) return fallback;
  try {
    const url = new URL(value.trim());
    if (
      !['http:', 'https:'].includes(url.protocol) ||
      url.username ||
      url.password ||
      url.search ||
      url.hash
    )
      return fallback;
    return url.href.replace(/\/+$/, '');
  } catch {
    return fallback;
  }
}

/** Loaded before Angular/Keycloak initialization, without the authenticated HTTP client. */
export async function loadRuntimeConfig(
  fetchConfig: typeof fetch = globalThis.fetch,
): Promise<RuntimeConfig> {
  try {
    const response = await fetchConfig(new URL('config.json', document.baseURI).href, {
      cache: 'no-store',
      signal: AbortSignal.timeout(5000),
    });
    if (response.status === 404) return defaults;
    if (!response.ok) throw new Error('Runtime configuration request failed');
    const config: unknown = await response.json();
    if (config === null || typeof config !== 'object' || Array.isArray(config)) {
      throw new Error('Runtime configuration must be an object');
    }
    const values = config as Record<string, unknown>;
    return Object.freeze({
      apiBaseUrl: resolveUrl(values['apiBaseUrl'], defaults.apiBaseUrl),
      keycloakUrl: resolveUrl(values['keycloakUrl'], defaults.keycloakUrl),
    });
  } catch {
    console.warn('Unable to load config.json; using environment defaults.');
    return defaults;
  }
}
