import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import {
  DashboardSummary,
  NotificationSummary,
  ProviderCircuitBreakerStatus,
} from '../../core/models/dashboard.models';
import { DashboardApiService } from '../../core/services/dashboard-api.service';
import { DashboardComponent } from './dashboard.component';

describe('DashboardComponent', () => {
  let mockApiService: {
    getSummary: ReturnType<typeof vi.fn>;
    getCircuitBreakers: ReturnType<typeof vi.fn>;
    getNotifications: ReturnType<typeof vi.fn>;
  };

  const sampleSummary: DashboardSummary = {
    totalRequests: {
      currentValue: 1250,
      previousValue: 1000,
      deltaPercent: 25.0,
      deltaPoints: null,
    },
    successRate: {
      currentValue: 99.4,
      previousValue: 98.2,
      deltaPercent: null,
      deltaPoints: 1.2,
    },
    fallbackActivations: {
      currentValue: 12,
      previousValue: 15,
      deltaPercent: -20.0,
      deltaPoints: null,
    },
    avgDeliveryLatency: {
      currentValue: 142,
      previousValue: 155,
      deltaPercent: -8.4,
      deltaPoints: null,
    },
  };

  const sampleProviders: ProviderCircuitBreakerStatus[] = [
    {
      key: 'email-provider',
      name: 'SendGrid',
      channel: 'EMAIL',
      role: 'PRIMARY',
      fallbackProvider: 'SendGrid Secondary',
      state: 'CLOSED',
    },
    {
      key: 'sms-provider',
      name: 'Twilio SMS',
      channel: 'SMS',
      role: 'PRIMARY',
      fallbackProvider: 'Twilio SMS Secondary',
      state: 'HALF_OPEN',
    },
    {
      key: 'whatsapp-provider',
      name: 'Twilio WhatsApp',
      channel: 'WHATSAPP',
      role: 'PRIMARY',
      fallbackProvider: 'Twilio WhatsApp Secondary',
      state: 'CLOSED',
    },
    {
      key: 'push-provider',
      name: 'Firebase Cloud Messaging',
      channel: 'PUSH',
      role: 'PRIMARY',
      fallbackProvider: 'Firebase Secondary',
      state: 'OPEN',
    },
  ];

  const sampleNotifications: NotificationSummary[] = [
    {
      id: 'notif-101',
      channel: 'EMAIL',
      recipient: 'user@example.com',
      templateName: 'order-confirmation',
      status: 'SENT',
      priority: 'HIGH',
      createdAt: '2026-10-13T10:00:00Z',
    },
    {
      id: 'notif-102',
      channel: 'SMS',
      recipient: '+1234567890',
      templateName: 'otp-token',
      status: 'FAILED_CRITICAL',
      priority: 'MEDIUM',
      createdAt: '2026-10-13T10:05:00Z',
    },
  ];

  beforeEach(async () => {
    mockApiService = {
      getSummary: vi.fn().mockReturnValue(of(sampleSummary)),
      getCircuitBreakers: vi.fn().mockReturnValue(of(sampleProviders)),
      getNotifications: vi.fn().mockReturnValue(of(sampleNotifications)),
    };

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideRouter([]),
        { provide: DashboardApiService, useValue: mockApiService },
      ],
    }).compileComponents();
  });

  it('renders all 4 KPI cards with their corresponding values and deltas', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const cards = compiled.querySelectorAll('.kpi-card');
    expect(cards.length).toBe(4);

    const labels = Array.from(compiled.querySelectorAll('.kpi-label')).map(
      (el) => el.textContent?.trim()
    );
    expect(labels).toEqual([
      'Total Requests (24h)',
      'Success Rate',
      'Fallback Activations',
      'Avg Delivery Latency',
    ]);

    const values = Array.from(compiled.querySelectorAll('.kpi-value')).map(
      (el) => el.textContent?.trim()
    );
    expect(values[0]).toBe('1,250');
    expect(values[1]).toBe('99.40%');
    expect(values[2]).toBe('12');
    expect(values[3]).toBe('142 ms');

    const deltas = Array.from(compiled.querySelectorAll('.kpi-delta')).map(
      (el) => el.textContent?.trim()
    );
    expect(deltas[0]).toBe('+25.0% vs yesterday');
    expect(deltas[1]).toBe('+1.20% vs yesterday');
    expect(deltas[2]).toBe('-20.0% vs yesterday');
    expect(deltas[3]).toBe('-8.4% vs yesterday');
  });

  it('renders provider circuit breakers with expected providers and badges', async () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const rows = compiled.querySelectorAll('.provider-row');
    expect(rows.length).toBe(4);

    const names = Array.from(compiled.querySelectorAll('.provider-name')).map(
      (el) => el.textContent?.trim()
    );
    expect(names).toEqual([
      'SendGrid',
      'Twilio SMS',
      'Twilio WhatsApp',
      'Firebase Cloud Messaging',
    ]);

    const badges = Array.from(
      compiled.querySelectorAll('.provider-table .badge')
    );
    expect(badges[0].textContent?.trim()).toBe('CLOSED');
    expect(badges[0].classList.contains('badge-success')).toBe(true);

    expect(badges[1].textContent?.trim()).toBe('HALF_OPEN');
    expect(badges[1].classList.contains('badge-warning')).toBe(true);

    expect(badges[2].textContent?.trim()).toBe('CLOSED');
    expect(badges[2].classList.contains('badge-success')).toBe(true);

    expect(badges[3].textContent?.trim()).toBe('OPEN');
    expect(badges[3].classList.contains('badge-danger')).toBe(true);
  });

  it('navigates to /notifications/:id when clicking a notification row', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate');

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const firstRow = compiled.querySelector(
      '.notification-row'
    ) as HTMLElement;
    expect(firstRow).not.toBeNull();

    firstRow.click();
    fixture.detectChanges();

    expect(navigateSpy).toHaveBeenCalledWith(['/notifications', 'notif-101']);
  });

  it('preserves circuit breakers and notifications panels when getSummary fails', async () => {
    mockApiService.getSummary.mockReturnValue(
      throwError(() => new Error('API failure'))
    );

    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    // KPI error displayed
    expect(compiled.querySelector('.kpi-error')).not.toBeNull();
    expect(compiled.textContent).toContain("Couldn't load dashboard KPIs.");

    // Circuit breakers panel still renders properly
    const providerRows = compiled.querySelectorAll('.provider-row');
    expect(providerRows.length).toBe(4);

    // Recent notifications panel still renders properly
    const notificationRows = compiled.querySelectorAll('.notification-row');
    expect(notificationRows.length).toBe(2);
  });
});
