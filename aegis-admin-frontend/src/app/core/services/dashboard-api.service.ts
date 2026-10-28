import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  DashboardSummary,
  NotificationSummary,
  ProviderCircuitBreakerStatus,
} from '../models/dashboard.models';

@Injectable({ providedIn: 'root' })
export class DashboardApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl.replace(/\/+$/, '');

  getSummary(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${this.baseUrl}/api/v1/dashboard/summary`);
  }

  getNotifications(limit: number = 20): Observable<NotificationSummary[]> {
    return this.http.get<NotificationSummary[]>(`${this.baseUrl}/api/v1/notifications?limit=${limit}`);
  }

  getCircuitBreakers(): Observable<ProviderCircuitBreakerStatus[]> {
    return this.http.get<ProviderCircuitBreakerStatus[]>(
      `${this.baseUrl}/api/v1/dashboard/circuit-breakers`
    );
  }
}
