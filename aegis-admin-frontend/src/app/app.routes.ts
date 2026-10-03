import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { AdminShellComponent } from './layouts/admin-shell/admin-shell.component';

/**
 * Root routing configuration for the application.
 * All routes are protected by the authGuard.
 */
export const routes: Routes = [
  {
    path: '',
    canActivate: [authGuard],
    component: AdminShellComponent,
    children: []
  }
];
