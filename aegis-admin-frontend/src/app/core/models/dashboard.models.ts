export interface MetricDelta {
  currentValue: number;
  previousValue: number;
  deltaPercent?: number | null;
  deltaPoints?: number | null;
}

export interface DashboardSummary {
  totalRequests: MetricDelta;
  successRate: MetricDelta;
  fallbackActivations: MetricDelta;
  avgDeliveryLatency: MetricDelta;
}

export type Channel = 'EMAIL' | 'SMS' | 'WHATSAPP' | 'PUSH';
export type Priority = 'HIGH' | 'MEDIUM' | 'LOW';
export type NotificationStatus =
  | 'PENDING'
  | 'QUEUED'
  | 'PROCESSING'
  | 'SENT'
  | 'SENT_VIA_FALLBACK'
  | 'FAILED'
  | 'FAILED_CRITICAL'
  | 'CANCELLED';

export interface NotificationSummary {
  id: string;
  channel: Channel;
  recipient: string;
  templateName: string;
  status: NotificationStatus;
  priority: Priority;
  createdAt: string;
}

export type CircuitBreakerState =
  | 'CLOSED'
  | 'OPEN'
  | 'HALF_OPEN'
  | 'DISABLED'
  | 'FORCED_OPEN'
  | 'METRICS_ONLY'
  | 'UNKNOWN';

export interface ProviderCircuitBreakerStatus {
  key: string;
  name: string;
  channel: Channel;
  role: string;
  fallbackProvider: string;
  state: CircuitBreakerState;
}
