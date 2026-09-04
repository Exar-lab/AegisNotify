import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = async () => {
  const authService = inject(AuthService);
  if (authService.isAuthenticated()) {
    return true;
  }
  await authService.login();
  return false;
};
