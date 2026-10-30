import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RUNTIME_CONFIG } from '../config/runtime-config';
import { DashboardApiService } from './dashboard-api.service';
import { MetricsApiService } from './metrics-api.service';
import { NotificationsApiService } from './notifications-api.service';
import { TemplatesApiService } from './templates-api.service';
import { UsersApiService } from './users-api.service';

describe('runtime API URL across admin services', () => {
  it('uses the injected gateway for every API service, including a deployment path', () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: RUNTIME_CONFIG,
          useValue: {
            apiBaseUrl: 'https://gateway.example:9443/admin/',
            keycloakUrl: 'https://identity.example',
          },
        },
      ],
    });
    TestBed.inject(DashboardApiService).getSummary().subscribe();
    TestBed.inject(MetricsApiService).getSummary().subscribe();
    TestBed.inject(NotificationsApiService).list({ page: 0 }).subscribe();
    TestBed.inject(TemplatesApiService).list().subscribe();
    TestBed.inject(UsersApiService).list().subscribe();

    const http = TestBed.inject(HttpTestingController);
    for (const path of [
      'dashboard/summary',
      'metrics/summary',
      'notifications',
      'templates',
      'users',
    ]) {
      const request = http.expectOne(
        (req) => req.url === `https://gateway.example:9443/admin/api/v1/${path}`,
      );
      request.flush({});
    }
    http.verify();
  });
});
