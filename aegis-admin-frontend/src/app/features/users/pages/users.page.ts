import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  BehaviorSubject,
  catchError,
  debounceTime,
  distinctUntilChanged,
  map,
  of,
  startWith,
  switchMap,
} from 'rxjs';
import { ManagedUser, UserPage } from '../../../core/models/user.models';
import { UsersApiService } from '../../../core/services/users-api.service';
import { UsersShieldComponent } from '../components/users-shield/users-shield.component';

type ListState = { kind: 'loading' | 'error' } | { kind: 'ready'; data: UserPage };

const PAGE_SIZE = 20;

/**
 * Users management page: lists system users with server-side pagination and search,
 * and provides reactive modals for creating, editing, role assignment, password reset,
 * and status toggling (enable/disable).
 */
@Component({
  selector: 'app-users-page',
  standalone: true,
  imports: [
    DatePipe,
    DecimalPipe,
    ReactiveFormsModule,
    FormsModule,
    RouterLink,
    UsersShieldComponent,
  ],
  templateUrl: './users.page.html',
  styleUrl: './users.page.scss',
})
export class UsersPage {
  private readonly api = inject(UsersApiService);
  private readonly fb = inject(FormBuilder);

  readonly page = signal(0);
  readonly search = signal('');
  readonly searchControl = this.fb.control('');

  readonly availableRoles = signal<string[]>([]);
  readonly selectedRoles = signal<string[]>([]);
  readonly selectedUser = signal<ManagedUser | null>(null);

  readonly isCreateOpen = signal(false);
  readonly isEditOpen = signal(false);
  readonly isRolesOpen = signal(false);
  readonly isPasswordOpen = signal(false);

  readonly submitting = signal(false);
  readonly formError = signal('');
  readonly actionError = signal('');
  readonly busyId = signal<string | null>(null);

  private readonly requests = new BehaviorSubject<{ page: number; search: string }>({
    page: 0,
    search: '',
  });

  readonly state = toSignal(
    this.requests.pipe(
      switchMap(({ page, search }) =>
        this.api.list(page, PAGE_SIZE, search).pipe(
          map((data): ListState => ({ kind: 'ready', data })),
          catchError(() => of<ListState>({ kind: 'error' })),
          startWith<ListState>({ kind: 'loading' }),
        ),
      ),
    ),
    { initialValue: { kind: 'loading' } as ListState },
  );

  readonly result = computed(() => {
    const s = this.state();
    return s.kind === 'ready' ? s.data : null;
  });

  readonly users = computed(() => this.result()?.content ?? []);
  readonly totalPages = computed(() => Math.max(1, this.result()?.totalPages ?? 1));

  readonly createForm = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.maxLength(255)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(320)]],
    firstName: ['', [Validators.required, Validators.maxLength(255)]],
    lastName: ['', [Validators.required, Validators.maxLength(255)]],
  });

  readonly editForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(320)]],
    firstName: ['', [Validators.required, Validators.maxLength(255)]],
    lastName: ['', [Validators.required, Validators.maxLength(255)]],
  });

  readonly passwordForm = this.fb.nonNullable.group({
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
    temporary: [true],
  });

  constructor() {
    this.searchControl.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((query) => {
        const trimmed = (query ?? '').trim();
        this.search.set(trimmed);
        this.page.set(0);
        this.load(0);
      });

    this.api.getRoles().subscribe({
      next: (roles) => this.availableRoles.set(roles),
      error: () => {},
    });
  }

  load(page = this.page()): void {
    this.page.set(page);
    this.requests.next({ page, search: this.search() });
  }

  go(page: number): void {
    if (page < 0 || page >= this.totalPages()) return;
    this.load(page);
  }

  clearSearch(): void {
    this.searchControl.setValue('');
  }

  fullName(user: ManagedUser): string {
    return `${user.firstName} ${user.lastName}`.trim();
  }

  openCreate(): void {
    this.createForm.reset();
    this.formError.set('');
    this.isCreateOpen.set(true);
  }

  closeCreate(): void {
    this.isCreateOpen.set(false);
  }

  submitCreate(): void {
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.formError.set('');
    this.api.create(this.createForm.getRawValue()).subscribe({
      next: () => {
        this.submitting.set(false);
        this.isCreateOpen.set(false);
        this.createForm.reset();
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

  openEdit(user: ManagedUser): void {
    this.selectedUser.set(user);
    this.editForm.setValue({
      email: user.email,
      firstName: user.firstName,
      lastName: user.lastName,
    });
    this.formError.set('');
    this.isEditOpen.set(true);
  }

  closeEdit(): void {
    this.isEditOpen.set(false);
  }

  submitEdit(): void {
    if (this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }
    const user = this.selectedUser();
    if (!user) return;

    this.submitting.set(true);
    this.formError.set('');
    this.api.update(user.id, this.editForm.getRawValue()).subscribe({
      next: () => {
        this.submitting.set(false);
        this.isEditOpen.set(false);
        this.load();
      },
      error: () => {
        this.submitting.set(false);
        this.formError.set("Couldn't update user details. Please try again.");
      },
    });
  }

  openRoles(user: ManagedUser): void {
    this.selectedUser.set(user);
    this.selectedRoles.set([...user.roles]);
    this.formError.set('');
    this.isRolesOpen.set(true);
    // Refresh available roles whenever the modal is opened
    this.api.getRoles().subscribe({
      next: (roles) => this.availableRoles.set(roles),
      error: () => {},
    });
  }

  closeRoles(): void {
    this.isRolesOpen.set(false);
  }

  toggleRole(role: string): void {
    this.selectedRoles.update((roles) =>
      roles.includes(role) ? roles.filter((r) => r !== role) : [...roles, role],
    );
  }

  isRoleSelected(role: string): boolean {
    return this.selectedRoles().includes(role);
  }

  submitRoles(): void {
    const user = this.selectedUser();
    if (!user) return;

    this.submitting.set(true);
    this.formError.set('');
    this.api.setRoles(user.id, this.selectedRoles()).subscribe({
      next: () => {
        this.submitting.set(false);
        this.isRolesOpen.set(false);
        this.load();
      },
      error: () => {
        this.submitting.set(false);
        this.formError.set("Couldn't update user roles. Please try again.");
      },
    });
  }

  openPassword(user: ManagedUser): void {
    this.selectedUser.set(user);
    this.passwordForm.reset({ newPassword: '', temporary: true });
    this.formError.set('');
    this.isPasswordOpen.set(true);
  }

  closePassword(): void {
    this.isPasswordOpen.set(false);
  }

  submitPassword(): void {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    const user = this.selectedUser();
    if (!user) return;

    this.submitting.set(true);
    this.formError.set('');
    this.api.resetPassword(user.id, this.passwordForm.getRawValue()).subscribe({
      next: () => {
        this.submitting.set(false);
        this.isPasswordOpen.set(false);
      },
      error: () => {
        this.submitting.set(false);
        this.formError.set("Couldn't reset the password. Please try again.");
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
        this.actionError.set(`Couldn't update status for ${user.username}. Please try again.`);
      },
    });
  }
}
