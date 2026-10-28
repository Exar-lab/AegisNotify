import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { switchMap, timer } from 'rxjs';
import {
  CircuitBreakerState,
  DashboardSummary,
  NotificationStatus,
  NotificationSummary,
  ProviderCircuitBreakerStatus,
} from '../../core/models/dashboard.models';
import { DashboardApiService } from '../../core/services/dashboard-api.service';
import { notificationTone } from '../../core/models/notification-presentation';
import { CoreHotspot, DashboardCoreComponent } from './components/dashboard-core/dashboard-core.component';

export type LoadState<T> =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'ready'; data: T };

export interface KpiTile {
  label: string;
  value: string;
  deltaText: string;
  deltaTrend: 'up' | 'down' | 'neutral';
}

/**
 * Dashboard screen (FRONTEND_REQUIREMENTS.md §5.1).
 * Displays 4 KPI cards with trend deltas, a Provider Circuit Breakers panel with 10s polling,
 * and a Recent Notifications table with navigation to detail view.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, DashboardCoreComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  private readonly api = inject(DashboardApiService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly kpis = signal<LoadState<KpiTile[]>>({ status: 'loading' });
  readonly providers = signal<LoadState<ProviderCircuitBreakerStatus[]>>({ status: 'loading' });
  readonly recentNotifications = signal<LoadState<NotificationSummary[]>>({ status: 'loading' });

  private readonly summary = signal<DashboardSummary | null>(null);

  /**
   * The six signals the Orchestration Core reveals on hover, each read straight from live state:
   * the 24h summary, the circuit-breaker poll and the latest notifications. Placeholders until data arrives.
   */
  readonly coreHotspots = computed<CoreHotspot[]>(() => {
    const summary = this.summary();
    const providers = this.providers();
    const recent = this.recentNotifications();
    const none = '—';

    let circuits = none;
    let circuitTone: CoreHotspot['tone'] = 'neutral';
    if (providers.status === 'ready' && providers.data.length > 0) {
      const closed = providers.data.filter((p) => p.state === 'CLOSED').length;
      circuits = `${closed}/${providers.data.length}`;
      circuitTone = providers.data.some((p) => p.state === 'OPEN' || p.state === 'FORCED_OPEN')
        ? 'critical'
        : closed === providers.data.length
          ? 'ok'
          : 'neutral';
    }

    let failed = none;
    let failedTone: CoreHotspot['tone'] = 'neutral';
    if (recent.status === 'ready') {
      const count = recent.data.filter((n) => n.status === 'FAILED' || n.status === 'FAILED_CRITICAL').length;
      failed = count.toLocaleString();
      failedTone = count === 0 ? 'ok' : 'critical';
    }

    return [
      { id: 'latency', label: 'Delivery latency', value: summary ? summary.avgDeliveryLatency.currentValue.toLocaleString() : none, unit: summary ? 'ms' : undefined, tone: 'neutral' },
      { id: 'success', label: 'Success rate', value: summary ? `${summary.successRate.currentValue.toFixed(2)}%` : none, tone: 'neutral' },
      { id: 'requests', label: 'Requests, last 24h', value: summary ? summary.totalRequests.currentValue.toLocaleString() : none, tone: 'neutral' },
      { id: 'critical', label: 'Failed in latest 20', value: failed, tone: failedTone },
      { id: 'fallbacks', label: 'Fallback activations', value: summary ? summary.fallbackActivations.currentValue.toLocaleString() : none, tone: 'neutral' },
      { id: 'circuits', label: 'Circuit breakers', value: circuits, unit: circuits === none ? undefined : 'closed', tone: circuitTone },
    ];
  });

  constructor() {
    this.loadSummary();
    this.loadNotifications();

    timer(0, 10000)
      .pipe(
        switchMap(() => this.api.getCircuitBreakers()),
        takeUntilDestroyed()
      )
      .subscribe({
        next: (providers) => this.providers.set({ status: 'ready', data: providers }),
        error: () => this.providers.set({ status: 'error' }),
      });
  }

  loadSummary(): void {
    this.kpis.set({ status: 'loading' });
    this.api
      .getSummary()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (summary) => {
          this.summary.set(summary);
          this.kpis.set({
            status: 'ready',
            data: this.buildKpiTiles(summary),
          });
        },
        error: () => this.kpis.set({ status: 'error' }),
      });
  }

  loadNotifications(): void {
    this.recentNotifications.set({ status: 'loading' });
    this.api
      .getNotifications(20)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (notifications) =>
          this.recentNotifications.set({
            status: 'ready',
            data: notifications,
          }),
        error: () => this.recentNotifications.set({ status: 'error' }),
      });
  }

  loadCircuitBreakers(): void {
    this.providers.set({ status: 'loading' });
    this.api
      .getCircuitBreakers()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (providers) => this.providers.set({ status: 'ready', data: providers }),
        error: () => this.providers.set({ status: 'error' }),
      });
  }

  navigateToNotification(id: string): void {
    this.router.navigate(['/notifications', id]);
  }

  formatDelta(
    delta: number | null | undefined,
    fractionDigits: number = 1
  ): { deltaText: string; deltaTrend: 'up' | 'down' | 'neutral' } {
    if (delta === null || delta === undefined) {
      return { deltaText: 'No prior baseline', deltaTrend: 'neutral' };
    }
    const sign = delta >= 0 ? '+' : '';
    const deltaTrend = delta > 0 ? 'up' : delta < 0 ? 'down' : 'neutral';
    return {
      deltaText: `${sign}${delta.toFixed(fractionDigits)}% vs yesterday`,
      deltaTrend,
    };
  }

  private buildKpiTiles(summary: DashboardSummary): KpiTile[] {
    const requestsDelta = this.formatDelta(summary.totalRequests.deltaPercent, 1);
    const successDelta = this.formatDelta(summary.successRate.deltaPoints, 2);
    const fallbackDelta = this.formatDelta(summary.fallbackActivations.deltaPercent, 1);
    const latencyDelta = this.formatDelta(summary.avgDeliveryLatency.deltaPercent, 1);

    return [
      {
        label: 'Total Requests (24h)',
        value: summary.totalRequests.currentValue.toLocaleString(),
        ...requestsDelta,
      },
      {
        label: 'Success Rate',
        value: `${summary.successRate.currentValue.toFixed(2)}%`,
        ...successDelta,
      },
      {
        label: 'Fallback Activations',
        value: summary.fallbackActivations.currentValue.toLocaleString(),
        ...fallbackDelta,
      },
      {
        label: 'Avg Delivery Latency',
        value: `${summary.avgDeliveryLatency.currentValue.toLocaleString()} ms`,
        ...latencyDelta,
      },
    ];
  }

  statusBadgeClass(status: NotificationStatus): string {
    return `badge-${notificationTone(status)}`;
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
      case 'DISABLED':
      case 'METRICS_ONLY':
      case 'UNKNOWN':
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
