import { Component, inject } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { SidebarService } from '../../core/layout/sidebar.service';

interface NavItem {
  label: string;
  route: string;
  icon: string;
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss'
})
export class SidebarComponent {
  private readonly router = inject(Router);
  private readonly sidebarService = inject(SidebarService);

  readonly navItems: NavItem[] = [
    { label: 'Dashboard', route: '/dashboard', icon: 'dashboard' },
    { label: 'Notifications', route: '/notifications', icon: 'notifications' },
    { label: 'Providers', route: '/providers', icon: 'providers' },
    { label: 'Metrics', route: '/metrics', icon: 'metrics' },
    { label: 'Settings', route: '/settings', icon: 'settings' },
  ];

  constructor() {
    this.router.events.subscribe((event) => {
      if (event instanceof NavigationEnd && this.sidebarService.isMobile()) {
        this.sidebarService.close();
      }
    });
  }
}
