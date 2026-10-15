import { Channel, NotificationStatus, Priority } from './dashboard.models';

export type LogStatus = NotificationStatus | 'PROVIDER_A_FAIL' | 'PROVIDER_B_FAIL';

export interface NotificationLogEntry {
  status: LogStatus;
  details: string | null;
  timestamp: string;
}

/** Matches the existing notification status endpoint. */
export interface NotificationDetail {
  id: string;
  channel: Channel;
  recipient: string;
  templateName: string;
  status: NotificationStatus;
  createdAt: string;
  updatedAt: string;
  auditTrail: NotificationLogEntry[];
  priority: Priority;
  correlationId: string;
  providerAttempts: ProviderAttempt[];
}

export interface ProviderAttempt {
  role: 'PRIMARY' | 'FALLBACK';
  providerName: string;
  success: boolean;
  code: string | null;
  errorDetail: string | null;
  latencyMillis: number;
  timestamp: string;
}
export function chronologicalLogs(logs: NotificationLogEntry[]): NotificationLogEntry[] {
  return [...logs].sort((a, b) => Date.parse(a.timestamp) - Date.parse(b.timestamp));
}
