import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { TemplateResponse } from '../../../core/models/template.models';
import { TemplateFormPage, parseVariables } from './template-form.page';

const template: TemplateResponse = {
  id: 'tpl-1',
  name: 'welcome',
  channel: 'SMS',
  subject: null,
  body: 'Hello {{name}}',
  variables: ['name', 'code'],
  active: true,
  createdAt: '2026-10-01T10:00:00Z',
  updatedAt: '2026-10-02T10:00:00Z',
};

describe('TemplateFormPage', () => {
  let http: HttpTestingController;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  const url = `${environment.apiBaseUrl.replace(/\/+$/, '')}/api/v1/templates`;

  function setup(id?: string) {
    params = new BehaviorSubject(convertToParamMap(id ? { id } : {}));
    TestBed.configureTestingModule({
      imports: [TemplateFormPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { paramMap: params } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(TemplateFormPage);
    fixture.detectChanges();
    return { fixture, navigate };
  }

  afterEach(() => http?.verify());

  it('parses comma-separated variables', () => {
    expect(parseVariables(' a, b,,a\nc ')).toEqual(['a', 'b', 'c']);
  });

  it('validates required fields and does not submit an invalid form', () => {
    const { fixture } = setup();
    fixture.componentInstance.submit();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Name, channel and body are required');
    http.expectNone(url);
  });

  it('creates a template and surfaces a 409 duplicate-name error', () => {
    const { fixture, navigate } = setup();
    const page = fixture.componentInstance;
    page.form.setValue({
      name: 'welcome',
      channel: 'EMAIL',
      subject: 'Hi',
      body: 'Hello {{name}}',
      variables: 'name',
    });
    page.submit();
    const conflict = http.expectOne(url);
    expect(conflict.request.method).toBe('POST');
    expect(conflict.request.body).toEqual({
      name: 'welcome',
      channel: 'EMAIL',
      subject: 'Hi',
      body: 'Hello {{name}}',
      variables: ['name'],
    });
    conflict.flush(
      { status: 409, error: 'Conflict', message: 'duplicate' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('A template named "welcome" already exists.');
    expect(navigate).not.toHaveBeenCalled();

    page.submit();
    http.expectOne(url).flush(template);
    expect(navigate).toHaveBeenCalledWith(['/templates']);
  });

  it('loads an existing template, locks name/channel and updates via PUT', () => {
    const { fixture, navigate } = setup('tpl-1');
    http.expectOne(`${url}/tpl-1`).flush(template);
    fixture.detectChanges();
    const page = fixture.componentInstance;
    expect(page.form.controls.name.disabled).toBe(true);
    expect(page.form.controls.channel.disabled).toBe(true);
    expect(page.form.controls.variables.value).toBe('name, code');
    expect(fixture.nativeElement.textContent).toContain('Edit template');

    page.form.controls.body.setValue('Your code is {{code}}');
    page.submit();
    const put = http.expectOne(`${url}/tpl-1`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({
      subject: undefined,
      body: 'Your code is {{code}}',
      variables: ['name', 'code'],
    });
    put.flush(template);
    expect(navigate).toHaveBeenCalledWith(['/templates']);
  });

  it('shows the backend message on a 400 and handles a missing template', () => {
    const { fixture } = setup('tpl-1');
    http.expectOne(`${url}/tpl-1`).flush(template);
    fixture.componentInstance.submit();
    http
      .expectOne(`${url}/tpl-1`)
      .flush(
        { status: 400, error: 'Bad Request', message: 'Missing variable: code' },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Missing variable: code');

    params.next(convertToParamMap({ id: 'missing' }));
    http.expectOne(`${url}/missing`).flush({}, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Template not found');
  });
});
