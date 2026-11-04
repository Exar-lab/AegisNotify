-- Issue #225: persist the summarizer subject for the aggregate leader.
-- Additive & nullable: forward-compatible with the previous jar (rollback safe).
ALTER TABLE notifications
    ADD COLUMN aggregate_subject VARCHAR(255);
