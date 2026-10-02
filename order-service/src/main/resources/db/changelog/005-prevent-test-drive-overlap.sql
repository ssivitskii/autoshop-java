CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE test_drive_requests
    ADD CONSTRAINT test_drive_no_overlapping_active
    EXCLUDE USING gist
    (
        (car_id::uuid) WITH =,
        tsrange(requested_date_time,
                requested_date_time + INTERVAL '1 hour',
                '[)') WITH &&
    )
    WHERE (removed = FALSE AND status IN ('PENDING', 'APPROVED'));
