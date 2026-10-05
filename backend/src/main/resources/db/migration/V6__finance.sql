-- =============================================================================
-- V6: Nhom 5 - Doanh thu, phi nen tang, rut tien (4 bang)
-- =============================================================================

-- 5.1 platform_fee_configs ------------------------------------------------
CREATE TABLE platform_fee_configs (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    fee_type       varchar(30) NOT NULL,
    fee_value      numeric(12,2) NOT NULL,
    effective_from timestamptz NOT NULL DEFAULT now(),
    effective_to   timestamptz,
    status         varchar(30) NOT NULL DEFAULT 'active',
    created_by     uuid REFERENCES users (id) ON DELETE SET NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pfc_type_check CHECK (fee_type IN ('percentage', 'fixed')),
    CONSTRAINT pfc_status_check CHECK (status IN ('active', 'inactive')),
    CONSTRAINT pfc_value_check CHECK (fee_value >= 0),
    CONSTRAINT pfc_percentage_check CHECK (fee_type <> 'percentage' OR fee_value <= 100),
    CONSTRAINT pfc_effective_check CHECK (effective_to IS NULL OR effective_to > effective_from)
);
-- Chi mot cau hinh phi dang active tai mot thoi diem.
CREATE UNIQUE INDEX pfc_single_active ON platform_fee_configs ((true)) WHERE status = 'active';

COMMENT ON COLUMN platform_fee_configs.fee_value IS 'percentage: phan tram 0-100. fixed: so tien VND tren moi booking';

-- 5.2 owner_balance_transactions ------------------------------------------
CREATE TABLE owner_balance_transactions (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id      uuid NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    booking_id    uuid REFERENCES bookings (id) ON DELETE SET NULL,
    payment_id    uuid REFERENCES payments (id) ON DELETE SET NULL,
    type          varchar(30) NOT NULL,
    amount        numeric(12,2) NOT NULL,
    balance_after numeric(12,2) NOT NULL,
    note          text,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT obt_type_check CHECK (
        type IN ('earning', 'platform_fee', 'withdrawal', 'refund_adjustment', 'manual_adjustment')
    )
);
CREATE INDEX obt_owner_idx ON owner_balance_transactions (owner_id, created_at DESC);
CREATE INDEX obt_booking_idx ON owner_balance_transactions (booking_id);

COMMENT ON TABLE owner_balance_transactions IS
    'So cai so du chu san. So du hien tai = balance_after cua ban ghi moi nhat, khong luu rieng mot cot tong';
COMMENT ON COLUMN owner_balance_transactions.amount IS 'Duong = cong tien, am = tru tien (phi nen tang, rut tien)';

-- 5.3 withdrawal_requests -------------------------------------------------
CREATE TABLE withdrawal_requests (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id          uuid NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    amount            numeric(12,2) NOT NULL,
    bank_name         varchar(100) NOT NULL,
    bank_account_no   varchar(50) NOT NULL,
    bank_account_name varchar(150) NOT NULL,
    status            varchar(30) NOT NULL DEFAULT 'pending',
    requested_at      timestamptz NOT NULL DEFAULT now(),
    reviewed_by       uuid REFERENCES users (id) ON DELETE SET NULL,
    reviewed_at       timestamptz,
    rejection_reason  text,
    CONSTRAINT wr_status_check CHECK (status IN ('pending', 'approved', 'rejected', 'paid', 'cancelled')),
    CONSTRAINT wr_amount_check CHECK (amount > 0)
);
CREATE INDEX wr_owner_idx ON withdrawal_requests (owner_id, requested_at DESC);
CREATE INDEX wr_status_idx ON withdrawal_requests (status, requested_at DESC);

-- 5.4 payout_transactions -------------------------------------------------
CREATE TABLE payout_transactions (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    withdrawal_request_id   uuid NOT NULL REFERENCES withdrawal_requests (id) ON DELETE RESTRICT,
    owner_id                uuid NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    amount                  numeric(12,2) NOT NULL,
    provider                varchar(50),
    provider_transaction_id varchar(255),
    status                  varchar(30) NOT NULL DEFAULT 'pending',
    paid_at                 timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pt_status_check CHECK (status IN ('pending', 'success', 'failed')),
    CONSTRAINT pt_amount_check CHECK (amount > 0)
);
CREATE INDEX pt_request_idx ON payout_transactions (withdrawal_request_id);
CREATE INDEX pt_owner_idx ON payout_transactions (owner_id, created_at DESC);
