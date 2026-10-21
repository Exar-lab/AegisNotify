import { NotificationSummary } from './dashboard.models';
export interface NotificationPage {
  content: (NotificationSummary & { providerUsed: string | null })[];
  totalElements: number;
  page: number;
  size: number;
}
