import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, catchError, distinctUntilChanged, map, of, startWith, switchMap, tap } from 'rxjs';
import {
  TEMPLATE_CHANNELS,
  TemplateChannel,
  TemplateResponse,
} from '../../../core/models/template.models';
import { TemplatesApiService } from '../../../core/services/templates-api.service';

type LoadState =
  | { kind: 'creating' }
  | { kind: 'loading' }
  | { kind: 'ready'; template: TemplateResponse }
  | { kind: 'not-found' | 'error' };

/** Parses a comma/newline separated list into unique, trimmed, non-empty entries. */
export function parseVariables(raw: string): string[] {
  return [
    ...new Set(
      raw
        .split(/[,\n]/)
        .map((entry) => entry.trim())
        .filter(Boolean),
    ),
  ];
}

/**
 * Create/edit view for a notification template.
 * `/templates/new` creates; `/templates/:id/edit` edits (name and channel are read-only).
 */
@Component({
  selector: 'app-template-form-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './template-form.page.html',
  styleUrl: './template-form.page.scss',
})
export class TemplateFormPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(TemplatesApiService);
  private readonly fb = inject(FormBuilder);

  readonly channels = TEMPLATE_CHANNELS;

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    channel: ['EMAIL' as TemplateChannel, Validators.required],
    subject: ['', Validators.maxLength(255)],
    body: ['', Validators.required],
    variables: [''],
  });

  readonly state = toSignal(
    this.route.paramMap.pipe(
      map((params) => params.get('id')),
      distinctUntilChanged(),
      switchMap((id): Observable<LoadState> => {
        if (!id) {
          this.form.controls.name.enable();
          this.form.controls.channel.enable();
          return of<LoadState>({ kind: 'creating' });
        }
        return this.api.get(id).pipe(
          tap((template) => this.fill(template)),
          map((template): LoadState => ({ kind: 'ready', template })),
          catchError((error: HttpErrorResponse) =>
            of<LoadState>({ kind: error.status === 404 ? 'not-found' : 'error' }),
          ),
          startWith<LoadState>({ kind: 'loading' }),
        );
      }),
    ),
    { initialValue: { kind: 'loading' } as LoadState },
  );
  readonly template = computed(() => {
    const state = this.state();
    return state.kind === 'ready' ? state.template : null;
  });
  readonly isEdit = computed(() => this.state().kind !== 'creating');

  readonly submitting = signal(false);
  readonly formError = signal('');

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const subject = value.subject.trim() || undefined;
    const variables = parseVariables(value.variables);
    const existing = this.template();
    const request$ = existing
      ? this.api.update(existing.id, { subject, body: value.body, variables })
      : this.api.create({
          name: value.name.trim(),
          channel: value.channel,
          subject,
          body: value.body,
          variables,
        });

    this.submitting.set(true);
    this.formError.set('');
    request$.subscribe({
      next: () => {
        this.submitting.set(false);
        void this.router.navigate(['/templates']);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.formError.set(this.describeError(err, value.name.trim()));
      },
    });
  }

  private fill(template: TemplateResponse): void {
    this.form.reset({
      name: template.name,
      channel: template.channel,
      subject: template.subject ?? '',
      body: template.body,
      variables: template.variables.join(', '),
    });
    this.form.controls.name.disable();
    this.form.controls.channel.disable();
  }

  private describeError(err: HttpErrorResponse, name: string): string {
    const backendMessage: unknown = err.error?.message;
    switch (err.status) {
      case 409:
        return `A template named "${name}" already exists.`;
      case 400:
        return typeof backendMessage === 'string' && backendMessage
          ? backendMessage
          : 'The template is invalid. Check the fields and try again.';
      case 404:
        return 'This template no longer exists.';
      default:
        return "Couldn't save the template. Please try again.";
    }
  }
}
