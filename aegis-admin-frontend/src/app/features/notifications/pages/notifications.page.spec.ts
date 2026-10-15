import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NotificationsPage } from './notifications.page';

describe('NotificationsPage', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [NotificationsPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('loads real totals, applies filters and resets pagination', () => {
    const fixture = TestBed.createComponent(NotificationsPage);
    fixture.detectChanges();
    const request = http.expectOne((req) => req.params.get('page') === '0');
    request.flush({ content: [], totalElements: 45, page: 0, size: 20 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('45 results');
    fixture.componentInstance.go(1);
    http
      .expectOne((req) => req.params.get('page') === '1')
      .flush({ content: [], totalElements: 45, page: 1, size: 20 });
    fixture.componentInstance.filters.search = 'welcome';
    fixture.componentInstance.filters.channel = 'EMAIL';
    fixture.componentInstance.apply();
    http
      .expectOne(
        (req) =>
          req.params.get('page') === '0' &&
          req.params.get('search') === 'welcome' &&
          req.params.get('channel') === 'EMAIL',
      )
      .flush({ content: [], totalElements: 0, page: 0, size: 20 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No notifications found');
  });
  it('rejects reversed dates without sending a request and supports retry after failure', () => {
    const fixture = TestBed.createComponent(NotificationsPage);
    fixture.detectChanges();
    http
      .expectOne((req) => req.params.get('page') === '0')
      .flush('error', { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain("Couldn't load notifications");
    fixture.componentInstance.fromDate = '2026-02-02';
    fixture.componentInstance.toDate = '2026-02-01';
    fixture.componentInstance.apply();
    expect(fixture.componentInstance.dateError()).toBe(true);
    http.expectNone((req) => req.url.includes('/notifications'));
    fixture.componentInstance.load();
    http
      .expectOne((req) => req.params.get('page') === '0')
      .flush({ content: [], totalElements: 0, page: 0, size: 20 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No notifications found');
  });
  it('cancels stale requests when filters change', () => {
    const fixture = TestBed.createComponent(NotificationsPage);
    fixture.detectChanges();
    const first = http.expectOne((req) => req.params.get('page') === '0');
    fixture.componentInstance.filters.priority = 'HIGH';
    fixture.componentInstance.apply();
    expect(first.cancelled).toBe(true);
    http
      .expectOne((req) => req.params.get('priority') === 'HIGH')
      .flush({ content: [], totalElements: 0, page: 0, size: 20 });
  });
});
