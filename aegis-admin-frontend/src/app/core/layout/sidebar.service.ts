import { Injectable, signal } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class SidebarService {
  private static readonly MOBILE_BREAKPOINT = 768;

  readonly isOpen = signal(false);

  toggle(): void {
    this.isOpen.update((open) => !open);
  }

  close(): void {
    this.isOpen.set(false);
  }

  isMobile(): boolean {
    return window.innerWidth < SidebarService.MOBILE_BREAKPOINT;
  }
}
