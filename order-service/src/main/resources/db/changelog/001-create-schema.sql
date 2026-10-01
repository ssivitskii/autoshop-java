CREATE TABLE users
(
    id         UUID PRIMARY KEY         NOT NULL,
    username   VARCHAR(255)             NOT NULL,
    password   VARCHAR(255)             NOT NULL,
    full_name  VARCHAR(255)             NOT NULL,
    email      VARCHAR(255)             NOT NULL,
    phone      VARCHAR(50),
    role       VARCHAR(50)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    removed    BOOLEAN                  NOT NULL DEFAULT FALSE
);

CREATE TABLE stock_orders
(
    id         UUID PRIMARY KEY         NOT NULL,
    client_id  VARCHAR(255)             NOT NULL,
    manager_id VARCHAR(255)             NOT NULL,
    car_id     VARCHAR(255)             NOT NULL,
    status     VARCHAR(50)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    removed    BOOLEAN                  NOT NULL DEFAULT FALSE
);

CREATE TABLE custom_orders
(
    id           UUID PRIMARY KEY         NOT NULL,
    manager_id   VARCHAR(255)             NOT NULL,
    client_id    VARCHAR(255)             NOT NULL,
    car_model_id VARCHAR(255)             NOT NULL,
    total_price  DECIMAL(15, 2)           NOT NULL,
    status       VARCHAR(50)              NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    removed      BOOLEAN                  NOT NULL DEFAULT FALSE
);

CREATE TABLE custom_order_variants
(
    custom_order_id UUID         NOT NULL,
    category_id     VARCHAR(255) NOT NULL,
    variant_id      VARCHAR(255) NOT NULL,
    CONSTRAINT fk_custom_order_variants_order FOREIGN KEY (custom_order_id) REFERENCES custom_orders (id)
);

CREATE TABLE test_drive_requests
(
    id                  UUID PRIMARY KEY         NOT NULL,
    client_id           VARCHAR(255)             NOT NULL,
    car_id              VARCHAR(255)             NOT NULL,
    requested_date_time TIMESTAMP                NOT NULL,
    status              VARCHAR(50)              NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    removed             BOOLEAN                  NOT NULL DEFAULT FALSE
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
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_outbox_unsent ON outbox_events (sent, created_at) WHERE sent = FALSE;
