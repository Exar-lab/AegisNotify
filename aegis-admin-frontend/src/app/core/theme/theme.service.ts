import { Injectable, signal } from '@angular/core';

/**
 * Service managing the application's light/dark theme.
 * Handles persistence in localStorage and updates the DOM accordingly.
 */
@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  /** Reactive signal indicating whether the dark theme is currently active. */
  readonly isDarkTheme = signal<boolean>(false);

  constructor() {
    // Light is the primary look: dark applies only when the user explicitly chose it.
    const savedTheme = localStorage.getItem('aegis-theme');
    this.setDarkTheme(savedTheme === 'dark');
  }

  /**
   * Toggles between light and dark themes.
   */
  toggleTheme(): void {
    this.setDarkTheme(!this.isDarkTheme());
  }

  /**
   * Applies the theme by setting the data attribute on the DOM and saving it to localStorage.
   * @param isDark True to enable dark theme, false for light theme.
   */
  private setDarkTheme(isDark: boolean): void {
    this.isDarkTheme.set(isDark);
    if (isDark) {
      document.documentElement.setAttribute('data-theme', 'dark');
      localStorage.setItem('aegis-theme', 'dark');
    } else {
      document.documentElement.removeAttribute('data-theme');
      localStorage.setItem('aegis-theme', 'light');
    }
  }
}

