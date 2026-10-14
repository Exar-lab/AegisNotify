import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, distinctUntilChanged, map, of, startWith, Subject, switchMap } from 'rxjs';
import {
  chronologicalLogs,
  LogStatus,
  NotificationDetail,
  providerAttempts,
} from '../../../core/models/notification-detail.models';
import { NotificationsApiService } from '../../../core/services/notifications-api.service';

type DetailState =
  | { kind: 'loading' }
  | { kind: 'ready'; notification: NotificationDetail }
  | { kind: 'not-found' | 'error' };

@Component({
  selector: 'app-notification-detail-page',
  standalone: true,
  imports: [DatePipe, RouterLink],
  templateUrl: './notification-detail.page.html',
  styleUrl: './notification-detail.page.scss',
})
export class NotificationDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(NotificationsApiService);
  private readonly refresh = new Subject<void>();

  readonly state = toSignal(
    this.route.paramMap.pipe(
      map((params) => params.get('id') ?? ''),
      distinctUntilChanged(),
      switchMap((id) =>
        this.refresh.pipe(
          startWith(undefined),
          switchMap(() =>
            this.api.getStatus(id).pipe(
              map((notification): DetailState => ({ kind: 'ready', notification })),
              catchError((error: HttpErrorResponse) =>
                of<DetailState>({
                  kind: error.status === 404 ? 'not-found' : 'error',
                }),
              ),
              startWith<DetailState>({ kind: 'loading' }),
            ),
          ),
        ),
      ),
    ),
    { initialValue: { kind: 'loading' } as DetailState },
  );
  readonly notification = computed(() => {
    const state = this.state();
    return state.kind === 'ready' ? state.notification : null;
  });
  readonly timeline = computed(() => chronologicalLogs(this.notification()?.auditTrail ?? []));
  readonly attempts = computed(() => providerAttempts(this.timeline()));

  retry(): void {
    this.refresh.next();
  }

  label(status: string): string {
    return status.replaceAll('_', ' ');
  }

  tone(status: LogStatus): string {
    switch (status) {
      case 'SENT':
        return 'success';
      case 'SENT_VIA_FALLBACK':
        return 'fallback';
      case 'FAILED':
      case 'FAILED_CRITICAL':
      case 'PROVIDER_A_FAIL':
      case 'PROVIDER_B_FAIL':
        return 'danger';
      case 'QUEUED':
      case 'PROCESSING':
        return 'warning';
      default:
        return 'neutral';
    }
  }

  icon(status: LogStatus): string {
    switch (status) {
      case 'SENT':
        return '✓';
      case 'SENT_VIA_FALLBACK':
        return '↪';
      case 'FAILED':
      case 'FAILED_CRITICAL':
      case 'PROVIDER_A_FAIL':
      case 'PROVIDER_B_FAIL':
        return '!';
      case 'CANCELLED':
        return '×';
      case 'PROCESSING':
        return '↻';
      case 'QUEUED':
        return '→';
      default:
        return '◷';
    }
  }
}
