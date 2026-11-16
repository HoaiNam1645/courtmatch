-- =============================================================================
-- V37: Danh dau khoan Courtly no chu san (courtly_owed) da duoc bu tru / thanh toan
-- =============================================================================
-- courtly_owed_cleared_at NULL = Courtly con no chu san khoan nay; co gia tri = da bu tru hoac da tra.
ALTER TABLE booking_commissions
    ADD COLUMN courtly_owed_cleared_at timestamptz;

CREATE INDEX bc_courtly_owed_idx ON booking_commissions (owner_id)
    WHERE courtly_owed > 0 AND courtly_owed_cleared_at IS NULL;
