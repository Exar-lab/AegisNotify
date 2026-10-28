export type TemplateChannel = 'EMAIL' | 'SMS' | 'WHATSAPP' | 'PUSH';

export const TEMPLATE_CHANNELS: readonly TemplateChannel[] = ['EMAIL', 'SMS', 'WHATSAPP', 'PUSH'];

/** Mirrors the notification-service TemplateResponse (timestamps are ISO-8601 instants). */
export interface TemplateResponse {
  id: string;
  name: string;
  channel: TemplateChannel;
  subject: string | null;
  body: string;
  variables: string[];
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTemplateRequest {
  name: string;
  channel: TemplateChannel;
  subject?: string;
  body: string;
  variables?: string[];
}

/** `name` and `channel` are immutable after creation. */
export interface UpdateTemplateRequest {
  subject?: string;
  body: string;
  variables?: string[];
}

export interface SetTemplateStatusRequest {
  active: boolean;
}
