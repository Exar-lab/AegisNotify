import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { TemplateResponse } from '../../../core/models/template.models';
import { TemplatesPage } from './templates.page';

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

describe('TemplatesPage', () => {
  let http: HttpTestingController;
  const url = `${environment.apiBaseUrl.replace(/\/+$/, '')}/api/v1/templates`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TemplatesPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists templates with status badges and edit links', () => {
    const fixture = TestBed.createComponent(TemplatesPage);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Loading templates');
    http.expectOne(url).flush([template, { ...template, id: 'tpl-2', name: 'otp', active: false }]);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelectorAll('tbody tr').length).toBe(2);
    expect(element.textContent).toContain('Active');
    expect(element.textContent).toContain('Inactive');
    expect(element.querySelector('td a')?.getAttribute('href')).toBe('/templates/tpl-1/edit');
  });

  it('toggles status via PATCH and reloads the list', () => {
    const fixture = TestBed.createComponent(TemplatesPage);
    fixture.detectChanges();
    http.expectOne(url).flush([template]);
    fixture.detectChanges();
    fixture.componentInstance.toggleStatus(template);
    const patch = http.expectOne(`${url}/tpl-1/status`);
    expect(patch.request.method).toBe('PATCH');
    expect(patch.request.body).toEqual({ active: false });
    patch.flush({ ...template, active: false });
    http.expectOne(url).flush([{ ...template, active: false }]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Activate');
  });

  it('shows an empty state and supports retry after failure', () => {
    const fixture = TestBed.createComponent(TemplatesPage);
    fixture.detectChanges();
    http.expectOne(url).flush('error', { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain("Couldn't load templates");
    fixture.componentInstance.load();
    http.expectOne(url).flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No templates yet');
  });
});
