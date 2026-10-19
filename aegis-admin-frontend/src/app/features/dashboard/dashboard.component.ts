import {
  afterRenderEffect,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ArcElement, Chart, DoughnutController, Legend, Tooltip } from 'chart.js';
import { DashboardApiService } from '../../core/services/dashboard-api.service';
import {
  CircuitBreakerState,
  DashboardSummary,
  NotificationStatus,
  NotificationSummary,
  Priority,
  ProviderRow,
} from '../../core/models/dashboard.models';
import { GlassTiltDirective } from '../../shared/directives/glass-tilt.directive';

Chart.register(DoughnutController, ArcElement, Legend, Tooltip);

type LoadState<T> =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'ready'; data: T };

interface KpiTile {
  label: string;
  value: string;
}

interface DeliveryOutcomes {
  successCount: number;
  fallbackCount: number;
  failedCount: number;
  pendingCount: number;
}

const CHANNEL_BREAKER_LABELS: Record<string, string> = {
  'email-provider': 'Email',
  'sms-provider': 'SMS',
  'whatsapp-provider': 'WhatsApp',
  'push-provider': 'Push',
};

/**
 * Real vendor names from aegis-notification-service's
 * application.yml `notification.providers.*` block. Every channel's
 * secondary config uses the SAME vendor as primary (a second account/
 * credential), so there is only ever one vendor name per channel here —
 * never a different company for the fallback path.
 */
const CHANNEL_VENDORS: Record<string, string> = {
  'email-provider': 'SendGrid',
  'sms-provider': 'Twilio',
  'whatsapp-provider': 'Twilio',
  'push-provider': 'FCM',
};

const NOTIFICATIONS_PAGE_SIZE = 6;
const CRITICAL_EVENTS_PAGE_SIZE = 5;
const TOTAL_REQUESTS_LABEL = 'Total Requests (24h)';

interface Page<T> {
  items: T[];
  total: number;
  pageIndex: number;
  pageCount: number;
  rangeStart: number;
  rangeEnd: number;
}

function paginate<T>(data: T[], pageIndex: number, pageSize: number): Page<T> {
  const total = data.length;
  const pageCount = Math.max(Math.ceil(total / pageSize), 1);
  const clampedIndex = Math.min(Math.max(pageIndex, 0), pageCount - 1);
  const start = clampedIndex * pageSize;
  return {
    items: data.slice(start, start + pageSize),
    total,
    pageIndex: clampedIndex,
    pageCount,
    rangeStart: total === 0 ? 0 : start + 1,
    rangeEnd: Math.min(start + pageSize, total),
  };
}
const CRITICAL_STATUSES: ReadonlySet<NotificationStatus> = new Set([
  'FAILED',
  'FAILED_CRITICAL',
]);
/** Not yet resolved — must not be counted as "Failed" in Delivery Outcomes. */
const NON_TERMINAL_STATUSES: ReadonlySet<NotificationStatus> = new Set([
  'PENDING',
  'QUEUED',
  'PROCESSING',
]);

/**
 * Dashboard screen (FRONTEND_REQUIREMENTS.md §5.1). Each panel loads
 * independently — one endpoint failing doesn't blank the other two.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [GlassTiltDirective],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  private readonly api = inject(DashboardApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly kpis = signal<LoadState<KpiTile[]>>({ status: 'loading' });
  readonly providers = signal<LoadState<ProviderRow[]>>({ status: 'loading' });
  private readonly summary = signal<LoadState<DashboardSummary>>({ status: 'loading' });

  /**
   * Full notification list as fetched (DashboardApiService.getNotifications()
   * returns everything; only the table view below is sliced). Recent
   * Notifications, Priority Queues, and Recent Critical Events are all
   * derived from this one signal so they share the same loading/error/empty
   * lifecycle instead of each needing its own state.
   */
  private readonly notifications = signal<LoadState<NotificationSummary[]>>({ status: 'loading' });

  /**
   * Derived from the summary aggregate PLUS the real notification list, so a
   * notification still in PENDING/QUEUED/PROCESSING is counted as pending,
   * not silently lumped into "Failed" just because it hasn't succeeded yet.
   */
  readonly deliveryOutcomes = computed<LoadState<DeliveryOutcomes>>(() => {
    const summaryState = this.summary();
    if (summaryState.status === 'error') {
      return { status: 'error' };
    }
    if (summaryState.status !== 'ready' || summaryState.data.totalRequests === 0) {
      return { status: 'loading' };
    }

    const { totalRequests, successRatePercent, fallbackActivations } = summaryState.data;
    const notifState = this.notifications();
    const pendingCount =
      notifState.status === 'ready'
        ? notifState.data.filter((n) => NON_TERMINAL_STATUSES.has(n.status)).length
        : 0;

    const successCount = Math.round((totalRequests * successRatePercent) / 100);
    const fallbackCount = fallbackActivations;
    const failedCount = Math.max(
      totalRequests - successCount - fallbackCount - pendingCount,
      0,
    );

    return {
      status: 'ready',
      data: { successCount, fallbackCount, failedCount, pendingCount },
    };
  });

  readonly notificationsPageIndex = signal(0);
  readonly criticalPageIndex = signal(0);

  readonly notificationsPage = computed<LoadState<Page<NotificationSummary>>>(() => {
    const state = this.notifications();
    if (state.status !== 'ready') {
      return state;
    }
    return {
      status: 'ready',
      data: paginate(state.data, this.notificationsPageIndex(), NOTIFICATIONS_PAGE_SIZE),
    };
  });

  readonly priorityQueues = computed<LoadState<{ priority: Priority; count: number }[]>>(() => {
    const state = this.notifications();
    if (state.status !== 'ready') {
      return state;
    }
    const counts: Record<Priority, number> = { HIGH: 0, MEDIUM: 0, LOW: 0 };
    for (const n of state.data) {
      counts[n.priority]++;
    }
    return {
      status: 'ready',
      data: [
        { priority: 'HIGH' as Priority, count: counts.HIGH },
        { priority: 'MEDIUM' as Priority, count: counts.MEDIUM },
        { priority: 'LOW' as Priority, count: counts.LOW },
      ],
    };
  });

  readonly maxPriorityCount = computed<number>(() => {
    const state = this.priorityQueues();
    if (state.status !== 'ready') {
      return 0;
    }
    return Math.max(...state.data.map((row) => row.count), 1);
  });

  readonly criticalEventsPage = computed<LoadState<Page<NotificationSummary>>>(() => {
    const state = this.notifications();
    if (state.status !== 'ready') {
      return state;
    }
    const filtered = state.data
      .filter((n) => CRITICAL_STATUSES.has(n.status))
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
    return {
      status: 'ready',
      data: paginate(filtered, this.criticalPageIndex(), CRITICAL_EVENTS_PAGE_SIZE),
    };
  });

  readonly totalRequestsTile = computed<KpiTile | undefined>(() => {
    const state = this.kpis();
    if (state.status !== 'ready') {
      return undefined;
    }
    return state.data.find((tile) => tile.label === TOTAL_REQUESTS_LABEL);
  });

  readonly secondaryKpis = computed<LoadState<KpiTile[]>>(() => {
    const state = this.kpis();
    if (state.status !== 'ready') {
      return state;
    }
    return {
      status: 'ready',
      data: state.data.filter((tile) => tile.label !== TOTAL_REQUESTS_LABEL),
    };
  });

  private readonly outcomesCanvas =
    viewChild<ElementRef<HTMLCanvasElement>>('outcomesCanvas');

  private outcomesChart: Chart | undefined;

  constructor() {
    this.api
      .getSummary()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (summary) => {
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
          });
          this.summary.set({ status: 'ready', data: summary });
        },
        error: () => {
          this.kpis.set({ status: 'error' });
          this.summary.set({ status: 'error' });
        },
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
              .map(([key, label]) => {
                const details = response.circuitBreakers[key];
                return {
                  key,
                  label,
                  state: details.state,
                  failureRate: Math.max(parseFloat(details.failureRate), 0),
                  slowCallRate: Math.max(parseFloat(details.slowCallRate), 0),
                  bufferedCalls: details.bufferedCalls,
                };
              }),
          }),
        error: () => this.providers.set({ status: 'error' }),
      });

    this.api
      .getNotifications()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (notifications) => this.notifications.set({ status: 'ready', data: notifications }),
        error: () => this.notifications.set({ status: 'error' }),
      });

    afterRenderEffect(() => {
      const canvasRef = this.outcomesCanvas();
      const state = this.deliveryOutcomes();
      if (!canvasRef || state.status !== 'ready') {
        return;
      }
      this.buildOutcomesChart(canvasRef.nativeElement, state.data);
    });

    this.destroyRef.onDestroy(() => {
      this.outcomesChart?.destroy();
    });
  }

  private buildOutcomesChart(canvas: HTMLCanvasElement, outcomes: DeliveryOutcomes): void {
    this.outcomesChart?.destroy();

    const styles = getComputedStyle(document.documentElement);
    const successColor = styles.getPropertyValue('--status-online').trim();
    const fallbackColor = styles.getPropertyValue('--status-fallback').trim();
    const failedColor = styles.getPropertyValue('--status-offline').trim();
    const pendingColor = styles.getPropertyValue('--status-neutral').trim();
    const textColor = styles.getPropertyValue('--text-secondary').trim();
    const surfaceColor = styles.getPropertyValue('--bg-surface').trim();
    const titleTextColor = styles.getPropertyValue('--text-primary').trim();
    const borderColor = styles.getPropertyValue('--border-color').trim();
    const fontFamily = styles.getPropertyValue('--font-family-base').trim();

    this.outcomesChart = new Chart(canvas, {
      type: 'doughnut',
      data: {
        labels: ['Success', 'Fallback', 'Failed', 'Pending'],
        datasets: [
          {
            data: [
              outcomes.successCount,
              outcomes.fallbackCount,
              outcomes.failedCount,
              outcomes.pendingCount,
            ],
            backgroundColor: [successColor, fallbackColor, failedColor, pendingColor],
            borderWidth: 3,
            borderColor: surfaceColor,
            hoverOffset: 6,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '72%',
        animation: { duration: 500, easing: 'easeOutQuart' },
        plugins: {
          legend: {
            display: true,
            position: 'bottom',
            labels: {
              color: textColor,
              usePointStyle: true,
              pointStyle: 'circle',
              boxWidth: 8,
              padding: 16,
              font: { family: fontFamily },
            },
          },
          tooltip: {
            enabled: true,
            backgroundColor: surfaceColor,
            titleColor: titleTextColor,
            bodyColor: titleTextColor,
            borderColor: borderColor,
            borderWidth: 1,
            padding: 10,
            cornerRadius: 8,
            displayColors: true,
            titleFont: { family: fontFamily },
            bodyFont: { family: fontFamily },
          },
        },
      },
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

  /** Same state grouping as {@link breakerBadgeClass}, used to tint the
   * Channel Resilience card's border/background instead of a badge. */
  breakerCardClass(state: CircuitBreakerState): string {
    switch (state) {
      case 'CLOSED':
        return 'state-success';
      case 'HALF_OPEN':
        return 'state-warning';
      case 'OPEN':
      case 'FORCED_OPEN':
        return 'state-danger';
      default:
        return 'state-neutral';
    }
  }

  vendorFor(key: string): string {
    return CHANNEL_VENDORS[key] ?? 'Unknown provider';
  }

  successRate(row: ProviderRow): number {
    return Math.max(100 - row.failureRate, 0);
  }

  formatTime(iso: string): string {
    return new Date(iso).toLocaleTimeString(undefined, {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  }

  /**
   * Masks PII for the Recent Critical Events feed. Emails keep the first
   * local-part character and the full domain; E.164 phone numbers keep a
   * short country-code-ish prefix and the last two digits.
   */
  maskRecipient(value: string): string {
    const trimmed = value?.trim() ?? '';
    if (!trimmed) {
      return trimmed;
    }

    const atIndex = trimmed.indexOf('@');
    if (atIndex > 0) {
      const local = trimmed.slice(0, atIndex);
      const domain = trimmed.slice(atIndex);
      const maskLength = Math.max(local.length - 1, 3);
      return `${local[0]}${'*'.repeat(maskLength)}${domain}`;
    }

    if (trimmed.startsWith('+')) {
      const digits = trimmed.slice(1);
      const tailLength = Math.min(2, digits.length);
      const tail = digits.slice(digits.length - tailLength);
      const headLength = Math.min(2, Math.max(digits.length - tailLength - 1, 1));
      const head = digits.slice(0, headLength);
      const maskLength = Math.max(digits.length - headLength - tailLength, 3);
      return `+${head} ${'*'.repeat(maskLength)}${tail}`;
    }

    return trimmed;
  }

  /** Plain Date-math relative timestamp, e.g. "2m ago", "1h ago". */
  relativeTime(iso: string): string {
    const diffMs = Math.max(Date.now() - new Date(iso).getTime(), 0);
    const diffMinutes = Math.floor(diffMs / 60_000);

    if (diffMinutes < 1) {
      return 'just now';
    }
    if (diffMinutes < 60) {
      return `${diffMinutes}m ago`;
    }
    const diffHours = Math.floor(diffMinutes / 60);
    if (diffHours < 24) {
      return `${diffHours}h ago`;
    }
    const diffDays = Math.floor(diffHours / 24);
    return `${diffDays}d ago`;
  }
}
