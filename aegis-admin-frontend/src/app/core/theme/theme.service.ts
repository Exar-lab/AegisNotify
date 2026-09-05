import { Injectable, signal } from '@angular/core';

/**
 * Service managing the application's light/dark theme.
 * Handles persistence in localStorage and updates the DOM accordingly.
 */
@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private readonly darkThemeMedia = window.matchMedia('(prefers-color-scheme: dark)');
  
  /** Reactive signal indicating whether the dark theme is currently active. */
  readonly isDarkTheme = signal<boolean>(false);

  constructor() {
    // Check local storage or system preference to initialize the theme
    const savedTheme = localStorage.getItem('aegis-theme');
    
    if (savedTheme === 'dark') {
      this.setDarkTheme(true);
    } else if (savedTheme === 'light') {
      this.setDarkTheme(false);
    } else {
      // Default is light according to requirements
      this.setDarkTheme(false);
    }
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

