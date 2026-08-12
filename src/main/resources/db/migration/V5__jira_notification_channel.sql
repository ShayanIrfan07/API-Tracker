-- Phase 8: allow Jira notification audit entries alongside email

ALTER TABLE notification_log
    DROP CONSTRAINT IF EXISTS chk_notification_channel;

ALTER TABLE notification_log
    ADD CONSTRAINT chk_notification_channel
        CHECK (channel IN ('EMAIL', 'JIRA'));
