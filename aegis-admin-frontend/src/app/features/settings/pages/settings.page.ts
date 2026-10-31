import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { SettingsShieldComponent } from '../components/settings-shield/settings-shield.component';

/**
 * Settings view: system configuration parameters, runtime environment details,
 * and navigation link to user management.
 */
@Component({
  selector: 'app-settings-page',
  standalone: true,
  imports: [RouterLink, SettingsShieldComponent],
  templateUrl: './settings.page.html',
  styleUrl: './settings.page.scss',
})
export class SettingsPage {
  readonly environmentName = environment.production ? 'Production' : 'Development';
  readonly apiBaseUrl = environment.apiBaseUrl;
}
