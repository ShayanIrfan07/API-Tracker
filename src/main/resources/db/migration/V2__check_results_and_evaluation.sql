-- Phase 2: check results and evaluation state for health monitoring

ALTER TABLE monitored_api
    ADD COLUMN failure_threshold     INTEGER      NOT NULL DEFAULT 3,
    ADD COLUMN success_threshold     INTEGER      NOT NULL DEFAULT 2,
    ADD COLUMN consecutive_failures  INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN consecutive_successes INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN last_checked_at       TIMESTAMPTZ  NULL,
    ADD COLUMN last_status_change_at TIMESTAMPTZ  NULL;

ALTER TABLE monitored_api
    ADD CONSTRAINT chk_monitored_api_failure_threshold_positive
        CHECK (failure_threshold > 0),
    ADD CONSTRAINT chk_monitored_api_success_threshold_positive
        CHECK (success_threshold > 0),
    ADD CONSTRAINT chk_monitored_api_consecutive_failures_nonneg
        CHECK (consecutive_failures >= 0),
    ADD CONSTRAINT chk_monitored_api_consecutive_successes_nonneg
        CHECK (consecutive_successes >= 0);

CREATE TABLE check_result (
    id            BIGSERIAL    PRIMARY KEY,
    api_id        UUID         NOT NULL REFERENCES monitored_api (id),
    checked_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    success       BOOLEAN      NOT NULL,
    http_status   INTEGER      NULL,
    latency_ms    INTEGER      NULL,
    error_message TEXT         NULL
);

CREATE INDEX idx_check_result_api_checked_at ON check_result (api_id, checked_at DESC);
