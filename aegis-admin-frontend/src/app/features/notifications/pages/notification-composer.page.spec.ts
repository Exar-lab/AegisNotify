import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { TemplateChannel, TemplateResponse } from '../../../core/models/template.models';
import {
  NotificationComposerPage,
  PREVIEW_DEBOUNCE_MS,
  recipientError,
} from './notification-composer.page';

function tpl(
  name: string,
  channel: TemplateChannel,
  variables: string[],
  active = true,
): TemplateResponse {
  return {
    id: `id-${name}`,
    name,
    channel,
    subject: null,
    body: 'x',
    variables,
    active,
    createdAt: '2026-10-01T10:00:00Z',
    updatedAt: '2026-10-01T10:00:00Z',
  };
}

const templates: TemplateResponse[] = [
  tpl('welcome', 'EMAIL', ['name', 'code']),
  tpl('old-welcome', 'EMAIL', ['name'], false),
  tpl('otp', 'SMS', ['code']),
  tpl('plain', 'EMAIL', []),
];

describe('recipientError', () => {
  it('validates EMAIL with the backend regex', () => {
    expect(recipientError('EMAIL', 'ada@example.com')).toBeNull();
    expect(recipientError('EMAIL', 'not-an-email')).toContain('email');
    expect(recipientError('EMAIL', 'a@b.toolong')).toContain('email');
  });

  it('validates SMS and WHATSAPP as E.164', () => {
    for (const channel of ['SMS', 'WHATSAPP'] as const) {
      expect(recipientError(channel, '+34600000000')).toBeNull();
      expect(recipientError(channel, '600000000')).toContain('E.164');
      expect(recipientError(channel, '+0123456')).toContain('E.164');
    }
  });

  it('only requires a non-blank value for PUSH and enforces the 320 char cap', () => {
    expect(recipientError('PUSH', 'device-token')).toBeNull();
    expect(recipientError('PUSH', '   ')).not.toBeNull();
    expect(recipientError('PUSH', 'a'.repeat(321))).toContain('320');
  });

  it('requires a value', () => {
    expect(recipientError('EMAIL', '')).toBe('Recipient is required.');
  });
});

describe('NotificationComposerPage', () => {
  let http: HttpTestingController;
  const base = environment.apiBaseUrl.replace(/\/+$/, '');
  const templatesUrl = `${base}/api/v1/templates`;
  const notificationsUrl = `${base}/api/v1/notifications`;

  function setup() {
    TestBed.configureTestingModule({
      imports: [NotificationComposerPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(NotificationComposerPage);
    fixture.detectChanges();
    http.expectOne(templatesUrl).flush(templates);
    fixture.detectChanges();
    return { fixture, page: fixture.componentInstance, navigate };
  }

  afterEach(() => http?.verify());

  it('offers only active templates of the selected channel', () => {
    const { page } = setup();
    expect(page.availableTemplates().map((t) => t.name)).toEqual(['welcome', 'plain']);
    page.form.controls.channel.setValue('SMS');
    expect(page.availableTemplates().map((t) => t.name)).toEqual(['otp']);
  });

  it('clears the template when the channel changes to one that does not offer it', () => {
    const { page } = setup();
    page.form.controls.templateName.setValue('welcome');
    page.form.controls.channel.setValue('SMS');
    expect(page.form.controls.templateName.value).toBe('');
  });

  it('re-validates the recipient when the channel changes', () => {
    const { page } = setup();
    page.form.controls.recipient.setValue('+34600000000');
    expect(page.form.controls.recipient.invalid).toBe(true); // EMAIL by default
    page.form.controls.channel.setValue('SMS');
    expect(page.form.controls.recipient.valid).toBe(true);
  });

  it('builds one required parameter field per template variable', () => {
    const { fixture, page } = setup();
    page.form.controls.templateName.setValue('welcome');
    fixture.detectChanges();
    expect(Object.keys(page.form.controls.parameters.controls)).toEqual(['name', 'code']);
    expect(fixture.nativeElement.textContent).toContain('name');
    page.form.controls.templateName.setValue('plain');
    expect(Object.keys(page.form.controls.parameters.controls)).toEqual([]);
  });

  it('reports missing template variables before submit and sends nothing', () => {
    const { fixture, page } = setup();
    page.form.patchValue({ recipient: 'ada@example.com', templateName: 'welcome' });
    page.form.controls.parameters.controls['name'].setValue('Ada');
    page.submit();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('code is required');
    http.expectNone(notificationsUrl);
  });

  it('does not submit an invalid recipient', () => {
    const { fixture, page } = setup();
    page.form.patchValue({ recipient: 'nope', templateName: 'plain' });
    page.submit();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('valid email');
    http.expectNone(notificationsUrl);
  });

  it('submits the request and navigates to the new notification on 202', () => {
    const { page, navigate } = setup();
    page.form.patchValue({
      recipient: '  ada@example.com ',
      templateName: 'welcome',
      priority: 'HIGH',
    });
    page.form.controls.parameters.controls['name'].setValue('Ada');
    page.form.controls.parameters.controls['code'].setValue('1234');
    page.submit();

    const req = http.expectOne(notificationsUrl);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      channel: 'EMAIL',
      recipient: 'ada@example.com',
      templateName: 'welcome',
      parameters: { name: 'Ada', code: '1234' },
      priority: 'HIGH',
    });
    req.flush({ id: 'n-42', status: 'PENDING' }, { status: 202, statusText: 'Accepted' });
    expect(navigate).toHaveBeenCalledWith(['/notifications', 'n-42']);
  });

  it('shows the backend validation message in the form and stays on the page', () => {
    const { fixture, page, navigate } = setup();
    page.form.patchValue({ recipient: 'ada@example.com', templateName: 'plain' });
    page.submit();
    http
      .expectOne(notificationsUrl)
      .flush(
        { status: 400, error: 'Bad Request', message: 'recipient: size must be between 0 and 320' },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('size must be between 0 and 320');
    expect(page.submitting()).toBe(false);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('explains a 404 (template missing or inactive) and a generic failure', () => {
    const { fixture, page } = setup();
    page.form.patchValue({ recipient: 'ada@example.com', templateName: 'plain' });
    page.submit();
    http
      .expectOne(notificationsUrl)
      .flush({ message: 'Template not found: plain' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Template not found: plain');

    page.submit();
    http.expectOne(notificationsUrl).flush('boom', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain("Couldn't send the notification");
  });

  it('shows an error state when templates cannot be loaded', () => {
    TestBed.configureTestingModule({
      imports: [NotificationComposerPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(NotificationComposerPage);
    fixture.detectChanges();
    http.expectOne(templatesUrl).flush('x', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain("Couldn't load templates");
  });

  describe('preview', () => {
    const renderUrl = `${templatesUrl}/render`;

    beforeEach(() => vi.useFakeTimers());
    afterEach(() => vi.useRealTimers());

    function fill(page: NotificationComposerPage) {
      page.form.controls.templateName.setValue('welcome');
      page.form.controls.parameters.controls['name'].setValue('Ada');
      page.form.controls.parameters.controls['code'].setValue('1234');
    }

    it('renders after the debounce once the template and all parameters are filled', () => {
      const { fixture, page } = setup();
      fill(page);
      http.expectNone(renderUrl);
      vi.advanceTimersByTime(PREVIEW_DEBOUNCE_MS);
      const req = http.expectOne(renderUrl);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({
        templateName: 'welcome',
        parameters: { name: 'Ada', code: '1234' },
      });
      req.flush({ name: 'welcome', subject: 'Hi Ada', body: 'Your code is 1234' });
      fixture.detectChanges();
      const text = fixture.nativeElement.textContent as string;
      expect(text).toContain('Preview');
      expect(text).toContain('Hi Ada');
      expect(text).toContain('Your code is 1234');
    });

    it('does not call the endpoint while a parameter is missing', () => {
      const { fixture, page } = setup();
      page.form.controls.templateName.setValue('welcome');
      page.form.controls.parameters.controls['name'].setValue('Ada');
      vi.advanceTimersByTime(PREVIEW_DEBOUNCE_MS * 2);
      http.expectNone(renderUrl);
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Fill in all parameters');
    });

    it('debounces rapid edits into a single request', () => {
      const { page } = setup();
      fill(page);
      vi.advanceTimersByTime(PREVIEW_DEBOUNCE_MS - 100);
      page.form.controls.parameters.controls['code'].setValue('9999');
      vi.advanceTimersByTime(PREVIEW_DEBOUNCE_MS - 100);
      http.expectNone(renderUrl);
      vi.advanceTimersByTime(100);
      const req = http.expectOne(renderUrl);
      expect(req.request.body.parameters.code).toBe('9999');
      req.flush({ name: 'welcome', subject: null, body: 'x' });
    });

    it('shows the backend message inline on 422 and 404', () => {
      const { fixture, page } = setup();
      fill(page);
      vi.advanceTimersByTime(PREVIEW_DEBOUNCE_MS);
      http
        .expectOne(renderUrl)
        .flush(
          { status: 422, message: 'Missing required variables: [code]' },
          { status: 422, statusText: 'Unprocessable Entity' },
        );
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Missing required variables: [code]');

      page.form.controls.parameters.controls['code'].setValue('5678');
      vi.advanceTimersByTime(PREVIEW_DEBOUNCE_MS);
      http
        .expectOne(renderUrl)
        .flush({ message: 'Template not found: welcome' }, { status: 404, statusText: 'Not Found' });
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Template not found: welcome');
    });

    it('shows a generic inline error on other failures', () => {
      const { fixture, page } = setup();
      fill(page);
      vi.advanceTimersByTime(PREVIEW_DEBOUNCE_MS);
      http.expectOne(renderUrl).flush('boom', { status: 500, statusText: 'Server Error' });
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain("Couldn't load the preview");
    });

    it('clears the preview when a parameter is emptied', () => {
      const { fixture, page } = setup();
      fill(page);
      vi.advanceTimersByTime(PREVIEW_DEBOUNCE_MS);
      http.expectOne(renderUrl).flush({ name: 'welcome', subject: 'Hi Ada', body: 'Body' });
      fixture.detectChanges();
      page.form.controls.parameters.controls['code'].setValue('');
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).not.toContain('Hi Ada');
    });

    it('previews a template without variables right away', () => {
      const { page } = setup();
      page.form.controls.templateName.setValue('plain');
      vi.advanceTimersByTime(PREVIEW_DEBOUNCE_MS);
      const req = http.expectOne(renderUrl);
      expect(req.request.body).toEqual({ templateName: 'plain', parameters: {} });
      req.flush({ name: 'plain', subject: null, body: 'x' });
    });
  });
});
