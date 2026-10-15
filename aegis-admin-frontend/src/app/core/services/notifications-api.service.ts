import { NotificationPage } from '../models/notification-list.models';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { NotificationDetail } from '../models/notification-detail.models';

@Injectable({ providedIn: 'root' })
export class NotificationsApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl.replace(/\/+$/, '');

  list(query: Record<string, string | number>): Observable<NotificationPage> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(query)) {
      if (value !== '') params = params.set(key, value);
    }
    return this.http.get<NotificationPage>(`${this.baseUrl}/api/v1/notifications`, { params });
  }

  getStatus(id: string): Observable<NotificationDetail> {
    return this.http.get<NotificationDetail>(
      `${this.baseUrl}/api/v1/notifications/${encodeURIComponent(id)}/status`,
    );
  }
}
