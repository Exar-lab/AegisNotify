import { TestBed } from '@angular/core/testing';
import Keycloak from 'keycloak-js';
import { AuthService } from './auth.service';

function setup(keycloak: Partial<Keycloak> | null): AuthService {
  TestBed.configureTestingModule({
    providers: keycloak ? [{ provide: Keycloak, useValue: keycloak }] : [],
  });
  return TestBed.inject(AuthService);
}

describe('AuthService.hasScope', () => {
  it('is true when the token scope claim contains the scope', () => {
    const auth = setup({
      authenticated: true,
      tokenParsed: { scope: 'openid profile notification:read notification:write' },
    });
    expect(auth.hasScope('notification:write')).toBe(true);
    expect(auth.hasScope('notification:read')).toBe(true);
  });

  it('is false when the scope is missing', () => {
    const auth = setup({ authenticated: true, tokenParsed: { scope: 'openid notification:read' } });
    expect(auth.hasScope('notification:write')).toBe(false);
  });

  it('matches whole scopes only, not substrings', () => {
    const auth = setup({ authenticated: true, tokenParsed: { scope: 'notification:write-all' } });
    expect(auth.hasScope('notification:write')).toBe(false);
  });

  it('is false when the scope claim is absent or not a string', () => {
    expect(setup({ authenticated: true, tokenParsed: {} }).hasScope('notification:write')).toBe(false);
    TestBed.resetTestingModule();
    expect(
      setup({ authenticated: true, tokenParsed: { scope: ['notification:write'] } }).hasScope(
        'notification:write',
      ),
    ).toBe(false);
  });

  it('is false when there is no token or no Keycloak instance', () => {
    expect(setup({ authenticated: false }).hasScope('notification:write')).toBe(false);
    TestBed.resetTestingModule();
    expect(setup(null).hasScope('notification:write')).toBe(false);
  });
});
