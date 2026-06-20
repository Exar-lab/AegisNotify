-- AegisNotify Core Schema
-- PostgreSQL 15+

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- TEMPLATES
-- ============================================================
CREATE TABLE templates (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(120) NOT NULL UNIQUE,
    channel     VARCHAR(20)  NOT NULL,
    subject     VARCHAR(255),
    body        TEXT         NOT NULL,
    variables   TEXT[],
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_template_channel CHECK (channel IN ('EMAIL', 'SMS', 'WHATSAPP', 'PUSH'))
);

CREATE INDEX idx_templates_name_active ON templates (name) WHERE active = TRUE;

-- ============================================================
-- NOTIFICATIONS
-- ============================================================
CREATE TABLE notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel         VARCHAR(20)   NOT NULL,
    recipient       VARCHAR(320)  NOT NULL,
    template_name   VARCHAR(120)  NOT NULL,
    parameters      JSONB         NOT NULL DEFAULT '{}',
    priority        VARCHAR(10)   NOT NULL DEFAULT 'MEDIUM',
    status          VARCHAR(30)   NOT NULL DEFAULT 'PENDING',
    provider_used   VARCHAR(60),
    error_detail    TEXT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT chk_notification_channel  CHECK (channel  IN ('EMAIL', 'SMS', 'WHATSAPP', 'PUSH')),
    CONSTRAINT chk_notification_priority CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT chk_notification_status   CHECK (status   IN (
        'PENDING', 'QUEUED', 'PROCESSING',
        'SENT', 'SENT_VIA_FALLBACK',
        'FAILED', 'FAILED_CRITICAL'
    ))
);

CREATE INDEX idx_notifications_status     ON notifications (status);
CREATE INDEX idx_notifications_channel    ON notifications (channel);
CREATE INDEX idx_notifications_created_at ON notifications (created_at);

-- ============================================================
-- TRANSACTIONAL OUTBOX
-- ============================================================
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id UUID         NOT NULL REFERENCES notifications(id),
    payload         JSONB        NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'UNPROCESSED',
    retry_count     INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at    TIMESTAMPTZ,

    CONSTRAINT chk_outbox_status CHECK (status IN ('UNPROCESSED', 'PROCESSING', 'PROCESSED', 'FAILED'))
);

CREATE INDEX idx_outbox_unprocessed ON outbox_events (created_at) WHERE status = 'UNPROCESSED';

-- ============================================================
-- AUDIT TRAIL (notification_logs)
-- ============================================================
CREATE TABLE notification_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id UUID         NOT NULL REFERENCES notifications(id),
    status          VARCHAR(30)  NOT NULL,
    details         TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_log_status CHECK (status IN (
        'PENDING', 'QUEUED', 'PROCESSING',
        'SENT', 'SENT_VIA_FALLBACK',
        'PROVIDER_A_FAIL', 'PROVIDER_B_FAIL',
        'FAILED', 'FAILED_CRITICAL'
    ))
);

CREATE INDEX idx_notification_logs_nid ON notification_logs (notification_id, created_at);
