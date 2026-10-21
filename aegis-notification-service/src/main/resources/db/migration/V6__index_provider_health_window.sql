CREATE INDEX idx_delivery_attempts_health_window
    ON delivery_attempts (attempted_at, notification_id);
