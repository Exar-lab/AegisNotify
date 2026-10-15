import { notificationTone, notificationIcon } from '../../../core/models/notification-presentation';
import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, catchError, map, of, startWith, switchMap } from 'rxjs';
import { NotificationPage } from '../../../core/models/notification-list.models';
import { NotificationsApiService } from '../../../core/services/notifications-api.service';

type ListState = { kind: 'loading' | 'error' } | { kind: 'ready'; data: NotificationPage };
const defaults = {
  search: '',
  channel: '',
  priority: '',
  status: '',
  from: '',
  to: '',
  page: 0,
  size: 20,
};
@Component({
  selector: 'app-notifications-page',
  standalone: true,
  imports: [DatePipe, DecimalPipe, FormsModule, RouterLink],
  templateUrl: './notifications.page.html',
  styleUrl: './notifications.page.scss',
})
export class NotificationsPage {
  private readonly api = inject(NotificationsApiService);
  private readonly requests = new Subject<typeof defaults>();
  readonly filters = { search: '', channel: '', priority: '', status: '' };
  readonly query = signal({ ...defaults });
  readonly channels = ['EMAIL', 'SMS', 'WHATSAPP', 'PUSH'];
  readonly priorities = ['HIGH', 'MEDIUM', 'LOW'];
  readonly statuses = [
    'PENDING',
    'QUEUED',
    'PROCESSING',
    'SENT',
    'SENT_VIA_FALLBACK',
    'FAILED',
    'FAILED_CRITICAL',
    'CANCELLED',
  ];
  readonly state = toSignal(
    this.requests.pipe(
      startWith(defaults),
      switchMap((query) =>
        this.api.list(query).pipe(
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
  readonly totalPages = computed(() =>
    Math.max(1, Math.ceil((this.result()?.totalElements ?? 0) / this.query().size)),
  );
  readonly dateError = signal(false);
  fromDate = '';
  toDate = '';
  size = 20;
  apply(): void {
    this.dateError.set(!!this.fromDate && !!this.toDate && this.fromDate > this.toDate);
    if (this.dateError()) return;
    const from = this.fromDate ? new Date(`${this.fromDate}T00:00:00`).toISOString() : '';
    let to = '';
    if (this.toDate) {
      const end = new Date(`${this.toDate}T00:00:00`);
      end.setDate(end.getDate() + 1);
      to = end.toISOString();
    }
    this.query.set({
      ...this.filters,
      search: this.filters.search.trim(),
      from,
      to,
      page: 0,
      size: this.size,
    });
    this.load();
  }
  clear(): void {
    Object.assign(this.filters, { search: '', channel: '', priority: '', status: '' });
    this.fromDate = this.toDate = '';
    this.size = 20;
    this.dateError.set(false);
    this.query.set({ ...defaults });
    this.load();
  }
  go(page: number): void {
    if (page < 0 || page >= this.totalPages()) return;
    this.query.update((query) => ({ ...query, page }));
    this.load();
  }
  load(): void {
    this.requests.next({ ...this.query() });
  }
  label(value: string): string {
    return value.replaceAll('_', ' ');
  }
  readonly tone = notificationTone;
  readonly icon = notificationIcon;
}
