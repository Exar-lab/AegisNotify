import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { NotificationDetail } from '../../../core/models/notification-detail.models';
import { environment } from '../../../../environments/environment';
import { NotificationDetailPage } from './notification-detail.page';

const notification: NotificationDetail = {
  priority: 'HIGH',
  correlationId: 'test-id',
  providerAttempts: [
    {
      role: 'PRIMARY',
      providerName: 'SendGrid',
      success: false,
      code: '503',
      errorDetail: 'HTTP 503: unavailable',
      latencyMillis: 42,
      timestamp: '2026-10-14T10:00:30Z',
    },
    {
      role: 'FALLBACK',
      providerName: 'Backup',
      success: true,
      code: '202',
      errorDetail: null,
      latencyMillis: 17,
      timestamp: '2026-10-14T10:01:00Z',
    },
  ],
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

  it('renders structured attempt data and does not fabricate attempts for legacy records', () => {
    const fixture = TestBed.createComponent(NotificationDetailPage);
    http.expectOne(url('test-id')).flush(notification);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('PRIMARY');
    expect(text).toContain('FALLBACK');
    expect(text).toContain('42 ms');
    expect(text).toContain('202');
    params.next(convertToParamMap({ id: 'legacy' }));
    http.expectOne(url('legacy')).flush({ ...notification, providerAttempts: [] });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No individual provider attempts');
  });

  describe('aggregation state', () => {
    const render = (aggregation: NotificationDetail['aggregation']) => {
      const fixture = TestBed.createComponent(NotificationDetailPage);
      http.expectOne(url('test-id')).flush({ ...notification, aggregation });
      fixture.detectChanges();
      return fixture.nativeElement as HTMLElement;
    };

    it('shows nothing for a notification that was never aggregated', () => {
      expect(render(undefined).querySelector('.aggregation')).toBeNull();
      http.verify();
    });

    it('shows nothing when the role is NONE', () => {
      const element = render({ role: 'NONE', aggregationId: null, groupSize: null, leaderId: null });
      expect(element.querySelector('.aggregation')).toBeNull();
    });

    it('shows the leader with its group size', () => {
      const element = render({
        role: 'LEADER',
        aggregationId: 'agg-1',
        groupSize: 3,
        leaderId: 'test-id',
      });
      const row = element.querySelector('.aggregation') as HTMLElement;
      expect(row.textContent).toContain('Aggregate leader');
      expect(row.textContent).toContain('3 notifications');
      expect(row.textContent).toContain('agg-1');
    });

    it('shows a sibling with a link to its leader', () => {
      const element = render({
        role: 'SIBLING',
        aggregationId: 'agg-1',
        groupSize: 3,
        leaderId: 'leader-9',
      });
      const row = element.querySelector('.aggregation') as HTMLElement;
      expect(row.textContent).toContain('Folded into an aggregate');
      expect(row.querySelector('a')?.getAttribute('href')).toBe('/notifications/leader-9');
    });

    it('shows a notification released individually from the buffer', () => {
      const element = render({
        role: 'RELEASED_INDIVIDUALLY',
        aggregationId: null,
        groupSize: null,
        leaderId: null,
      });
      expect(element.querySelector('.aggregation')?.textContent).toContain('Released individually');
    });
  });
});
