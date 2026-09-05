import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Root component of the application.
 * Serves as the entry point rendering the primary router outlet.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  /** Application title signal used primarily for internal state tracking. */
  protected readonly title = signal('aegis-admin-frontend');
}
