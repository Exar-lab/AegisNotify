import { Component, HostListener, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { TopbarComponent } from '../topbar/topbar.component';
import { SidebarService } from '../../core/layout/sidebar.service';

@Component({
  selector: 'app-admin-shell',
  standalone: true,
  imports: [RouterOutlet, SidebarComponent, TopbarComponent],
  templateUrl: './admin-shell.component.html',
  styleUrl: './admin-shell.component.scss'
})
export class AdminShellComponent {
  readonly sidebarService = inject(SidebarService);

  @HostListener('window:resize')
  onResize(): void {
    if (!this.sidebarService.isMobile()) {
      this.sidebarService.close();
    }
  }
}
