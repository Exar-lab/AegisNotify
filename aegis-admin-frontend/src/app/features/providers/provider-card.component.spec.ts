import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ProviderCircuitBreakerStatus } from '../../core/models/dashboard.models';
import { ProviderCardComponent } from './provider-card.component';

describe('ProviderCardComponent', () => {
  const provider: ProviderCircuitBreakerStatus = {
    key: 'email-provider',
    name: 'SendGrid',
    channel: 'EMAIL',
    role: 'PRIMARY',
    fallbackProvider: 'SendGrid Secondary',
    state: 'CLOSED',
    calls24h: 100,
    successRate: 95,
    failureRate: 5,
    slowCallRate: null,
  };
  beforeEach(() =>
    TestBed.configureTestingModule({
      imports: [ProviderCardComponent],
      providers: [provideRouter([])],
    }),
  );

  it.each([
    [100, 'healthy'],
    [95, 'healthy'],
    [94.9, 'degraded'],
    [70, 'degraded'],
    [69.9, 'open'],
    [0, 'open'],
  ])('renders a %s percent success bar with %s color', (rate, tone) => {
    const fixture = TestBed.createComponent(ProviderCardComponent);
    fixture.componentRef.setInput('provider', { ...provider, successRate: rate });
    fixture.componentRef.setInput('now', Date.now());
    fixture.detectChanges();
    const bar = fixture.nativeElement.querySelector('[role="progressbar"]');
    expect(bar.getAttribute('aria-valuenow')).toBe(String(rate));
    expect(bar.firstElementChild.style.width).toBe(`${rate}%`);
    expect(bar.firstElementChild.classList.contains(tone)).toBe(true);
  });

  it('flags open and recovering circuits, links channel logs, and does not invent missing rates', () => {
    const fixture = TestBed.createComponent(ProviderCardComponent);
    fixture.componentRef.setInput('now', Date.now());
    fixture.componentRef.setInput('provider', { ...provider, state: 'OPEN', successRate: null });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('article').classList.contains('open')).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('primary calls are routed to fallback');
    expect(fixture.nativeElement.textContent).toContain('No calls');
    expect(fixture.nativeElement.querySelector('[role="progressbar"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('a').getAttribute('href')).toBe(
      '/notifications?channel=EMAIL',
    );
    fixture.componentRef.setInput('provider', { ...provider, state: 'HALF_OPEN' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('article').classList.contains('degraded')).toBe(
      true,
    );
    expect(fixture.nativeElement.textContent).toContain('Recovery in progress');
  });

  it('shows relative time and clamps an invalid bar range', () => {
    const fixture = TestBed.createComponent(ProviderCardComponent);
    fixture.componentRef.setInput('now', Date.parse('2026-10-15T12:00:00Z'));
    fixture.componentRef.setInput('provider', {
      ...provider,
      successRate: 150,
      lastStateChange: '2026-10-15T11:55:00Z',
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('State changed 5m ago');
    expect(
      fixture.nativeElement.querySelector('[role="progressbar"]').getAttribute('aria-valuenow'),
    ).toBe('100');
  });
});
