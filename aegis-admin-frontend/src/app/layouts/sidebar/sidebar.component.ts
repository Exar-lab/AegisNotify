import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

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
  readonly navItems: NavItem[] = [
    { label: 'Dashboard', route: '/dashboard', icon: 'dashboard' },
    { label: 'Notifications', route: '/notifications', icon: 'notifications' },
    { label: 'Providers', route: '/providers', icon: 'providers' },
    { label: 'Metrics', route: '/metrics', icon: 'metrics' },
    { label: 'Settings', route: '/settings', icon: 'settings' },
  ];
}
