-- Phase 6: enrich alerts with failure reason and resolved duration for MTTR

ALTER TABLE alert
    ADD COLUMN failure_reason VARCHAR(500) NULL,
    ADD COLUMN duration_seconds BIGINT NULL;

CREATE INDEX idx_alert_resolved_at ON alert (resolved_at)
    WHERE status = 'RESOLVED';
