import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CircuitBreakersResponse,
  DashboardSummary,
  NotificationSummary,
} from '../models/dashboard.models';

/**
 * Talks to the notification service's dashboard-facing endpoints, through
 * the API gateway (see FRONTEND_REQUIREMENTS.md §5.1).
 */
@Injectable({ providedIn: 'root' })
export class DashboardApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl.replace(/\/+$/, '');

  getSummary(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${this.baseUrl}/api/v1/dashboard/summary`);
  }

  getNotifications(): Observable<NotificationSummary[]> {
    return this.http.get<NotificationSummary[]>(`${this.baseUrl}/api/v1/notifications`);
  }

  getCircuitBreakers(): Observable<CircuitBreakersResponse> {
    return this.http.get<CircuitBreakersResponse>(`${this.baseUrl}/actuator/circuitbreakers`);
  }
}
