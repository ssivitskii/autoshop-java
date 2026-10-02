CREATE TABLE car_reservations
(
    order_id       UUID PRIMARY KEY,
    car_id         UUID                     NOT NULL,
    state          VARCHAR(32)              NOT NULL,
    hold_expires_at TIMESTAMP WITH TIME ZONE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_car_reservation_state CHECK (state IN ('HELD', 'CONFIRMED', 'RELEASED', 'EXPIRED')),
    CONSTRAINT chk_held_reservation_expiry CHECK (state <> 'HELD' OR hold_expires_at IS NOT NULL)
);

INSERT INTO car_reservations (order_id, car_id, state, hold_expires_at)
SELECT reserved_by_order_id, id, 'CONFIRMED', NULL
FROM cars
WHERE reserved_by_order_id IS NOT NULL
ON CONFLICT (order_id) DO NOTHING;

CREATE INDEX idx_car_reservations_expiry
    ON car_reservations (hold_expires_at, order_id)
    WHERE state = 'HELD';
