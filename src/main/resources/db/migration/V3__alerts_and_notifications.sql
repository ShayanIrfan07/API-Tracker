-- Phase 3: alerting, notification audit, and owner email for outage handling

ALTER TABLE monitored_api
    ADD COLUMN owner_email VARCHAR(255) NULL;

CREATE TABLE alert (
    id             UUID         PRIMARY KEY,
    api_id         UUID         NOT NULL REFERENCES monitored_api (id),
    status         VARCHAR(16)  NOT NULL,
    opened_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    resolved_at    TIMESTAMPTZ  NULL,
    jira_issue_key VARCHAR(64)  NULL,
    summary        VARCHAR(500) NOT NULL,
    detail         TEXT         NULL,

    CONSTRAINT chk_alert_status CHECK (status IN ('OPEN', 'RESOLVED'))
);

CREATE UNIQUE INDEX uq_alert_one_open_per_api
    ON alert (api_id)
    WHERE status = 'OPEN';

CREATE INDEX idx_alert_status ON alert (status);
CREATE INDEX idx_alert_api_id ON alert (api_id);

CREATE TABLE notification_log (
    id            BIGSERIAL    PRIMARY KEY,
    alert_id      UUID         NOT NULL REFERENCES alert (id),
    channel       VARCHAR(32)  NOT NULL,
    recipient     VARCHAR(255) NULL,
    success       BOOLEAN      NOT NULL,
    error_message TEXT         NULL,
    sent_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_notification_channel CHECK (channel IN ('EMAIL'))
);

CREATE INDEX idx_notification_log_alert_id ON notification_log (alert_id);
