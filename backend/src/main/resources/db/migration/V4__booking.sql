-- =============================================================================
-- V4: Nhom 3 - Dat san (3 bang)
-- =============================================================================

-- 3.1 bookings ------------------------------------------------------------
CREATE TABLE bookings (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_code        varchar(50) NOT NULL UNIQUE,
    user_id             uuid NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    venue_id            uuid NOT NULL REFERENCES venues (id) ON DELETE RESTRICT,
    status              varchar(30) NOT NULL DEFAULT 'pending_payment',
    total_amount        numeric(12,2) NOT NULL DEFAULT 0,
    note                text,
    expires_at          timestamptz,
    confirmed_at        timestamptz,
    cancelled_at        timestamptz,
    cancellation_reason text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT bookings_status_check CHECK (
        status IN ('pending_payment', 'confirmed', 'cancelled', 'completed', 'expired', 'refunded')
    ),
    CONSTRAINT bookings_amount_check CHECK (total_amount >= 0)
);
CREATE INDEX bookings_user_idx ON bookings (user_id, created_at DESC);
CREATE INDEX bookings_venue_idx ON bookings (venue_id, created_at DESC);
CREATE INDEX bookings_status_idx ON bookings (status);
-- Job huy don qua han (2.1.42) quet theo cot nay.
CREATE INDEX bookings_expiring_idx ON bookings (expires_at) WHERE status = 'pending_payment';
CREATE TRIGGER bookings_set_updated_at BEFORE UPDATE ON bookings
    FOR EACH ROW EXECUTE FUNCTION courtly_set_updated_at();

COMMENT ON COLUMN bookings.booking_code IS 'Ma hien thi cho nguoi dung, vi du CT-20260923-0001';

-- 3.2 booking_items -------------------------------------------------------
CREATE TABLE booking_items (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id uuid NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    court_id   uuid NOT NULL REFERENCES courts (id) ON DELETE RESTRICT,
    start_time timestamptz NOT NULL,
    end_time   timestamptz NOT NULL,
    price      numeric(12,2) NOT NULL,
    status     varchar(30) NOT NULL DEFAULT 'pending',
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT booking_items_time_check CHECK (end_time > start_time),
    CONSTRAINT booking_items_price_check CHECK (price >= 0),
    CONSTRAINT booking_items_status_check CHECK (
        status IN ('pending', 'confirmed', 'cancelled', 'completed', 'expired')
    ),
    -- Chan dat trung mot san trong cung khung gio o muc database, khong chi check bang code.
    CONSTRAINT booking_items_no_overlap EXCLUDE USING gist (
        court_id WITH =,
        tstzrange(start_time, end_time, '[)') WITH &&
    ) WHERE (status IN ('pending', 'confirmed'))
);
CREATE INDEX booking_items_court_time_idx ON booking_items (court_id, start_time, end_time);
CREATE INDEX booking_items_booking_idx ON booking_items (booking_id);

COMMENT ON CONSTRAINT booking_items_no_overlap ON booking_items IS
    'Hai booking_items pending/confirmed khong duoc trung gio tren cung mot san';

-- 3.3 booking_status_history ----------------------------------------------
CREATE TABLE booking_status_history (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id uuid NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    old_status varchar(30),
    new_status varchar(30) NOT NULL,
    reason     text,
    changed_by uuid REFERENCES users (id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX bsh_booking_idx ON booking_status_history (booking_id, created_at);

COMMENT ON COLUMN booking_status_history.changed_by IS 'NULL khi trang thai do he thong tu dong cap nhat';
