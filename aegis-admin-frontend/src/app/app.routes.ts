import { Routes } from '@angular/router';
import { AdminShellComponent } from './layouts/admin-shell/admin-shell.component';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: '',
    component: AdminShellComponent,
    canActivate: [authGuard],
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'dashboard',
      },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/pages/dashboard.page').then(
            (m) => m.DashboardPage,
          ),
      },
      {
        path: 'notifications',
        loadComponent: () =>
          import('./features/notifications/pages/notifications.page').then(
            (m) => m.NotificationsPage,
          ),
      },
      {
        path: 'notifications/:id',
        loadComponent: () =>
          import('./features/notifications/pages/notification-detail.page').then(
            (m) => m.NotificationDetailPage,
          ),
      },
      {
        path: 'providers',
        loadComponent: () =>
          import('./features/providers/pages/providers.page').then(
            (m) => m.ProvidersPage,
          ),
      },
      {
        path: 'metrics',
        loadComponent: () =>
          import('./features/metrics/pages/metrics.page').then(
            (m) => m.MetricsPage,
          ),
      },
      {
        path: 'settings',
        loadComponent: () =>
          import('./features/settings/pages/settings.page').then(
            (m) => m.SettingsPage,
          ),
      },
    ],
  },
  {
    path: '**',
    redirectTo: 'dashboard',
  },
];