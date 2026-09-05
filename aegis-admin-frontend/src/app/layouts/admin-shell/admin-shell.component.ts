import { Component, HostListener, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { TopbarComponent } from '../topbar/topbar.component';
import { SidebarService } from '../../core/layout/sidebar.service';

/**
 * Main application shell component that wraps the overall layout.
 * Contains the sidebar, topbar, and main content router outlet.
 */
@Component({
  selector: 'app-admin-shell',
  standalone: true,
  imports: [RouterOutlet, SidebarComponent, TopbarComponent],
  templateUrl: './admin-shell.component.html',
  styleUrl: './admin-shell.component.scss'
})
export class AdminShellComponent {
  readonly sidebarService = inject(SidebarService);

  /**
   * Listens for window resize events to automatically close the sidebar 
   * when transitioning from mobile to desktop views.
   */
  @HostListener('window:resize')
  onResize(): void {
    if (!this.sidebarService.isMobile()) {
      this.sidebarService.close();
    }
  }
}

