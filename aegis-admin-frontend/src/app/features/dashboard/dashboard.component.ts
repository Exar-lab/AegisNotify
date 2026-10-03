import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DashboardApiService } from '../../core/services/dashboard-api.service';
import {
  CircuitBreakerState,
  NotificationStatus,
  NotificationSummary,
} from '../../core/models/dashboard.models';

type LoadState<T> =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'ready'; data: T };

interface KpiTile {
  label: string;
  value: string;
}

interface ProviderRow {
  key: string;
  label: string;
  state: CircuitBreakerState;
}

const CHANNEL_BREAKER_LABELS: Record<string, string> = {
  'email-provider': 'Email',
  'sms-provider': 'SMS',
  'whatsapp-provider': 'WhatsApp',
  'push-provider': 'Push',
};

const RECENT_NOTIFICATIONS_LIMIT = 6;

/**
 * Dashboard screen (FRONTEND_REQUIREMENTS.md §5.1). Each panel loads
 * independently — one endpoint failing doesn't blank the other two.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  private readonly api = inject(DashboardApiService);

  readonly kpis = signal<LoadState<KpiTile[]>>({ status: 'loading' });
  readonly providers = signal<LoadState<ProviderRow[]>>({ status: 'loading' });
  readonly recentNotifications = signal<LoadState<NotificationSummary[]>>({ status: 'loading' });

  constructor() {
    this.api
      .getSummary()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (summary) =>
          this.kpis.set({
            status: 'ready',
            data: [
              { label: 'Total Requests (24h)', value: summary.totalRequests.toLocaleString() },
              { label: 'Success Rate', value: `${summary.successRatePercent.toFixed(2)}%` },
              {
                label: 'Fallback Activations',
                value: summary.fallbackActivations.toLocaleString(),
              },
              {
                label: 'Avg Delivery Latency',
                value: `${summary.avgDeliveryLatencyMillis.toLocaleString()} ms`,
              },
            ],
          }),
        error: () => this.kpis.set({ status: 'error' }),
      });

    this.api
      .getCircuitBreakers()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (response) =>
          this.providers.set({
            status: 'ready',
            data: Object.entries(CHANNEL_BREAKER_LABELS)
              .filter(([key]) => key in response.circuitBreakers)
              .map(([key, label]) => ({
                key,
                label,
                state: response.circuitBreakers[key].state,
              })),
          }),
        error: () => this.providers.set({ status: 'error' }),
      });

    this.api
      .getNotifications()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (notifications) =>
          this.recentNotifications.set({
            status: 'ready',
            data: notifications.slice(0, RECENT_NOTIFICATIONS_LIMIT),
          }),
        error: () => this.recentNotifications.set({ status: 'error' }),
      });
  }

  statusBadgeClass(status: NotificationStatus): string {
    switch (status) {
      case 'SENT':
        return 'badge-success';
      case 'SENT_VIA_FALLBACK':
        return 'badge-fallback';
      case 'PROCESSING':
        return 'badge-warning';
      case 'FAILED_CRITICAL':
      case 'FAILED':
        return 'badge-danger';
      default:
        return 'badge-neutral';
    }
  }

  breakerBadgeClass(state: CircuitBreakerState): string {
    switch (state) {
      case 'CLOSED':
        return 'badge-success';
      case 'HALF_OPEN':
        return 'badge-warning';
      case 'OPEN':
      case 'FORCED_OPEN':
        return 'badge-danger';
      default:
        return 'badge-neutral';
    }
  }

  formatTime(iso: string): string {
    return new Date(iso).toLocaleTimeString(undefined, {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  }
}
