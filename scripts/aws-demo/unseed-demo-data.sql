-- Removes exactly what seed-demo-data.sql created (issue #238): the 160 seeded notifications, their
-- status logs and delivery attempts, and the six demo templates. Real notifications are untouched.
--
-- The demo templates are deleted by name. If you created your own template with one of those names
-- (for example welcome-email) it is deleted as well; keep a copy first if that matters.
BEGIN;

CREATE TEMP TABLE demo_seed_ids ON COMMIT DROP AS
SELECT md5('aegis-demo-seed-' || n)::uuid AS id FROM generate_series(1, 160) AS n;

DELETE FROM delivery_attempts WHERE notification_id IN (SELECT id FROM demo_seed_ids);
DELETE FROM notification_logs WHERE notification_id IN (SELECT id FROM demo_seed_ids);
DELETE FROM outbox_events WHERE notification_id IN (SELECT id FROM demo_seed_ids);
DELETE FROM notifications WHERE id IN (SELECT id FROM demo_seed_ids);
DELETE FROM templates WHERE name IN ('welcome-email', 'order-shipped-email', 'otp-sms',
  'welcome-whatsapp', 'new-login-push', 'legacy-newsletter');

COMMIT;
