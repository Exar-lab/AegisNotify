import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { CreateNotificationRequest } from '../models/notification-create.models';
import { NotificationsApiService } from './notifications-api.service';

describe('NotificationsApiService.create', () => {
  const url = `${environment.apiBaseUrl.replace(/\/+$/, '')}/api/v1/notifications`;

  it('POSTs the request body and returns the accepted notification', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const body: CreateNotificationRequest = {
      channel: 'EMAIL',
      recipient: 'a@b.com',
      templateName: 'welcome',
      parameters: { name: 'Ada' },
      priority: 'HIGH',
    };
    let result: unknown;
    TestBed.inject(NotificationsApiService)
      .create(body)
      .subscribe((response) => (result = response));

    const req = http.expectOne(url);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({ id: 'n-1', status: 'PENDING' }, { status: 202, statusText: 'Accepted' });
    expect(result).toEqual({ id: 'n-1', status: 'PENDING' });
    http.verify();
  });
});
