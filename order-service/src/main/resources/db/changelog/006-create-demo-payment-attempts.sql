CREATE TABLE demo_payment_attempts
(
    id                UUID PRIMARY KEY         NOT NULL,
    stock_order_id    UUID,
    custom_order_id   UUID,
    client_id         VARCHAR(255)             NOT NULL,
    idempotency_key   UUID                     NOT NULL,
    requested_outcome VARCHAR(16)              NOT NULL,
    status            VARCHAR(16)              NOT NULL,
    failure_code      VARCHAR(64),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_demo_payment_stock_order FOREIGN KEY (stock_order_id) REFERENCES stock_orders (id),
    CONSTRAINT fk_demo_payment_custom_order FOREIGN KEY (custom_order_id) REFERENCES custom_orders (id),
    CONSTRAINT chk_demo_payment_exactly_one_order CHECK
        ((stock_order_id IS NOT NULL) <> (custom_order_id IS NOT NULL)),
    CONSTRAINT chk_demo_payment_outcome CHECK
        (requested_outcome IN ('SUCCESS', 'DECLINE')),
    CONSTRAINT chk_demo_payment_status CHECK
        (status IN ('PENDING', 'SUCCEEDED', 'DECLINED', 'FAILED'))
);

CREATE UNIQUE INDEX uq_demo_payment_stock_key
    ON demo_payment_attempts(stock_order_id, idempotency_key)
    WHERE stock_order_id IS NOT NULL;

CREATE UNIQUE INDEX uq_demo_payment_custom_key
    ON demo_payment_attempts(custom_order_id, idempotency_key)
    WHERE custom_order_id IS NOT NULL;

CREATE UNIQUE INDEX uq_demo_payment_stock_active
    ON demo_payment_attempts(stock_order_id)
    WHERE stock_order_id IS NOT NULL AND status IN ('PENDING', 'SUCCEEDED');

CREATE UNIQUE INDEX uq_demo_payment_custom_active
    ON demo_payment_attempts(custom_order_id)
    WHERE custom_order_id IS NOT NULL AND status IN ('PENDING', 'SUCCEEDED');
