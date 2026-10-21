/** Shared display conventions for notification list, detail and dashboard. */
export function notificationTone(status: string): string {
  switch (status) {
    case 'SENT':
      return 'success';
    case 'SENT_VIA_FALLBACK':
      return 'fallback';
    case 'FAILED':
    case 'FAILED_CRITICAL':
    case 'PROVIDER_A_FAIL':
    case 'PROVIDER_B_FAIL':
      return 'danger';
    case 'QUEUED':
    case 'PROCESSING':
      return 'warning';
    default:
      return 'neutral';
  }
}
export function notificationIcon(status: string): string {
  switch (status) {
    case 'SENT':
      return '✓';
    case 'SENT_VIA_FALLBACK':
      return '↪';
    case 'FAILED':
    case 'FAILED_CRITICAL':
    case 'PROVIDER_A_FAIL':
    case 'PROVIDER_B_FAIL':
      return '!';
    case 'CANCELLED':
      return '×';
    case 'PROCESSING':
      return '↻';
    case 'QUEUED':
      return '→';
    default:
      return '◷';
  }
}
