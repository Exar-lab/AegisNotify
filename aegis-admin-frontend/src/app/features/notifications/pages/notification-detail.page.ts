import { notificationTone, notificationIcon } from '../../../core/models/notification-presentation';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, distinctUntilChanged, map, of, startWith, Subject, switchMap } from 'rxjs';
import {
  chronologicalLogs,
  NotificationDetail,
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
  readonly attempts = computed(() => this.notification()?.providerAttempts ?? []);

  retry(): void {
    this.refresh.next();
  }

  label(status: string): string {
    return status.replaceAll('_', ' ');
  }

  readonly tone = notificationTone;
  readonly icon = notificationIcon;
}
