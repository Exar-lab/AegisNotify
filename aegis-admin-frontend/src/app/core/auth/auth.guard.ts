import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { createAuthGuard, AuthGuardData } from 'keycloak-angular';

/**
 * Checks if the user is authenticated before allowing access to a route.
 * Redirects to Keycloak login if the user is not authenticated.
 */
const isAccessAllowed = async (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot,
  authData: AuthGuardData
): Promise<boolean> => {
  const { authenticated, keycloak } = authData;

  if (!authenticated) {
    await keycloak.login({
      redirectUri: window.location.origin + state.url,
    });
    return false;
  }

  return true;
};

/**
 * Auth guard instance to be used in Angular route definitions.
 */
export const authGuard = createAuthGuard(isAccessAllowed);

