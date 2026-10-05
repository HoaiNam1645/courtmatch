-- =============================================================================
-- V5: Nhom 4 - Thanh toan SePay, hoan tien (3 bang)
-- =============================================================================

-- 4.1 payments ------------------------------------------------------------
CREATE TABLE payments (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id              uuid NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    provider                varchar(50) NOT NULL DEFAULT 'sepay',
    amount                  numeric(12,2) NOT NULL,
    status                  varchar(30) NOT NULL DEFAULT 'pending',
    qr_url                  text,
    bank_account_no         varchar(50),
    transfer_content        varchar(255),
    provider_transaction_id varchar(255),
    paid_at                 timestamptz,
    expired_at              timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT payments_status_check CHECK (status IN ('pending', 'paid', 'failed', 'expired', 'refunded')),
    CONSTRAINT payments_amount_check CHECK (amount >= 0)
);
-- Chan xu ly webhook trung cho cung mot giao dich.
CREATE UNIQUE INDEX payments_provider_txn_unique
    ON payments (provider, provider_transaction_id)
    WHERE provider_transaction_id IS NOT NULL;
CREATE INDEX payments_booking_idx ON payments (booking_id);
CREATE INDEX payments_status_idx ON payments (status);
CREATE INDEX payments_transfer_content_idx ON payments (transfer_content);
CREATE TRIGGER payments_set_updated_at BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION courtly_set_updated_at();

COMMENT ON COLUMN payments.transfer_content IS 'Noi dung chuyen khoan dung de doi soat voi webhook SePay (2.1.38)';

-- 4.2 sepay_webhook_logs --------------------------------------------------
CREATE TABLE sepay_webhook_logs (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id       uuid REFERENCES payments (id) ON DELETE SET NULL,
    transaction_code varchar(255) NOT NULL UNIQUE,
    payload_json     jsonb NOT NULL,
    processed_status varchar(30) NOT NULL DEFAULT 'received',
    error_message    text,
    received_at      timestamptz NOT NULL DEFAULT now(),
    processed_at     timestamptz,
    CONSTRAINT swl_status_check CHECK (processed_status IN ('received', 'processed', 'ignored', 'failed'))
);
CREATE INDEX swl_payment_idx ON sepay_webhook_logs (payment_id);
CREATE INDEX swl_status_idx ON sepay_webhook_logs (processed_status, received_at DESC);

COMMENT ON COLUMN sepay_webhook_logs.transaction_code IS 'Unique de chong xu ly trung khi SePay gui lai webhook (2.1.37)';

-- 4.3 refunds -------------------------------------------------------------
CREATE TABLE refunds (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id   uuid NOT NULL REFERENCES payments (id) ON DELETE RESTRICT,
    booking_id   uuid NOT NULL REFERENCES bookings (id) ON DELETE RESTRICT,
    amount       numeric(12,2) NOT NULL,
    reason       text,
    status       varchar(30) NOT NULL DEFAULT 'requested',
    requested_by uuid REFERENCES users (id) ON DELETE SET NULL,
    processed_by uuid REFERENCES users (id) ON DELETE SET NULL,
    processed_at timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT refunds_status_check CHECK (status IN ('requested', 'approved', 'rejected', 'processed', 'failed')),
    CONSTRAINT refunds_amount_check CHECK (amount > 0)
);
CREATE INDEX refunds_booking_idx ON refunds (booking_id);
CREATE INDEX refunds_payment_idx ON refunds (payment_id);
CREATE INDEX refunds_status_idx ON refunds (status);
