import { DecimalPipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Subject, catchError, exhaustMap, map, merge, of, scan, timer } from 'rxjs';
import { Channel, ProviderCircuitBreakerStatus } from '../../../core/models/dashboard.models';
import { DashboardApiService } from '../../../core/services/dashboard-api.service';
import {
  ChannelTones,
  NEUTRAL_TONES,
  ProviderShieldComponent,
  ShieldTone,
  toneOfCircuit,
  worstTone,
} from '../components/provider-shield/provider-shield.component';
import { ProviderCardComponent } from '../provider-card.component';

interface ProviderState {
  providers: ProviderCircuitBreakerStatus[] | null;
  error: boolean;
  updatedAt: number | null;
}

@Component({
  selector: 'app-providers-page',
  standalone: true,
  imports: [DecimalPipe, ProviderCardComponent, ProviderShieldComponent],
  templateUrl: './providers.page.html',
  styleUrl: './providers.page.scss',
})
export class ProvidersPage {
  private readonly api = inject(DashboardApiService);
  private readonly refreshRequests = new Subject<void>();
  readonly state = toSignal(
    merge(timer(0, 15_000), this.refreshRequests).pipe(
      exhaustMap(() =>
        this.api.getCircuitBreakers().pipe(
          map((providers) => ({ providers, error: false, updatedAt: Date.now() })),
          catchError(() => of({ error: true })),
        ),
      ),
      scan((previous: ProviderState, update): ProviderState => ({ ...previous, ...update }), {
        providers: null,
        error: false,
        updatedAt: null,
      }),
    ),
    { initialValue: { providers: null, error: false, updatedAt: null } as ProviderState },
  );
  readonly groups = computed(() =>
    (
      [
        { channel: 'EMAIL', label: 'Email', icon: 'mail' },
        { channel: 'SMS', label: 'SMS', icon: 'sms' },
        { channel: 'WHATSAPP', label: 'WhatsApp', icon: 'chat' },
        { channel: 'PUSH', label: 'Push', icon: 'notifications_active' },
      ] as const
    ).map((group) => ({
      ...group,
      providers: (this.state().providers ?? [])
        .filter((provider) => provider.channel === group.channel)
        .sort((a, b) => Number(b.role === 'PRIMARY') - Number(a.role === 'PRIMARY')),
    })),
  );
  /** Per-channel health for the backdrop shield: real data only, calm neutral until a snapshot has loaded. */
  readonly channelTones = computed<ChannelTones>(() => {
    const providers = this.state().providers;
    if (providers === null) return NEUTRAL_TONES;
    const toneOf = (channel: Channel): ShieldTone =>
      worstTone(
        providers.filter((provider) => provider.channel === channel).map((p) => toneOfCircuit(p.state)),
      );
    return { EMAIL: toneOf('EMAIL'), SMS: toneOf('SMS'), WHATSAPP: toneOf('WHATSAPP'), PUSH: toneOf('PUSH') };
  });
  readonly summary = computed(() => {
    const providers = this.state().providers ?? [];
    return {
      healthy: providers.filter((provider) => provider.state === 'CLOSED').length,
      degraded: providers.filter((provider) => provider.state === 'HALF_OPEN').length,
      open: providers.filter((provider) => ['OPEN', 'FORCED_OPEN'].includes(provider.state)).length,
      calls: providers.reduce((sum, provider) => sum + (provider.callsToday ?? 0), 0),
    };
  });

  refresh(): void {
    this.refreshRequests.next();
  }
}
