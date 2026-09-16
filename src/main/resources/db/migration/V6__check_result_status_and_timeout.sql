-- Phase 5: enrich check results with evaluated API status and timeout flag

ALTER TABLE check_result
    ADD COLUMN api_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN timed_out BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE check_result
    ADD CONSTRAINT chk_check_result_api_status
        CHECK (api_status IN ('UNKNOWN', 'UP', 'DOWN', 'DEGRADED'));
