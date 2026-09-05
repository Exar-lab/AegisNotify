import { Component, HostListener, inject, signal } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map, startWith } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { ThemeService } from '../../core/theme/theme.service';

@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [],
  templateUrl: './topbar.component.html',
  styleUrl: './topbar.component.scss'
})
export class TopbarComponent {
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);
  readonly themeService = inject(ThemeService);

  readonly notificationsOpen = signal(false);

  toggleNotifications(event?: Event): void {
    event?.stopPropagation();
    this.notificationsOpen.update((open) => !open);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    if (!target.closest('.notifications-wrapper')) {
      this.notificationsOpen.set(false);
    }
  }

  readonly userRole = 'Administrator';
  readonly environmentName = 'Local Development';

  get currentUser(): string {
    return this.authService.getDisplayName() || this.authService.getUsername() || 'aegis-dev';
  }

  readonly currentFeature = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => this.deriveFeatureName(event.urlAfterRedirects || event.url)),
      startWith(this.deriveFeatureName(this.router.url))
    ),
    { initialValue: this.deriveFeatureName(this.router.url) }
  );

  private deriveFeatureName(url: string): string {
    const cleanUrl = url.split('?')[0].split('#')[0];
    if (cleanUrl.includes('/notifications/') && cleanUrl !== '/notifications') {
      return 'Notification Detail';
    }
    if (cleanUrl.startsWith('/dashboard')) {
      return 'Dashboard';
    }
    if (cleanUrl.startsWith('/notifications')) {
      return 'Notifications';
    }
    if (cleanUrl.startsWith('/providers')) {
      return 'Providers';
    }
    if (cleanUrl.startsWith('/metrics')) {
      return 'Metrics';
    }
    if (cleanUrl.startsWith('/settings')) {
      return 'Settings';
    }
    return 'Dashboard';
  }
}
