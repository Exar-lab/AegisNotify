import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

/**
 * Root routing configuration for the application.
 * All routes are protected by the authGuard.
 */
export const routes: Routes = [
  {
    path: '',
    canActivate: [authGuard],
    children: []
  }
];
