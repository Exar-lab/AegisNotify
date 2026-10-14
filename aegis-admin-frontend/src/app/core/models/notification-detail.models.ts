import { Channel, NotificationStatus } from './dashboard.models';

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
}

export interface ProviderAttempt {
  role: 'Primary' | 'Fallback' | 'Unavailable';
  provider: string;
  result: 'Success' | 'Failure';
  details: string | null;
  timestamp: string;
}

export function chronologicalLogs(logs: NotificationLogEntry[]): NotificationLogEntry[] {
  return [...logs].sort((a, b) => Date.parse(a.timestamp) - Date.parse(b.timestamp));
}

/** Only recorded provider events are shown; terminal DLQ events are not attempts. */
export function providerAttempts(logs: NotificationLogEntry[]): ProviderAttempt[] {
  return chronologicalLogs(logs).flatMap((entry): ProviderAttempt[] => {
    switch (entry.status) {
      case 'SENT':
      case 'SENT_VIA_FALLBACK': {
        const fallback = entry.status === 'SENT_VIA_FALLBACK';
        const prefix = fallback ? 'Sent via fallback provider ' : 'Sent via ';
        const provider = entry.details?.startsWith(prefix)
          ? entry.details.slice(prefix.length).trim() || 'Unavailable'
          : 'Unavailable';
        return [
          {
            role: fallback ? 'Fallback' : 'Primary',
            provider,
            result: 'Success',
            details: entry.details,
            timestamp: entry.timestamp,
          },
        ];
      }
      case 'PROVIDER_A_FAIL':
      case 'PROVIDER_B_FAIL':
        return [
          {
            role: entry.status === 'PROVIDER_A_FAIL' ? 'Primary' : 'Fallback',
            provider: 'Unavailable',
            result: 'Failure',
            details: entry.details,
            timestamp: entry.timestamp,
          },
        ];
      case 'FAILED':
        return [
          {
            role: 'Unavailable',
            provider: 'Unavailable',
            result: 'Failure',
            details: entry.details,
            timestamp: entry.timestamp,
          },
        ];
      default:
        return [];
    }
  });
}
