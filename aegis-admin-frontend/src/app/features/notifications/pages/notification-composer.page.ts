import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  FormControl,
  FormRecord,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, map, of, startWith, switchMap, tap } from 'rxjs';
import {
  NOTIFICATION_PRIORITIES,
  NotificationPriority,
} from '../../../core/models/notification-create.models';
import {
  TEMPLATE_CHANNELS,
  TemplateChannel,
  TemplateResponse,
} from '../../../core/models/template.models';
import { NotificationsApiService } from '../../../core/services/notifications-api.service';
import { TemplatesApiService } from '../../../core/services/templates-api.service';

/** Wait this long after the last edit before asking the backend for a preview. */
export const PREVIEW_DEBOUNCE_MS = 400;

const EMAIL_PATTERN = /^[\w-.]+@([\w-]+\.)+[\w-]{2,4}$/;
const E164_PATTERN = /^\+[1-9]\d{1,14}$/;
const MAX_RECIPIENT_LENGTH = 320;

/** Mirrors the backend recipient rules (Notification.validateRecipient). Returns null when valid. */
export function recipientError(channel: TemplateChannel, value: string): string | null {
  const recipient = value.trim();
  if (!recipient) return 'Recipient is required.';
  if (recipient.length > MAX_RECIPIENT_LENGTH) {
    return `Recipient must be at most ${MAX_RECIPIENT_LENGTH} characters.`;
  }
  switch (channel) {
    case 'EMAIL':
      return EMAIL_PATTERN.test(recipient) ? null : 'Enter a valid email address.';
    case 'SMS':
    case 'WHATSAPP':
      return E164_PATTERN.test(recipient)
        ? null
        : 'Enter a phone number in E.164 format, e.g. +34600000000.';
    default:
      return null;
  }
}

type PreviewState =
  | { kind: 'idle' | 'loading' }
  | { kind: 'ready'; subject: string | null; body: string }
  | { kind: 'error'; message: string };

type TemplatesState = { kind: 'loading' | 'error' } | { kind: 'ready'; templates: TemplateResponse[] };

/**
 * Operator screen to compose and submit a notification (`POST /api/v1/notifications`).
 * Route is gated by the `notification:write` scope.
 */
@Component({
  selector: 'app-notification-composer-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './notification-composer.page.html',
  styleUrl: './notification-composer.page.scss',
})
export class NotificationComposerPage {
  private readonly router = inject(Router);
  private readonly notificationsApi = inject(NotificationsApiService);
  private readonly templatesApi = inject(TemplatesApiService);
  private readonly fb = inject(FormBuilder);

  readonly channels = TEMPLATE_CHANNELS;
  readonly priorities = NOTIFICATION_PRIORITIES;

  readonly form = this.fb.nonNullable.group({
    channel: ['EMAIL' as TemplateChannel, Validators.required],
    recipient: ['', [(control: AbstractControl) => this.validateRecipient(control)]],
    templateName: ['', Validators.required],
    priority: ['MEDIUM' as NotificationPriority, Validators.required],
    parameters: new FormRecord<FormControl<string>>({}),
  });

  readonly templatesState = toSignal(
    this.templatesApi.list().pipe(
      map((templates): TemplatesState => ({ kind: 'ready', templates })),
      catchError(() => of<TemplatesState>({ kind: 'error' })),
      startWith<TemplatesState>({ kind: 'loading' }),
    ),
    { initialValue: { kind: 'loading' } as TemplatesState },
  );

  private readonly channel = toSignal(
    this.form.controls.channel.valueChanges.pipe(startWith(this.form.controls.channel.value)),
    { initialValue: this.form.controls.channel.value },
  );
  private readonly templateName = toSignal(
    this.form.controls.templateName.valueChanges.pipe(
      startWith(this.form.controls.templateName.value),
    ),
    { initialValue: this.form.controls.templateName.value },
  );

  /** Active templates matching the selected channel. */
  readonly availableTemplates = computed(() => {
    const state = this.templatesState();
    if (state.kind !== 'ready') return [];
    const channel = this.channel();
    return state.templates.filter((t) => t.active && t.channel === channel);
  });
  readonly selectedTemplate = computed(
    () => this.availableTemplates().find((t) => t.name === this.templateName()) ?? null,
  );
  readonly parameterNames = computed(() => this.selectedTemplate()?.variables ?? []);

  readonly previewState = signal<PreviewState>({ kind: 'idle' });

  readonly previewReady = computed(() => {
    const state = this.previewState();
    return state.kind === 'ready' ? state : null;
  });
  readonly previewError = computed(() => {
    const state = this.previewState();
    return state.kind === 'error' ? state.message : '';
  });

  readonly submitting = signal(false);
  readonly formError = signal('');

  constructor() {
    this.form.controls.channel.valueChanges.subscribe(() => {
      this.form.controls.recipient.updateValueAndValidity();
      const current = this.form.controls.templateName.value;
      if (current && !this.availableTemplates().some((t) => t.name === current)) {
        this.form.controls.templateName.setValue('');
      }
    });
    this.form.controls.templateName.valueChanges.subscribe(() => this.syncParameters());
    this.watchPreview();
  }

  recipientMessage(): string | null {
    const control = this.form.controls.recipient;
    return control.touched && control.errors ? (control.errors['recipient'] as string) : null;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.submitting.set(true);
    this.formError.set('');
    this.notificationsApi
      .create({
        channel: value.channel,
        recipient: value.recipient.trim(),
        templateName: value.templateName,
        parameters: value.parameters,
        priority: value.priority,
      })
      .subscribe({
        next: (created) => {
          this.submitting.set(false);
          void this.router.navigate(['/notifications', created.id]);
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          this.formError.set(this.describeError(err));
        },
      });
  }

  /**
   * Requests a server-side preview whenever a template is selected and all of its parameters are
   * filled. Rendering stays on the backend so the preview matches what is actually sent.
   */
  private watchPreview(): void {
    this.form.valueChanges
      .pipe(
        map(() => this.previewRequest()),
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
        tap(() => this.previewState.set({ kind: 'idle' })),
        debounceTime(PREVIEW_DEBOUNCE_MS),
        switchMap((request) =>
          request
            ? this.templatesApi.render(request).pipe(
                map((rendered): PreviewState => ({ kind: 'ready', subject: rendered.subject, body: rendered.body })),
                catchError((err: HttpErrorResponse) =>
                  of<PreviewState>({ kind: 'error', message: this.describePreviewError(err) }),
                ),
                startWith<PreviewState>({ kind: 'loading' }),
              )
            : of<PreviewState>({ kind: 'idle' }),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((state) => this.previewState.set(state));
  }

  private previewRequest(): { templateName: string; parameters: Record<string, string> } | null {
    const { templateName, parameters } = this.form.getRawValue();
    if (!templateName || !this.availableTemplates().some((t) => t.name === templateName)) return null;
    const names = this.parameterNames();
    const filled = names.every((n) => (parameters[n] ?? '').trim() !== '');
    if (!filled) return null;
    return { templateName, parameters: { ...parameters } };
  }

  private describePreviewError(err: HttpErrorResponse): string {
    const backendMessage: unknown = err.error?.message;
    if ((err.status === 404 || err.status === 422) && typeof backendMessage === 'string' && backendMessage) {
      return backendMessage;
    }
    if (err.status === 403) return "You don't have permission to preview templates.";
    return "Couldn't load the preview. Please try again.";
  }

  private validateRecipient(control: AbstractControl): ValidationErrors | null {
    const channel = (control.parent?.get('channel')?.value as TemplateChannel | undefined) ?? 'EMAIL';
    const message = recipientError(channel, (control.value as string) ?? '');
    return message ? { recipient: message } : null;
  }

  /** Rebuilds the parameter controls, one required field per variable of the selected template. */
  private syncParameters(): void {
    const record = this.form.controls.parameters;
    const names = this.availableTemplates().find(
      (t) => t.name === this.form.controls.templateName.value,
    )?.variables ?? [];
    for (const existing of Object.keys(record.controls)) {
      if (!names.includes(existing)) record.removeControl(existing as never);
    }
    for (const name of names) {
      if (!record.contains(name)) {
        record.addControl(name, new FormControl('', { nonNullable: true, validators: Validators.required }));
      }
    }
  }

  private describeError(err: HttpErrorResponse): string {
    const backendMessage: unknown = err.error?.message;
    const message = typeof backendMessage === 'string' && backendMessage ? backendMessage : null;
    switch (err.status) {
      case 400:
      case 404:
      case 422:
        return message ?? 'The notification is invalid. Check the fields and try again.';
      case 403:
        return "You don't have permission to send notifications.";
      case 429:
        return 'Too many requests. Wait a moment and try again.';
      default:
        return "Couldn't send the notification. Please try again.";
    }
  }
}
