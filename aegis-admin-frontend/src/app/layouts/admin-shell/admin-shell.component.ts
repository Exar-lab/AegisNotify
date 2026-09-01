import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { TopbarComponent } from '../topbar/topbar.component';

@Component({
  selector: 'app-admin-shell',
  standalone: true,
  imports: [RouterOutlet, SidebarComponent, TopbarComponent],
  templateUrl: './admin-shell.component.html',
  styleUrl: './admin-shell.component.scss'
})
export class AdminShellComponent {}
