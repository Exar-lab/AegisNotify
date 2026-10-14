import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import {
  NotificationDetail,
  providerAttempts,
} from '../../../core/models/notification-detail.models';
import { environment } from '../../../../environments/environment';
import { NotificationDetailPage } from './notification-detail.page';

const notification: NotificationDetail = {
  id: 'test-id',
  channel: 'EMAIL',
  recipient: 'operator@example.com',
  templateName: 'welcome',
  status: 'SENT_VIA_FALLBACK',
  createdAt: '2026-10-14T10:00:00Z',
  updatedAt: '2026-10-14T10:01:00Z',
  auditTrail: [
    {
      status: 'SENT_VIA_FALLBACK',
      details: 'Sent via fallback provider Backup',
      timestamp: '2026-10-14T10:01:00Z',
    },
    { status: 'PENDING', details: 'Accepted', timestamp: '2026-10-14T10:00:00Z' },
    {
      status: 'PROVIDER_A_FAIL',
      details: 'HTTP 503: unavailable',
      timestamp: '2026-10-14T10:00:30Z',
    },
  ],
};

describe('NotificationDetailPage', () => {
  let http: HttpTestingController;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  const url = (id: string) => `${environment.apiBaseUrl}/api/v1/notifications/${id}/status`;

  beforeEach(() => {
    params = new BehaviorSubject(convertToParamMap({ id: 'test-id' }));
    TestBed.configureTestingModule({
      imports: [NotificationDetailPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { paramMap: params } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('fetches the status, renders every event chronologically and links to the list', () => {
    const fixture = TestBed.createComponent(NotificationDetailPage);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Loading notification');
    const request = http.expectOne(url('test-id'));
    expect(request.request.method).toBe('GET');
    request.flush(notification);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(
      Array.from(element.querySelectorAll('.timeline h3')).map((node) => node.textContent?.trim()),
    ).toEqual(['PENDING', 'PROVIDER A FAIL', 'SENT VIA FALLBACK']);
    expect(element.textContent).toContain('operator@example.com');
    expect(element.textContent).toContain('HTTP 503: unavailable');
    expect(element.textContent).toContain('Backup');
    expect(element.querySelector('.breadcrumb a')?.getAttribute('href')).toBe('/notifications');
    expect(element.querySelector('.large')?.getAttribute('data-tone')).toBe('fallback');
    expect(
      element.querySelector('.timeline li:nth-child(2) .badge')?.getAttribute('data-tone'),
    ).toBe('danger');
  });

  it('handles a missing notification', () => {
    const fixture = TestBed.createComponent(NotificationDetailPage);
    http.expectOne(url('test-id')).flush({}, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Notification not found');
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });

  it('allows retry after a server error', () => {
    const fixture = TestBed.createComponent(NotificationDetailPage);
    http.expectOne(url('test-id')).flush({}, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Unable to load notification');
    fixture.nativeElement.querySelector('button').click();
    http.expectOne(url('test-id')).flush({ ...notification, auditTrail: [] });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No delivery events');
  });

  it('cancels stale requests when navigating to another notification', () => {
    const fixture = TestBed.createComponent(NotificationDetailPage);
    const stale = http.expectOne(url('test-id'));
    params.next(convertToParamMap({ id: 'other-id' }));
    expect(stale.cancelled).toBe(true);
    http.expectOne(url('other-id')).flush({ ...notification, id: 'other-id' });
    fixture.detectChanges();
    expect(fixture.componentInstance.notification()?.id).toBe('other-id');
  });

  it('distinguishes recorded primary and fallback outcomes without inventing attempts', () => {
    const attempts = providerAttempts(notification.auditTrail);
    expect(attempts.map((attempt) => [attempt.role, attempt.result])).toEqual([
      ['Primary', 'Failure'],
      ['Fallback', 'Success'],
    ]);
    expect(
      providerAttempts([
        { status: 'FAILED_CRITICAL', details: 'Sent to DLQ', timestamp: notification.updatedAt },
      ]),
    ).toEqual([]);
    expect(notification.auditTrail[0].status).toBe('SENT_VIA_FALLBACK');
  });
});
