import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ProvidersPage } from './providers.page';

describe('ProvidersPage', () => {
  let http: HttpTestingController;
  const endpoint = (request: { url: string }) =>
    request.url.endsWith('/dashboard/circuit-breakers');
  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      imports: [ProvidersPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    TestBed.resetTestingModule();
    http.verify();
    vi.useRealTimers();
  });

  it('groups primary and fallback, refreshes live states, retains stale data on errors and stops polling on destroy', () => {
    const fixture = TestBed.createComponent(ProvidersPage);
    fixture.detectChanges();
    vi.advanceTimersByTime(0);
    const primary = {
      key: 'email-provider',
      name: 'SendGrid',
      channel: 'EMAIL',
      role: 'PRIMARY',
      fallbackProvider: 'Secondary',
      state: 'CLOSED',
      callsToday: 5,
      calls24h: 10,
    };
    const fallback = {
      ...primary,
      key: 'email-fallback',
      name: 'Secondary',
      role: 'FALLBACK',
      state: 'NOT_PROTECTED',
      callsToday: 2,
    };
    http.expectOne(endpoint).flush([fallback, primary]);
    fixture.detectChanges();
    expect(fixture.componentInstance.summary()).toEqual({
      healthy: 1,
      degraded: 0,
      open: 0,
      calls: 7,
    });
    const cards = fixture.nativeElement.querySelectorAll('app-provider-card');
    expect(cards[0].textContent).toContain('SendGrid');
    expect(cards[1].textContent).toContain('Secondary');
    expect(fixture.nativeElement.querySelectorAll('section')).toHaveLength(4);
    vi.advanceTimersByTime(15_000);
    http.expectOne(endpoint).flush('error', { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('last successful snapshot');
    expect(fixture.nativeElement.querySelectorAll('app-provider-card')).toHaveLength(2);
    fixture.componentInstance.refresh();
    http.expectOne(endpoint).flush([{ ...primary, state: 'OPEN' }]);
    fixture.detectChanges();
    expect(fixture.componentInstance.summary().open).toBe(1);
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
    fixture.destroy();
    vi.advanceTimersByTime(30_000);
    http.expectNone(endpoint);
  });

  it('supports initial failure, retry and an empty provider list without overlapping requests', () => {
    const fixture = TestBed.createComponent(ProvidersPage);
    fixture.detectChanges();
    vi.advanceTimersByTime(0);
    fixture.componentInstance.refresh();
    http.expectOne(endpoint).flush('error', { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain("Couldn't load provider health");
    fixture.componentInstance.refresh();
    http.expectOne(endpoint).flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No providers available');
    fixture.destroy();
  });
});
