INSERT INTO notifications
(id, channel, recipient, template_name, parameters, priority, status, provider_used, created_at, updated_at)
SELECT md5('issue84-manual-' || i)::uuid,
       (ARRAY['EMAIL','SMS','WHATSAPP','PUSH'])[1 + i % 4],
       'issue84-' || lpad(i::text,2,'0') || '@example.com',
       'issue84-manual-fixture', '{}'::jsonb,
       (ARRAY['HIGH','MEDIUM','LOW'])[1 + i % 3],
       (ARRAY['PENDING','QUEUED','PROCESSING','SENT','SENT_VIA_FALLBACK','FAILED','FAILED_CRITICAL','CANCELLED'])[1 + i % 8],
       CASE WHEN i % 8 IN (3,4,5,6) THEN 'Local fixture provider' ELSE NULL END,
       timestamptz '2026-10-13 18:00:00+00' + (i / 12) * interval '1 day' + i * interval '1 minute',
       timestamptz '2026-10-13 18:00:00+00' + (i / 12) * interval '1 day' + i * interval '1 minute'
FROM generate_series(0,23) AS series(i)
ON CONFLICT (id) DO NOTHING;
