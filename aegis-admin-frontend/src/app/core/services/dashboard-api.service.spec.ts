import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import {
  DashboardSummary,
  NotificationSummary,
  ProviderCircuitBreakerStatus,
} from '../models/dashboard.models';
import { DashboardApiService } from './dashboard-api.service';

describe('DashboardApiService', () => {
  let service: DashboardApiService;
  let httpMock: HttpTestingController;
  const baseUrl = environment.apiBaseUrl.replace(/\/+$/, '');

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        DashboardApiService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    service = TestBed.inject(DashboardApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should fetch dashboard summary via GET /api/v1/dashboard/summary', () => {
    const mockSummary: DashboardSummary = {
      totalRequests: { currentValue: 1250, previousValue: 1000, deltaPercent: 25.0, deltaPoints: null },
      successRate: { currentValue: 99.4, previousValue: 98.2, deltaPercent: null, deltaPoints: 1.2 },
      fallbackActivations: { currentValue: 12, previousValue: 15, deltaPercent: -20.0, deltaPoints: null },
      avgDeliveryLatency: { currentValue: 245, previousValue: 260, deltaPercent: -5.77, deltaPoints: null },
    };

    service.getSummary().subscribe((data) => {
      expect(data).toEqual(mockSummary);
    });

    const req = httpMock.expectOne(`${baseUrl}/api/v1/dashboard/summary`);
    expect(req.request.method).toBe('GET');
    req.flush(mockSummary);
  });

  it('should fetch recent notifications with default limit=20 via GET /api/v1/notifications?limit=20', () => {
    const mockNotifications: NotificationSummary[] = [
      {
        id: 'notif-1',
        channel: 'EMAIL',
        recipient: 'test@example.com',
        templateName: 'welcome',
        status: 'SENT',
        priority: 'HIGH',
        createdAt: '2026-10-12T12:00:00Z',
      },
    ];

    service.getNotifications().subscribe((data) => {
      expect(data).toEqual(mockNotifications);
    });

    const req = httpMock.expectOne(`${baseUrl}/api/v1/notifications?limit=20`);
    expect(req.request.method).toBe('GET');
    req.flush(mockNotifications);
  });

  it('should fetch recent notifications with custom limit via GET /api/v1/notifications?limit=5', () => {
    const mockNotifications: NotificationSummary[] = [];

    service.getNotifications(5).subscribe((data) => {
      expect(data).toEqual(mockNotifications);
    });

    const req = httpMock.expectOne(`${baseUrl}/api/v1/notifications?limit=5`);
    expect(req.request.method).toBe('GET');
    req.flush(mockNotifications);
  });

  it('should fetch circuit breaker statuses via GET /api/v1/dashboard/circuit-breakers', () => {
    const mockBreakers: ProviderCircuitBreakerStatus[] = [
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
        state: 'OPEN',
      },
    ];

    service.getCircuitBreakers().subscribe((data) => {
      expect(data).toEqual(mockBreakers);
    });

    const req = httpMock.expectOne(`${baseUrl}/api/v1/dashboard/circuit-breakers`);
    expect(req.request.method).toBe('GET');
    req.flush(mockBreakers);
  });
});
