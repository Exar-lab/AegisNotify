import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MetricsChannel, MetricsSummary } from '../../../core/models/metrics.models';
import { MetricsApiService } from '../../../core/services/metrics-api.service';

type LoadState<T> = { status: 'loading' } | { status: 'error' } | { status: 'ready'; data: T };

const CHANNELS: { channel: MetricsChannel; label: string }[] = [
  { channel: 'EMAIL', label: 'Email' },
  { channel: 'SMS', label: 'SMS' },
  { channel: 'WHATSAPP', label: 'WhatsApp' },
  { channel: 'PUSH', label: 'Push' },
];

/** Formats seconds as "380ms" below one second, otherwise "2.1s". */
export function formatSeconds(seconds: number): string {
  if (seconds < 1) {
    return `${Math.round(seconds * 1000)}ms`;
  }
  return `${Number(seconds.toFixed(2))}s`;
}

/**
 * Metrics screen: read-only view over GET /api/v1/metrics/summary.
 */
@Component({
  selector: 'app-metrics-page',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './metrics.page.html',
  styleUrl: './metrics.page.scss',
})
export class MetricsPage {
  private readonly api = inject(MetricsApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly state = signal<LoadState<MetricsSummary>>({ status: 'loading' });
  readonly formatSeconds = formatSeconds;

  readonly channelCards = computed(() => {
    const s = this.state();
    const counts = s.status === 'ready' ? s.data.requestsByChannel : [];
    return CHANNELS.map((c) => ({
      ...c,
      count: counts.find((r) => r.channel === c.channel)?.count ?? 0,
    }));
  });

  /** 'danger' when anything reached the DLQ, 'warning' when failures are >= 10% of handled messages. */
  readonly kafkaTone = computed<'ok' | 'warning' | 'danger'>(() => {
    const s = this.state();
    if (s.status !== 'ready') return 'ok';
    const { success, failure, dlq } = s.data.kafkaConsumer;
    if (dlq > 0) return 'danger';
    const total = success + failure;
    return total > 0 && failure / total >= 0.1 ? 'warning' : 'ok';
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.state.set({ status: 'loading' });
    this.api
      .getSummary()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => this.state.set({ status: 'ready', data }),
        error: () => this.state.set({ status: 'error' }),
      });
  }
}
