-- LOCAL DEMO ONLY. These synthetic rows demonstrate rendering, not real deliveries.
-- Run after Flyway V5. Safe to rerun without duplicating the fixtures.
BEGIN;

INSERT INTO templates (name, channel, subject, body, variables)
VALUES ('detail-evidence', 'EMAIL', 'Notification detail evidence', 'Hello {{name}}', ARRAY['name'])
ON CONFLICT (name) DO NOTHING;

INSERT INTO notifications
    (id, channel, recipient, template_name, priority, status, created_at, updated_at)
SELECT ('83000000-0000-4000-8000-00000000000' || n)::uuid,
       'EMAIL', 'evidence-' || n || '@example.com', 'detail-evidence', 'HIGH', status,
       '2026-10-14T12:00:00Z'::timestamptz, '2026-10-14T12:00:05Z'::timestamptz
FROM (VALUES (1, 'SENT'), (2, 'SENT_VIA_FALLBACK'), (3, 'FAILED_CRITICAL'),
             (4, 'PENDING')) AS scenarios(n, status)
ON CONFLICT (id) DO NOTHING;

INSERT INTO notification_logs (id, notification_id, status, details, created_at)
SELECT gen_random_uuid(), notification.id, event.status, event.details,
       notification.created_at + event.seconds * interval '1 second'
FROM notifications notification
CROSS JOIN (VALUES
    ('PENDING', 'Notification accepted (synthetic evidence)', 0),
    ('QUEUED', 'Queued for delivery (synthetic evidence)', 1),
    ('PROCESSING', 'Processing started (synthetic evidence)', 2)
) AS event(status, details, seconds)
WHERE notification.id IN (
    '83000000-0000-4000-8000-000000000001', '83000000-0000-4000-8000-000000000002',
    '83000000-0000-4000-8000-000000000003', '83000000-0000-4000-8000-000000000004')
AND (notification.status <> 'PENDING' OR event.status = 'PENDING')
AND NOT EXISTS (SELECT 1 FROM notification_logs existing
                WHERE existing.notification_id = notification.id AND existing.status = event.status);

INSERT INTO notification_logs (id, notification_id, status, details, created_at)
SELECT gen_random_uuid(), notification.id, notification.status,
       CASE notification.status
         WHEN 'SENT' THEN 'Sent via SendGrid (synthetic evidence)'
         WHEN 'SENT_VIA_FALLBACK' THEN 'Sent via fallback provider SendGrid (synthetic evidence)'
         ELSE 'Critical failure, sent to DLQ (synthetic evidence)'
       END, notification.updated_at
FROM notifications notification
WHERE notification.id IN (
    '83000000-0000-4000-8000-000000000001', '83000000-0000-4000-8000-000000000002',
    '83000000-0000-4000-8000-000000000003')
AND NOT EXISTS (SELECT 1 FROM notification_logs existing
                WHERE existing.notification_id = notification.id
                  AND existing.status = notification.status);

INSERT INTO delivery_attempts
    (id, notification_id, provider_name, provider_role, success,
     result_code, error_detail, latency_millis, attempted_at)
SELECT gen_random_uuid(), ('83000000-0000-4000-8000-00000000000' || n)::uuid,
       'SendGrid', role, success, code, detail, latency,
       '2026-10-14T12:00:00Z'::timestamptz + seconds * interval '1 second'
FROM (VALUES
    (1, 'PRIMARY', true, '202', NULL, 25, 3),
    (2, 'PRIMARY', false, '503', 'Service unavailable', 42, 3),
    (2, 'PRIMARY', false, '503', 'Service unavailable', 31, 4),
    (2, 'FALLBACK', true, '202', NULL, 18, 5),
    (3, 'PRIMARY', false, '401', 'Primary authentication rejected', 24, 3),
    (3, 'FALLBACK', false, '503', 'Fallback service unavailable', 37, 4)
) AS attempt(n, role, success, code, detail, latency, seconds)
WHERE NOT EXISTS (
    SELECT 1 FROM delivery_attempts existing
    WHERE existing.notification_id = ('83000000-0000-4000-8000-00000000000' || n)::uuid
      AND existing.attempted_at = '2026-10-14T12:00:00Z'::timestamptz
          + seconds * interval '1 second'
      AND existing.provider_role = role);

INSERT INTO notification_logs (id, notification_id, status, details, created_at)
SELECT gen_random_uuid(), attempt.notification_id,
       CASE attempt.provider_role WHEN 'PRIMARY' THEN 'PROVIDER_A_FAIL' ELSE 'PROVIDER_B_FAIL' END,
       attempt.provider_name || ' [' || attempt.result_code || ']: ' || attempt.error_detail,
       attempt.attempted_at
FROM delivery_attempts attempt
WHERE attempt.notification_id IN (
    '83000000-0000-4000-8000-000000000002', '83000000-0000-4000-8000-000000000003')
AND NOT attempt.success
AND NOT EXISTS (SELECT 1 FROM notification_logs existing
                WHERE existing.notification_id = attempt.notification_id
                  AND existing.created_at = attempt.attempted_at
                  AND existing.status IN ('PROVIDER_A_FAIL', 'PROVIDER_B_FAIL'));

COMMIT;
