import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { createAuthGuard, AuthGuardData } from 'keycloak-angular';

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

export const authGuard = createAuthGuard(isAccessAllowed);
