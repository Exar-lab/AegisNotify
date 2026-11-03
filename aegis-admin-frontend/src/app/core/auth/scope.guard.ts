import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Scope required to submit notifications (mirrors the gateway's `notification:write`). */
export const NOTIFICATION_WRITE_SCOPE = 'notification:write';

/**
 * Route guard factory that only allows navigation when the access token carries the given scope.
 * Users without it are redirected to the dashboard. Compose with `authGuard` on a parent route.
 */
export const scopeGuard =
  (scope: string): CanActivateFn =>
  () => {
    const auth = inject(AuthService);
    return auth.hasScope(scope) ? true : inject(Router).parseUrl('/dashboard');
  };
