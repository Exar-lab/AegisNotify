-- AWS demo seed data (issue #238). SYNTHETIC: nothing here was ever sent to anyone.
--
-- Loads active templates and 160 historical notifications (with their status timelines and
-- provider delivery attempts) so the Dashboard, Notifications list, detail view and provider
-- health have something to show before the first real send.
--
-- Run after the notification service has started once (Flyway creates the schema), on the
-- `aegisnotify` database. Idempotent: ids are derived from md5('aegis-demo-seed-<n>'), and every
-- insert skips rows that already exist, so it is safe to run again. It does not touch rows that
-- were not created by this file. Remove it with unseed-demo-data.sql.
--
-- Do NOT use "Retry" on these notifications: it would try to deliver to the fake recipients.
-- (Emails use example.com; phone numbers and device ids are fictional.)
--
-- Not covered: the Audit screen reads encrypted events from MongoDB, which are written when real
-- notifications are processed, and the circuit breaker table is live in-memory state.
BEGIN;

INSERT INTO templates (name, channel, subject, body, variables, active) VALUES
  ('welcome-email', 'EMAIL', 'Hola, {{name}}',
   '<p>Hola {{name}}, esto es una prueba de AegisNotify.</p>', ARRAY['name'], TRUE),
  ('order-shipped-email', 'EMAIL', 'Tu pedido {{orderId}} va en camino',
   '<p>Hola {{name}}, tu pedido <strong>{{orderId}}</strong> ya salio del almacen.</p>',
   ARRAY['name', 'orderId'], TRUE),
  ('otp-sms', 'SMS', NULL, 'Tu codigo de verificacion es {{code}}', ARRAY['code'], TRUE),
  ('welcome-whatsapp', 'WHATSAPP', NULL,
   'Hola {{name}}, esto es una prueba de AegisNotify por WhatsApp.', ARRAY['name'], TRUE),
  ('new-login-push', 'PUSH', 'Nuevo inicio de sesion', 'Se inicio sesion desde {{device}}.',
   ARRAY['device'], TRUE),
  ('legacy-newsletter', 'EMAIL', 'Boletin', 'Plantilla desactivada de ejemplo para {{name}}.',
   ARRAY['name'], FALSE)
ON CONFLICT (name) DO NOTHING;

-- One row per seeded notification, computed once from a deterministic hash of its number.
CREATE TEMP TABLE demo_seed ON COMMIT DROP AS
WITH raw AS (
  SELECT n, md5('aegis-demo-seed-' || n) AS h FROM generate_series(1, 160) AS n
), r AS (
  SELECT n, h,
         get_byte(decode(h, 'hex'), 0) / 255.0 AS r_channel,
         get_byte(decode(h, 'hex'), 1) / 255.0 AS r_status,
         get_byte(decode(h, 'hex'), 2) / 255.0 AS r_priority,
         get_byte(decode(h, 'hex'), 3) / 255.0 AS r_time,
         get_byte(decode(h, 'hex'), 4) / 255.0 AS r_latency,
         get_byte(decode(h, 'hex'), 5) / 255.0 AS r_template
  FROM raw
), s AS (
  SELECT n, h, r_latency,
         CASE WHEN r_channel < 0.45 THEN 'EMAIL' WHEN r_channel < 0.65 THEN 'SMS'
              WHEN r_channel < 0.90 THEN 'WHATSAPP' ELSE 'PUSH' END AS channel,
         CASE WHEN r_status < 0.82 THEN 'SENT' WHEN r_status < 0.90 THEN 'SENT_VIA_FALLBACK'
              WHEN r_status < 0.945 THEN 'FAILED' WHEN r_status < 0.98 THEN 'FAILED_CRITICAL'
              ELSE 'CANCELLED' END AS status,
         CASE WHEN r_priority < 0.20 THEN 'HIGH' WHEN r_priority < 0.75 THEN 'MEDIUM'
              ELSE 'LOW' END AS priority,
         -- 60 in the last 24 h, 40 in the 24 h before, 60 spread over the previous 12 days
         now() - CASE WHEN n <= 60 THEN r_time * interval '23 hours' + interval '30 minutes'
                      WHEN n <= 100 THEN interval '24 hours' + r_time * interval '23 hours'
                      ELSE interval '48 hours' + r_time * interval '12 days' END AS created_at,
         r_template
  FROM r
)
SELECT n, md5('aegis-demo-seed-' || n)::uuid AS id, channel, status, priority, created_at, r_latency,
       CASE channel
         WHEN 'EMAIL' THEN 'demo.user.' || n || '@example.com'
         WHEN 'PUSH' THEN 'demo-device-' || lpad(n::text, 4, '0')
         ELSE '+34600' || lpad(n::text, 6, '0') END AS recipient,
       CASE channel
         WHEN 'EMAIL' THEN CASE WHEN r_template < 0.6 THEN 'welcome-email' ELSE 'order-shipped-email' END
         WHEN 'SMS' THEN 'otp-sms' WHEN 'WHATSAPP' THEN 'welcome-whatsapp'
         ELSE 'new-login-push' END AS template_name,
       CASE channel
         WHEN 'EMAIL' THEN CASE WHEN r_template < 0.6
              THEN jsonb_build_object('name', 'Demo User ' || n)
              ELSE jsonb_build_object('name', 'Demo User ' || n, 'orderId', 'ORD-' || lpad(n::text, 5, '0')) END
         WHEN 'SMS' THEN jsonb_build_object('code', lpad(((get_byte(decode(h, 'hex'), 6) * 3917 + n * 131) % 1000000)::text, 6, '0'))
         WHEN 'WHATSAPP' THEN jsonb_build_object('name', 'Demo User ' || n)
         ELSE jsonb_build_object('device', 'Demo Device ' || n) END AS parameters,
       CASE channel WHEN 'EMAIL' THEN 'Mailtrap' WHEN 'SMS' THEN 'Twilio'
                    WHEN 'WHATSAPP' THEN 'Vonage' ELSE 'FCM' END AS primary_provider,
       CASE channel WHEN 'EMAIL' THEN 'SendGrid' WHEN 'SMS' THEN 'Twilio'
                    WHEN 'WHATSAPP' THEN 'Twilio' ELSE 'FCM' END AS fallback_provider,
       -- seconds from creation until the terminal state: 3 s to about 9 s
       3 + floor(r_latency * 7)::int AS terminal_seconds
FROM s;

INSERT INTO notifications (id, channel, recipient, template_name, parameters, priority, status,
                           provider_used, error_detail, created_at, updated_at)
SELECT id, channel, recipient, template_name, parameters, priority, status,
       CASE status WHEN 'SENT' THEN primary_provider WHEN 'SENT_VIA_FALLBACK' THEN fallback_provider END,
       CASE status WHEN 'FAILED' THEN 'Recipient rejected by provider (synthetic)'
                   WHEN 'FAILED_CRITICAL' THEN 'Primary and fallback providers failed (synthetic)' END,
       created_at,
       created_at + (CASE status WHEN 'CANCELLED' THEN 2 ELSE terminal_seconds END) * interval '1 second'
FROM demo_seed
ON CONFLICT (id) DO NOTHING;

-- Status timeline (skipped for notifications that already have one).
INSERT INTO notification_logs (id, notification_id, status, details, created_at)
SELECT gen_random_uuid(), d.id, e.status, e.details, d.created_at + e.seconds * interval '1 second'
FROM demo_seed d
CROSS JOIN LATERAL (VALUES
  ('PENDING', 'Notification accepted (synthetic)', 0, TRUE),
  ('QUEUED', 'Queued for delivery (synthetic)', 1, d.status <> 'CANCELLED'),
  ('PROCESSING', 'Processing started (synthetic)', 2, d.status <> 'CANCELLED'),
  ('PROVIDER_A_FAIL', 'Primary provider ' || d.primary_provider || ' failed (synthetic)',
     d.terminal_seconds - 1, d.status IN ('SENT_VIA_FALLBACK', 'FAILED_CRITICAL')),
  ('PROVIDER_B_FAIL', 'Fallback provider ' || d.fallback_provider || ' failed (synthetic)',
     d.terminal_seconds - 1, d.status = 'FAILED_CRITICAL'),
  ('SENT', 'Sent via ' || d.primary_provider || ' (synthetic)', d.terminal_seconds, d.status = 'SENT'),
  ('SENT_VIA_FALLBACK', 'Sent via fallback provider ' || d.fallback_provider || ' (synthetic)',
     d.terminal_seconds, d.status = 'SENT_VIA_FALLBACK'),
  ('FAILED', 'Delivery failed: recipient rejected (synthetic)', d.terminal_seconds, d.status = 'FAILED'),
  ('FAILED_CRITICAL', 'Critical failure, sent to DLQ (synthetic)', d.terminal_seconds, d.status = 'FAILED_CRITICAL'),
  ('CANCELLED', 'Cancelled by an operator (synthetic)', 2, d.status = 'CANCELLED')
) AS e(status, details, seconds, applies)
WHERE e.applies
  AND NOT EXISTS (SELECT 1 FROM notification_logs l WHERE l.notification_id = d.id);

-- Provider delivery attempts (skipped for notifications that already have some).
INSERT INTO delivery_attempts (id, notification_id, provider_name, provider_role, success,
                               result_code, error_detail, latency_millis, attempted_at)
SELECT gen_random_uuid(), d.id, a.provider, a.role, a.success, a.code, a.detail, a.latency,
       d.created_at + a.seconds * interval '1 second'
FROM demo_seed d
CROSS JOIN LATERAL (VALUES
  (d.primary_provider, 'PRIMARY', TRUE, '202', NULL::text,
     (80 + d.r_latency * 700)::bigint, 3, d.status = 'SENT'),
  (d.primary_provider, 'PRIMARY', FALSE,
     CASE WHEN d.n % 2 = 0 THEN '503' ELSE 'CIRCUIT_OPEN' END,
     CASE WHEN d.n % 2 = 0 THEN 'Service unavailable' ELSE 'Circuit breaker open, call skipped' END,
     CASE WHEN d.n % 2 = 0 THEN (900 + d.r_latency * 1500)::bigint ELSE 0::bigint END, 3,
     d.status IN ('SENT_VIA_FALLBACK', 'FAILED_CRITICAL')),
  (d.fallback_provider, 'FALLBACK', TRUE, '202', NULL::text,
     (90 + d.r_latency * 600)::bigint, 4, d.status = 'SENT_VIA_FALLBACK'),
  (d.fallback_provider, 'FALLBACK', FALSE, '502', 'Bad gateway',
     (700 + d.r_latency * 1200)::bigint, 4, d.status = 'FAILED_CRITICAL'),
  (d.primary_provider, 'PRIMARY', FALSE, '422', 'Recipient rejected by provider',
     (60 + d.r_latency * 300)::bigint, 3, d.status = 'FAILED')
) AS a(provider, role, success, code, detail, latency, seconds, applies)
WHERE a.applies
  AND NOT EXISTS (SELECT 1 FROM delivery_attempts x WHERE x.notification_id = d.id);

COMMIT;
