-- Phase 1 foundation schema for API monitoring

CREATE TABLE monitored_api (
    id                   UUID         PRIMARY KEY,
    name                 VARCHAR(100) NOT NULL,
    base_url             VARCHAR(500) NOT NULL,
    path                 VARCHAR(500) NOT NULL DEFAULT '/',
    http_method          VARCHAR(16)  NOT NULL,
    expected_status_code INTEGER      NOT NULL,
    timeout_ms           INTEGER      NOT NULL,
    interval_seconds     INTEGER      NOT NULL,
    enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
    current_status       VARCHAR(16)  NOT NULL DEFAULT 'UNKNOWN',
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_monitored_api_name UNIQUE (name),
    CONSTRAINT chk_monitored_api_http_method
        CHECK (http_method IN ('GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS')),
    CONSTRAINT chk_monitored_api_expected_status
        CHECK (expected_status_code BETWEEN 100 AND 599),
    CONSTRAINT chk_monitored_api_timeout_positive
        CHECK (timeout_ms > 0),
    CONSTRAINT chk_monitored_api_interval_positive
        CHECK (interval_seconds > 0),
    CONSTRAINT chk_monitored_api_status
        CHECK (current_status IN ('UNKNOWN', 'UP', 'DOWN', 'DEGRADED'))
);

CREATE INDEX idx_monitored_api_enabled ON monitored_api (enabled);
CREATE INDEX idx_monitored_api_current_status ON monitored_api (current_status);
