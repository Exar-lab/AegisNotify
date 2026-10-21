import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subject, catchError, map, of, startWith, switchMap } from 'rxjs';
import { ManagedUser, UserPage } from '../../../core/models/user.models';
import { UsersApiService } from '../../../core/services/users-api.service';
import { SettingsShieldComponent } from '../components/settings-shield/settings-shield.component';

type ListState = { kind: 'loading' | 'error' } | { kind: 'ready'; data: UserPage };

const PAGE_SIZE = 20;

/**
 * Settings view: administrator account management.
 */
@Component({
  selector: 'app-settings-page',
  standalone: true,
  imports: [DatePipe, DecimalPipe, ReactiveFormsModule, SettingsShieldComponent],
  templateUrl: './settings.page.html',
  styleUrl: './settings.page.scss',
})
export class SettingsPage {
  private readonly api = inject(UsersApiService);
  private readonly fb = inject(FormBuilder);
  private readonly requests = new Subject<number>();

  readonly page = signal(0);
  readonly state = toSignal(
    this.requests.pipe(
      startWith(0),
      switchMap((page) =>
        this.api.list(page, PAGE_SIZE).pipe(
          map((data): ListState => ({ kind: 'ready', data })),
          catchError(() => of<ListState>({ kind: 'error' })),
          startWith<ListState>({ kind: 'loading' }),
        ),
      ),
    ),
    { initialValue: { kind: 'loading' } as ListState },
  );
  readonly result = computed(() => {
    const state = this.state();
    return state.kind === 'ready' ? state.data : null;
  });
  readonly totalPages = computed(() => Math.max(1, this.result()?.totalPages ?? 1));

  readonly creating = signal(false);
  readonly submitting = signal(false);
  readonly formError = signal('');
  readonly actionError = signal('');
  readonly busyId = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.maxLength(255)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(320)]],
    firstName: ['', [Validators.required, Validators.maxLength(255)]],
    lastName: ['', [Validators.required, Validators.maxLength(255)]],
  });

  load(page = this.page()): void {
    this.page.set(page);
    this.requests.next(page);
  }

  go(page: number): void {
    if (page < 0 || page >= this.totalPages()) return;
    this.load(page);
  }

  fullName(user: ManagedUser): string {
    return `${user.firstName} ${user.lastName}`.trim();
  }

  toggleCreate(): void {
    this.creating.update((open) => !open);
    this.formError.set('');
    if (!this.creating()) this.form.reset();
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.formError.set('');
    this.api.create(this.form.getRawValue()).subscribe({
      next: () => {
        this.submitting.set(false);
        this.creating.set(false);
        this.form.reset();
        this.load(0);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.formError.set(
          err.status === 409
            ? 'A user with that username or email already exists.'
            : "Couldn't create the user. Please try again.",
        );
      },
    });
  }

  toggleStatus(user: ManagedUser): void {
    this.busyId.set(user.id);
    this.actionError.set('');
    this.api.setStatus(user.id, !user.enabled).subscribe({
      next: () => {
        this.busyId.set(null);
        this.load();
      },
      error: () => {
        this.busyId.set(null);
        this.actionError.set(`Couldn't update ${user.username}. Please try again.`);
      },
    });
  }

  resetPassword(user: ManagedUser): void {
    const newPassword = window.prompt(`New temporary password for ${user.username} (min 8 chars):`);
    if (!newPassword) return;
    if (newPassword.length < 8) {
      this.actionError.set('Password must be at least 8 characters.');
      return;
    }
    this.busyId.set(user.id);
    this.actionError.set('');
    this.api.resetPassword(user.id, { newPassword, temporary: true }).subscribe({
      next: () => this.busyId.set(null),
      error: () => {
        this.busyId.set(null);
        this.actionError.set(`Couldn't reset the password for ${user.username}.`);
      },
    });
  }
}
