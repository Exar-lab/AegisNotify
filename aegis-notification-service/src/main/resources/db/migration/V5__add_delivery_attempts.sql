CREATE TABLE delivery_attempts (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL REFERENCES notifications(id),
    provider_name VARCHAR(100) NOT NULL,
    provider_role VARCHAR(20) NOT NULL CHECK (provider_role IN ('PRIMARY', 'FALLBACK')),
    success BOOLEAN NOT NULL,
    result_code VARCHAR(100),
    error_detail TEXT,
    latency_millis BIGINT NOT NULL CHECK (latency_millis >= 0),
    attempted_at TIMESTAMPTZ NOT NULL,
    sequence_number BIGSERIAL NOT NULL
);

CREATE INDEX idx_delivery_attempts_notification
    ON delivery_attempts (notification_id, sequence_number);
