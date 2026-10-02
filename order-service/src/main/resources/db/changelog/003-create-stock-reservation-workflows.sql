CREATE TABLE stock_reservation_workflows
(
    order_id           UUID PRIMARY KEY,
    car_id             VARCHAR(255)             NOT NULL,
    state              VARCHAR(32)              NOT NULL,
    storage_expires_at TIMESTAMP WITH TIME ZONE,
    next_attempt_at    TIMESTAMP WITH TIME ZONE,
    attempt_count      INT                      NOT NULL DEFAULT 0,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_reservation_order FOREIGN KEY (order_id) REFERENCES stock_orders (id),
    CONSTRAINT chk_stock_reservation_workflow_state CHECK
        (state IN ('HELD', 'LEGACY_UNVERIFIED', 'CONFIRM_PENDING', 'CONFIRMED', 'RELEASE_PENDING', 'RELEASED', 'EXPIRED')),
    CONSTRAINT chk_stock_held_expiry CHECK (state <> 'HELD' OR storage_expires_at IS NOT NULL)
);

-- Existing paid/fulfilled orders predate leases and remain confirmed. Unpaid
-- orders must verify their storage ledger before payment; cancelled orders are
-- queued for owner-checked release. No legacy row receives retroactive TTL.
INSERT INTO stock_reservation_workflows(order_id, car_id, state, next_attempt_at)
SELECT id,
       car_id,
       CASE
           WHEN status = 'CANCELLED' THEN 'RELEASE_PENDING'
           WHEN status IN ('CREATED', 'APPROVED_BY_MANAGER', 'AWAITING_PAYMENT') THEN 'LEGACY_UNVERIFIED'
           ELSE 'CONFIRMED'
       END,
       CASE WHEN status = 'CANCELLED' THEN CURRENT_TIMESTAMP ELSE NULL END
FROM stock_orders
WHERE removed = FALSE
ON CONFLICT (order_id) DO NOTHING;

CREATE INDEX idx_stock_reservation_pending
    ON stock_reservation_workflows(next_attempt_at, updated_at, order_id)
    WHERE state IN ('CONFIRM_PENDING', 'RELEASE_PENDING');

CREATE INDEX idx_stock_reservation_local_expiry
    ON stock_reservation_workflows(storage_expires_at, order_id)
    WHERE state = 'HELD';
