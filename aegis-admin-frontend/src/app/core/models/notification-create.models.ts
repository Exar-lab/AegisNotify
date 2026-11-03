import { TemplateChannel } from './template.models';

export type NotificationPriority = 'HIGH' | 'MEDIUM' | 'LOW';

export const NOTIFICATION_PRIORITIES: readonly NotificationPriority[] = ['HIGH', 'MEDIUM', 'LOW'];

/** Mirrors the notification-service CreateNotificationRequest (`POST /api/v1/notifications`). */
export interface CreateNotificationRequest {
  channel: TemplateChannel;
  /** Max 320 chars. EMAIL address, E.164 phone for SMS/WHATSAPP, device token for PUSH. */
  recipient: string;
  /** Name (not id) of an active template; max 120 chars. */
  templateName: string;
  parameters: Record<string, string>;
  priority: NotificationPriority;
}

/** Mirrors the notification-service NotificationResponse returned with HTTP 202. */
export interface CreatedNotification {
  id: string;
  status: string;
}
