import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MetricsSummary } from '../models/metrics.models';

@Injectable({ providedIn: 'root' })
export class MetricsApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl.replace(/\/+$/, '');

  getSummary(): Observable<MetricsSummary> {
    return this.http.get<MetricsSummary>(`${this.baseUrl}/api/v1/metrics/summary`);
  }
}
