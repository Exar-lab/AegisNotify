import { DecimalPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProviderCircuitBreakerStatus } from '../../core/models/dashboard.models';

@Component({
  selector: 'app-provider-card',
  standalone: true,
  imports: [DecimalPipe, RouterLink],
  templateUrl: './provider-card.component.html',
  styleUrl: './provider-card.component.scss',
})
export class ProviderCardComponent {
  readonly provider = input.required<ProviderCircuitBreakerStatus>();
  readonly now = input.required<number>();
  readonly circuitTone = computed(() => {
    switch (this.provider().state) {
      case 'CLOSED':
        return 'healthy';
      case 'HALF_OPEN':
        return 'degraded';
      case 'OPEN':
      case 'FORCED_OPEN':
        return 'open';
      default:
        return 'neutral';
    }
  });
  readonly successRate = computed(() => {
    const value = this.provider().successRate;
    return value == null || !Number.isFinite(value) ? null : Math.max(0, Math.min(100, value));
  });
  readonly successTone = computed(() => {
    const value = this.successRate();
    return value === null ? 'neutral' : value >= 95 ? 'healthy' : value >= 70 ? 'degraded' : 'open';
  });
  readonly message = computed(() => {
    switch (this.provider().state) {
      case 'OPEN':
        return 'Circuit open, primary calls are routed to fallback.';
      case 'FORCED_OPEN':
        return 'Circuit forced open. Calls are blocked.';
      case 'HALF_OPEN':
        return 'Recovery in progress. Testing provider availability.';
      case 'NOT_PROTECTED':
        return 'Direct fallback, no circuit breaker configured.';
      case 'UNKNOWN':
        return 'Circuit state unavailable.';
      case 'DISABLED':
        return 'Circuit breaker disabled.';
      case 'METRICS_ONLY':
        return 'Monitoring only. Calls are not blocked.';
      default: {
        const changed = this.provider().lastStateChange;
        if (!changed) return 'No state change observed since service startup.';
        const seconds = Math.max(0, Math.floor((this.now() - Date.parse(changed)) / 1000));
        if (!Number.isFinite(seconds)) return 'Last state change unavailable.';
        if (seconds < 60) return 'State changed just now';
        if (seconds < 3600) return `State changed ${Math.floor(seconds / 60)}m ago`;
        if (seconds < 86400) return `State changed ${Math.floor(seconds / 3600)}h ago`;
        return `State changed ${Math.floor(seconds / 86400)}d ago`;
      }
    }
  });
}
