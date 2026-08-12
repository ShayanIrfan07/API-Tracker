-- Phase 5: latency threshold for DEGRADED status evaluation

ALTER TABLE monitored_api
    ADD COLUMN latency_threshold_ms INTEGER NULL;

ALTER TABLE monitored_api
    ADD CONSTRAINT chk_monitored_api_latency_threshold_positive
        CHECK (latency_threshold_ms IS NULL OR latency_threshold_ms > 0);
