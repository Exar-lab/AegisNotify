-- Add CANCELLED to the notification_logs status CHECK constraint.
-- LogStatus.CANCELLED is used by CancelNotificationService but was missing
-- from the original constraint, causing INSERTs to fail on cancellation.

ALTER TABLE notification_logs DROP CONSTRAINT chk_log_status;

ALTER TABLE notification_logs ADD CONSTRAINT chk_log_status CHECK (status IN (
    'PENDING', 'QUEUED', 'PROCESSING',
    'SENT', 'SENT_VIA_FALLBACK',
    'PROVIDER_A_FAIL', 'PROVIDER_B_FAIL',
    'FAILED', 'FAILED_CRITICAL',
    'CANCELLED'
));
