import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { TemplateResponse } from '../models/template.models';
import { TemplatesApiService } from './templates-api.service';

const template: TemplateResponse = {
  id: 'tpl-1',
  name: 'welcome',
  channel: 'EMAIL',
  subject: 'Hi',
  body: 'Hello {{name}}',
  variables: ['name'],
  active: true,
  createdAt: '2026-10-01T10:00:00Z',
  updatedAt: '2026-10-02T10:00:00Z',
};

describe('TemplatesApiService', () => {
  let service: TemplatesApiService;
  let http: HttpTestingController;
  const url = `${environment.apiBaseUrl.replace(/\/+$/, '')}/api/v1/templates`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TemplatesApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists, gets, creates, updates and toggles templates against /api/v1/templates', () => {
    service.list().subscribe((data) => expect(data).toEqual([template]));
    const list = http.expectOne(url);
    expect(list.request.method).toBe('GET');
    list.flush([template]);

    service.get('tpl-1').subscribe((data) => expect(data).toEqual(template));
    expect(http.expectOne(`${url}/tpl-1`).request.method).toBe('GET');

    service.create({ name: 'welcome', channel: 'EMAIL', body: 'x' }).subscribe();
    const create = http.expectOne(url);
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual({ name: 'welcome', channel: 'EMAIL', body: 'x' });

    service.update('tpl-1', { body: 'y', variables: ['a'] }).subscribe();
    const update = http.expectOne(`${url}/tpl-1`);
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual({ body: 'y', variables: ['a'] });

    service.setStatus('tpl-1', false).subscribe();
    const status = http.expectOne(`${url}/tpl-1/status`);
    expect(status.request.method).toBe('PATCH');
    expect(status.request.body).toEqual({ active: false });

    http.match(() => true).forEach((req) => req.flush(template));
  });
});
