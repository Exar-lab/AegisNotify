import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { NotificationDetail } from '../models/notification-detail.models';

@Injectable({ providedIn: 'root' })
export class NotificationsApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl.replace(/\/+$/, '');

  getStatus(id: string): Observable<NotificationDetail> {
    return this.http.get<NotificationDetail>(
      `${this.baseUrl}/api/v1/notifications/${encodeURIComponent(id)}/status`,
    );
  }
}
