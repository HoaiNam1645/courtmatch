-- =============================================================================
-- V15: Direct owner payment, owner booking approval, commission reconciliation
-- =============================================================================

-- Existing pending_payment rows already passed owner review in the legacy flow.
ALTER TABLE bookings DROP CONSTRAINT bookings_status_check;
ALTER TABLE bookings RENAME COLUMN expires_at TO payment_expires_at;
ALTER TABLE bookings ALTER COLUMN status TYPE varchar(40);
ALTER TABLE bookings
    ADD COLUMN price_locked_at timestamptz,
    ADD COLUMN owner_response_expires_at timestamptz,
    ADD COLUMN owner_responded_at timestamptz,
    ADD COLUMN owner_responded_by uuid REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN completed_at timestamptz,
    ADD COLUMN rejected_at timestamptz,
    ADD COLUMN rejection_reason text;
UPDATE bookings
SET status = 'awaiting_payment',
    price_locked_at = COALESCE(created_at, now()),
    owner_responded_at = COALESCE(created_at, now())
WHERE status = 'pending_payment';
UPDATE bookings SET price_locked_at = COALESCE(price_locked_at, created_at, now());
ALTER TABLE bookings ALTER COLUMN status SET DEFAULT 'pending_owner_confirmation';
ALTER TABLE bookings ALTER COLUMN price_locked_at SET NOT NULL;
ALTER TABLE bookings ADD CONSTRAINT bookings_status_check CHECK (
    status IN ('pending_owner_confirmation', 'awaiting_payment', 'confirmed', 'rejected',
               'cancelled', 'completed', 'expired', 'disputed', 'refunded')
);
DROP INDEX IF EXISTS bookings_expiring_idx;
CREATE INDEX bookings_owner_review_expiring_idx ON bookings (owner_response_expires_at)
    WHERE status = 'pending_owner_confirmation';
CREATE INDEX bookings_payment_expiring_idx ON bookings (payment_expires_at)
    WHERE status = 'awaiting_payment';

ALTER TABLE booking_status_history
    ADD COLUMN actor_role varchar(30);
ALTER TABLE booking_status_history ALTER COLUMN old_status TYPE varchar(40);
ALTER TABLE booking_status_history ALTER COLUMN new_status TYPE varchar(40);

-- Consolidate legacy demo owners into the oldest OWNER account.
WITH canonical_owner AS (
    SELECT ur.user_id
    FROM user_roles ur
    JOIN roles r ON r.id = ur.role_id
    JOIN users u ON u.id = ur.user_id
    WHERE r.code = 'owner'
    ORDER BY u.created_at, u.id
    LIMIT 1
)
UPDATE venues
SET owner_id = (SELECT user_id FROM canonical_owner)
WHERE EXISTS (SELECT 1 FROM canonical_owner)
  AND owner_id <> (SELECT user_id FROM canonical_owner);

WITH canonical_owner AS (
    SELECT ur.user_id
    FROM user_roles ur
    JOIN roles r ON r.id = ur.role_id
    JOIN users u ON u.id = ur.user_id
    WHERE r.code = 'owner'
    ORDER BY u.created_at, u.id
    LIMIT 1
)
DELETE FROM court_owner_profiles
WHERE EXISTS (SELECT 1 FROM canonical_owner)
  AND user_id <> (SELECT user_id FROM canonical_owner);

WITH canonical_owner AS (
    SELECT ur.user_id
    FROM user_roles ur
    JOIN roles r ON r.id = ur.role_id
    JOIN users u ON u.id = ur.user_id
    WHERE r.code = 'owner'
    ORDER BY u.created_at, u.id
    LIMIT 1
)
DELETE FROM user_roles ur
USING roles r
WHERE ur.role_id = r.id
  AND r.code = 'owner'
  AND EXISTS (SELECT 1 FROM canonical_owner)
  AND ur.user_id <> (SELECT user_id FROM canonical_owner);

-- Payments now record money transferred directly from player to venue owner.
DROP INDEX IF EXISTS payments_provider_txn_unique;
DROP INDEX IF EXISTS payments_transfer_content_idx;
ALTER TABLE payments DROP CONSTRAINT payments_status_check;
ALTER TABLE payments RENAME COLUMN transfer_content TO reference_content;
ALTER TABLE payments RENAME COLUMN paid_at TO confirmed_at;
ALTER TABLE payments ALTER COLUMN status TYPE varchar(40);
ALTER TABLE payments
    ADD COLUMN recipient_owner_id uuid REFERENCES users (id) ON DELETE RESTRICT,
    ADD COLUMN method varchar(30),
    ADD COLUMN bank_name varchar(100),
    ADD COLUMN bank_account_name varchar(150),
    ADD COLUMN proof_url text,
    ADD COLUMN payer_reported_at timestamptz,
    ADD COLUMN confirmed_by uuid REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN rejected_by uuid REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN rejected_at timestamptz,
    ADD COLUMN rejection_reason text;
UPDATE payments p
SET recipient_owner_id = v.owner_id,
    method = 'owner_bank_transfer',
    bank_name = cop.bank_name,
    bank_account_name = cop.bank_account_name,
    status = CASE p.status
        WHEN 'pending' THEN 'awaiting_payment'
        WHEN 'paid' THEN 'confirmed'
        WHEN 'failed' THEN 'rejected'
        ELSE p.status
    END
FROM bookings b
JOIN venues v ON v.id = b.venue_id
LEFT JOIN court_owner_profiles cop ON cop.user_id = v.owner_id
WHERE p.booking_id = b.id;
ALTER TABLE payments ALTER COLUMN recipient_owner_id SET NOT NULL;
ALTER TABLE payments ALTER COLUMN method SET NOT NULL;
ALTER TABLE payments ALTER COLUMN status SET DEFAULT 'awaiting_payment';
ALTER TABLE payments ADD CONSTRAINT payments_method_check
    CHECK (method IN ('cash', 'owner_bank_transfer'));
ALTER TABLE payments ADD CONSTRAINT payments_status_check CHECK (
    status IN ('awaiting_payment', 'payer_reported', 'confirmed', 'rejected', 'expired',
               'disputed', 'partially_refunded', 'refunded')
);
ALTER TABLE payments
    DROP COLUMN provider,
    DROP COLUMN qr_url,
    DROP COLUMN provider_transaction_id;
CREATE INDEX payments_owner_status_idx ON payments (recipient_owner_id, status, created_at DESC);

DROP TABLE sepay_webhook_logs;

CREATE TABLE payment_status_history (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id  uuid NOT NULL REFERENCES payments (id) ON DELETE CASCADE,
    old_status  varchar(40),
    new_status  varchar(40) NOT NULL,
    reason      text,
    changed_by  uuid REFERENCES users (id) ON DELETE SET NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX psh_payment_idx ON payment_status_history (payment_id, created_at);

-- Courtly no longer keeps an owner balance or pays withdrawals.
DROP TABLE payout_transactions;
DROP TABLE withdrawal_requests;
DROP TABLE owner_balance_transactions;

ALTER TABLE platform_fee_configs ADD COLUMN name varchar(150);
UPDATE platform_fee_configs SET name = 'Chinh sach hoa hong' WHERE name IS NULL;
ALTER TABLE platform_fee_configs ALTER COLUMN name SET NOT NULL;
INSERT INTO platform_fee_configs
    (id, name, fee_type, fee_value, effective_from, status, created_at)
SELECT gen_random_uuid(), 'Hoa hong mac dinh', 'percentage', 10, now(), 'active', now()
WHERE NOT EXISTS (SELECT 1 FROM platform_fee_configs WHERE status = 'active');

CREATE TABLE commission_settlements (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id            uuid NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    period_from         date NOT NULL,
    period_to           date NOT NULL,
    opening_outstanding numeric(12,2) NOT NULL DEFAULT 0,
    commission_total    numeric(12,2) NOT NULL DEFAULT 0,
    adjustment_total    numeric(12,2) NOT NULL DEFAULT 0,
    amount_due          numeric(12,2) NOT NULL DEFAULT 0,
    amount_paid         numeric(12,2) NOT NULL DEFAULT 0,
    status              varchar(30) NOT NULL DEFAULT 'open',
    owner_submitted_at  timestamptz,
    payment_reference   varchar(255),
    proof_url           text,
    reviewed_by         uuid REFERENCES users (id) ON DELETE SET NULL,
    reviewed_at         timestamptz,
    paid_at             timestamptz,
    note                text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT cs_period_check CHECK (period_to >= period_from),
    CONSTRAINT cs_status_check CHECK (
        status IN ('open', 'pending_review', 'partial', 'paid', 'overdue', 'disputed')
    ),
    UNIQUE (owner_id, period_from, period_to)
);
CREATE INDEX cs_owner_status_idx ON commission_settlements (owner_id, status, period_to DESC);
CREATE TRIGGER commission_settlements_set_updated_at BEFORE UPDATE ON commission_settlements
    FOR EACH ROW EXECUTE FUNCTION courtly_set_updated_at();

CREATE TABLE booking_commissions (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id        uuid NOT NULL UNIQUE REFERENCES bookings (id) ON DELETE RESTRICT,
    owner_id          uuid NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    fee_config_id     uuid REFERENCES platform_fee_configs (id) ON DELETE SET NULL,
    settlement_id     uuid REFERENCES commission_settlements (id) ON DELETE SET NULL,
    gross_amount      numeric(12,2) NOT NULL,
    fee_type          varchar(30) NOT NULL,
    fee_value         numeric(12,2) NOT NULL,
    commission_amount numeric(12,2) NOT NULL,
    status            varchar(30) NOT NULL DEFAULT 'pending',
    calculated_at     timestamptz NOT NULL DEFAULT now(),
    due_at            timestamptz NOT NULL,
    settled_at        timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT bc_fee_type_check CHECK (fee_type IN ('percentage', 'fixed')),
    CONSTRAINT bc_status_check CHECK (
        status IN ('pending', 'due', 'suspended', 'adjusted', 'waived', 'settled')
    ),
    CONSTRAINT bc_amount_check CHECK (gross_amount >= 0 AND commission_amount >= 0)
);
CREATE INDEX bc_owner_status_due_idx ON booking_commissions (owner_id, status, due_at);
CREATE INDEX bc_settlement_idx ON booking_commissions (settlement_id);
CREATE TRIGGER booking_commissions_set_updated_at BEFORE UPDATE ON booking_commissions
    FOR EACH ROW EXECUTE FUNCTION courtly_set_updated_at();

CREATE TABLE booking_disputes (
    id                        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id                uuid NOT NULL REFERENCES bookings (id) ON DELETE RESTRICT,
    payment_id                uuid REFERENCES payments (id) ON DELETE SET NULL,
    reporter_id               uuid NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    category                  varchar(50) NOT NULL,
    description               text NOT NULL,
    reporter_evidences_json   jsonb,
    refund_requested          boolean NOT NULL DEFAULT false,
    requested_refund_amount   numeric(12,2),
    status                    varchar(30) NOT NULL DEFAULT 'open',
    owner_response_deadline   timestamptz,
    owner_response            text,
    owner_responded_by        uuid REFERENCES users (id) ON DELETE SET NULL,
    owner_responded_at        timestamptz,
    owner_evidences_json      jsonb,
    resolution                varchar(30),
    resolution_note           text,
    resolution_evidences_json jsonb,
    handled_by                uuid REFERENCES users (id) ON DELETE SET NULL,
    handled_at                timestamptz,
    created_at                timestamptz NOT NULL DEFAULT now(),
    updated_at                timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT bd_status_check CHECK (
        status IN ('open', 'awaiting_owner_response', 'reviewing', 'resolved', 'rejected', 'cancelled')
    )
);
CREATE INDEX bd_booking_idx ON booking_disputes (booking_id);
CREATE INDEX bd_status_idx ON booking_disputes (status, created_at DESC);
CREATE INDEX bd_owner_deadline_idx ON booking_disputes (owner_response_deadline)
    WHERE status = 'awaiting_owner_response';
CREATE TRIGGER booking_disputes_set_updated_at BEFORE UPDATE ON booking_disputes
    FOR EACH ROW EXECUTE FUNCTION courtly_set_updated_at();

-- Refunds are performed by the owner and only tracked/confirmed by Courtly.
ALTER TABLE refunds DROP CONSTRAINT refunds_status_check;
ALTER TABLE refunds ALTER COLUMN status TYPE varchar(40);
UPDATE refunds SET status = 'confirmed' WHERE status = 'processed';
ALTER TABLE refunds
    ADD COLUMN dispute_id uuid REFERENCES booking_disputes (id) ON DELETE SET NULL,
    ADD COLUMN approved_by uuid REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN owner_reported_at timestamptz,
    ADD COLUMN proof_url text,
    ADD COLUMN confirmed_by uuid REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN confirmed_at timestamptz,
    ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE refunds
    DROP COLUMN processed_by,
    DROP COLUMN processed_at;
ALTER TABLE refunds ADD CONSTRAINT refunds_status_check CHECK (
    status IN ('requested', 'approved', 'awaiting_owner_refund', 'owner_reported_refunded',
               'confirmed', 'rejected', 'failed')
);
CREATE TRIGGER refunds_set_updated_at BEFORE UPDATE ON refunds
    FOR EACH ROW EXECUTE FUNCTION courtly_set_updated_at();
