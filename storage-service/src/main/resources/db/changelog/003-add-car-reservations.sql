ALTER TABLE cars
    ADD COLUMN reserved_by_order_id UUID;

ALTER TABLE cars
    ADD CONSTRAINT chk_reserved_car_unavailable
        CHECK (reserved_by_order_id IS NULL OR available = FALSE);

CREATE UNIQUE INDEX uq_cars_reserved_by_order_id
    ON cars (reserved_by_order_id)
    WHERE reserved_by_order_id IS NOT NULL;
