-- Add CANCELLED to the notifications status CHECK constraint.
-- NotificationStatus.CANCELLED is produced by Notification.markCancelled() and
-- persisted by CancelNotificationService, but was missing from the original
-- constraint, causing UPDATEs to fail on cancellation. V2 already applied the
-- equivalent fix to chk_log_status; this is the notifications-table half.

ALTER TABLE notifications DROP CONSTRAINT chk_notification_status;

ALTER TABLE notifications ADD CONSTRAINT chk_notification_status CHECK (status IN (
    'PENDING', 'QUEUED', 'PROCESSING',
    'SENT', 'SENT_VIA_FALLBACK',
    'FAILED', 'FAILED_CRITICAL',
    'CANCELLED'
));
