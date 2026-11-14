-- =============================================================================
-- V27: Chu san bao da thanh toan hoa hong cho Courtly (2.3.24 - 2.3.26)
-- =============================================================================

CREATE TABLE commission_payment_reports (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id       uuid NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    settlement_id  uuid REFERENCES commission_settlements (id) ON DELETE SET NULL,
    amount         numeric(12,2) NOT NULL,
    paid_on        date NOT NULL,
    reference_code varchar(100) NOT NULL,
    proof_url      text,
    note           text,
    status         varchar(30) NOT NULL DEFAULT 'pending_review',
    reviewed_by    uuid REFERENCES users (id) ON DELETE SET NULL,
    reviewed_at    timestamptz,
    review_note    text,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT cpr_amount_check CHECK (amount > 0),
    CONSTRAINT cpr_status_check CHECK (status IN ('pending_review', 'confirmed', 'rejected'))
);
CREATE INDEX cpr_owner_created_idx ON commission_payment_reports (owner_id, created_at DESC);
CREATE INDEX cpr_owner_status_idx ON commission_payment_reports (owner_id, status);
CREATE INDEX cpr_settlement_idx ON commission_payment_reports (settlement_id);
-- Mot ma tham chieu chi duoc dung mot lan cho cac bao cao chua bi tu choi.
CREATE UNIQUE INDEX cpr_reference_unique ON commission_payment_reports (owner_id, lower(reference_code))
    WHERE status <> 'rejected';
CREATE TRIGGER commission_payment_reports_set_updated_at BEFORE UPDATE ON commission_payment_reports
    FOR EACH ROW EXECUTE FUNCTION courtly_set_updated_at();

COMMENT ON TABLE commission_payment_reports IS
    'Chu san bao da chuyen khoan hoa hong cho Courtly; Courtly xac nhan hoac tu choi';
