-- AegisNotify Aggregation/Summarization Agent (issue #86)
-- Additive & nullable: forward-compatible with the previous jar (rollback safe).

-- ============================================================
-- NOTIFICATIONS — aggregation linkage columns
-- ============================================================
ALTER TABLE notifications
    ADD COLUMN aggregation_id UUID,
    ADD COLUMN aggregate_body TEXT;

CREATE INDEX idx_notifications_aggregation_id ON notifications (aggregation_id)
    WHERE aggregation_id IS NOT NULL;

-- ============================================================
-- AGGREGATION BUFFER
-- ============================================================
CREATE TABLE aggregation_buffer (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id UUID         NOT NULL REFERENCES notifications(id),
    channel         VARCHAR(20)  NOT NULL,
    recipient       VARCHAR(320) NOT NULL,
    template_name   VARCHAR(120),
    priority        VARCHAR(10)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'BUFFERED',
    expires_at      TIMESTAMPTZ  NOT NULL,
    claimed_at      TIMESTAMPTZ,
    attempts        INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_aggregation_buffer_channel  CHECK (channel  IN ('EMAIL', 'SMS', 'WHATSAPP', 'PUSH')),
    CONSTRAINT chk_aggregation_buffer_priority CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT chk_aggregation_buffer_status   CHECK (status   IN ('BUFFERED', 'CLAIMED', 'DONE'))
);

-- Supports the flush poller's claimable-window scan: expired BUFFERED rows
-- plus stale CLAIMED rows past their lease (B1/B3 of the design).
CREATE INDEX idx_aggregation_buffer_claimable ON aggregation_buffer (status, expires_at, claimed_at);
CREATE INDEX idx_aggregation_buffer_notification_id ON aggregation_buffer (notification_id);
