/**
 * Mirrors com.aegisnotify.notification.application.dto.DashboardSummaryResponse.
 */
export interface DashboardSummary {
  totalRequests: number;
  successRatePercent: number;
  fallbackActivations: number;
  avgDeliveryLatencyMillis: number;
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

/**
 * Mirrors com.aegisnotify.notification.application.dto.NotificationSummary.
 */
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
  | 'METRICS_ONLY';

/**
 * Mirrors Resilience4j's CircuitBreakerDetails, as returned by
 * GET /actuator/circuitbreakers.
 */
export interface CircuitBreakerDetails {
  failureRate: string;
  slowCallRate: string;
  bufferedCalls: number;
  failedCalls: number;
  slowCalls: number;
  notPermittedCalls: number;
  state: CircuitBreakerState;
}

export interface CircuitBreakersResponse {
  circuitBreakers: Record<string, CircuitBreakerDetails>;
}
