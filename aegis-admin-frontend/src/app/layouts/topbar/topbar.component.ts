import { Component } from '@angular/core';

@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [],
  templateUrl: './topbar.component.html',
  styleUrl: './topbar.component.scss'
})
export class TopbarComponent {
  readonly currentUser = 'aegis-dev';
  readonly userRole = 'Administrator';
  readonly environmentName = 'Local Development';
}
