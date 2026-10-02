ALTER TABLE assembly_orders
    ADD CONSTRAINT uq_assembly_source_order UNIQUE (source_order_id);

CREATE TABLE inbox_events
(
    event_id      UUID PRIMARY KEY         NOT NULL,
    topic         VARCHAR(255)             NOT NULL,
    partition_id  INTEGER                  NOT NULL,
    source_offset BIGINT                   NOT NULL,
    processed_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE outbox_events
(
    id             UUID PRIMARY KEY         NOT NULL,
    aggregate_type VARCHAR(100)             NOT NULL,
    aggregate_id   VARCHAR(255)             NOT NULL,
    event_type     VARCHAR(100)             NOT NULL,
    payload        TEXT                     NOT NULL,
    trace_id       VARCHAR(255)             NOT NULL,
    sent           BOOLEAN                  NOT NULL DEFAULT FALSE,
    claim_token    UUID,
    claim_until    TIMESTAMP WITH TIME ZONE,
    attempt_count  INTEGER                  NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    sent_at        TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_outbox_assembly_response UNIQUE (aggregate_id)
);

CREATE INDEX idx_outbox_publishable
    ON outbox_events (next_attempt_at, created_at, id)
    WHERE sent = FALSE;
