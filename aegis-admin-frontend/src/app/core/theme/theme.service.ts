import { Injectable, signal } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private readonly darkThemeMedia = window.matchMedia('(prefers-color-scheme: dark)');
  readonly isDarkTheme = signal<boolean>(false);

  constructor() {
    // Check local storage or system preference
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

  toggleTheme(): void {
    this.setDarkTheme(!this.isDarkTheme());
  }

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
