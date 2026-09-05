import { Injectable, signal } from '@angular/core';

/**
 * Service managing the state of the sidebar (open/closed) and providing
 * utility to check if the current screen size is mobile.
 */
@Injectable({
  providedIn: 'root',
})
export class SidebarService {
  private static readonly MOBILE_BREAKPOINT = 768;

  /** Reactive signal indicating whether the sidebar is currently open. */
  readonly isOpen = signal(false);

  /**
   * Toggles the sidebar open state.
   */
  toggle(): void {
    this.isOpen.update((open) => !open);
  }

  /**
   * Explicitly closes the sidebar.
   */
  close(): void {
    this.isOpen.set(false);
  }

  /**
   * Checks if the current window width falls below the mobile breakpoint.
   */
  isMobile(): boolean {
    return window.innerWidth < SidebarService.MOBILE_BREAKPOINT;
  }
}

