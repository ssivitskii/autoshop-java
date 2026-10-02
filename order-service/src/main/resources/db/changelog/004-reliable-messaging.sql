ALTER TABLE outbox_events
    ADD COLUMN claim_token UUID,
    ADD COLUMN claim_until TIMESTAMP WITH TIME ZONE,
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    ADD COLUMN sent_at TIMESTAMP WITH TIME ZONE;

DROP INDEX idx_outbox_unsent;
CREATE INDEX idx_outbox_publishable
    ON outbox_events (next_attempt_at, created_at, id)
    WHERE sent = FALSE;

CREATE TABLE inbox_events
(
    event_id     UUID PRIMARY KEY         NOT NULL,
    topic        VARCHAR(255)             NOT NULL,
    partition_id INTEGER                  NOT NULL,
    source_offset BIGINT                  NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
