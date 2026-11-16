-- B4: voucher giam gia theo phan tram cho nguoi choi. Courtly chiu phan giam gia:
-- chu san van nhan du gia goc, hoa hong tinh tren gia goc, nguoi choi chi tra phan sau giam.
CREATE TABLE vouchers (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code                varchar(40)  NOT NULL,
    name                varchar(150) NOT NULL,
    description         text,
    discount_percent    numeric(5,2) NOT NULL,
    max_discount_amount numeric(12,2),
    valid_from          timestamptz  NOT NULL,
    valid_until         timestamptz  NOT NULL,
    total_limit         integer,
    min_elo             numeric(8,2),
    min_booking_matches integer,
    min_active_weeks    integer,
    status              varchar(20)  NOT NULL DEFAULT 'active',
    created_by          uuid REFERENCES users (id) ON DELETE SET NULL,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT vouchers_percent_check CHECK (discount_percent > 0 AND discount_percent <= 100),
    CONSTRAINT vouchers_cap_check CHECK (max_discount_amount IS NULL OR max_discount_amount > 0),
    CONSTRAINT vouchers_validity_check CHECK (valid_until > valid_from),
    CONSTRAINT vouchers_limit_check CHECK (total_limit IS NULL OR total_limit > 0),
    CONSTRAINT vouchers_min_elo_check CHECK (min_elo IS NULL OR min_elo >= 0),
    CONSTRAINT vouchers_min_matches_check CHECK (min_booking_matches IS NULL OR min_booking_matches > 0),
    CONSTRAINT vouchers_min_weeks_check CHECK (min_active_weeks IS NULL OR min_active_weeks > 0),
    CONSTRAINT vouchers_status_check CHECK (status IN ('active', 'locked'))
);
CREATE UNIQUE INDEX vouchers_code_unique ON vouchers (upper(code));
CREATE TRIGGER vouchers_set_updated_at BEFORE UPDATE ON vouchers
    FOR EACH ROW EXECUTE FUNCTION courtly_set_updated_at();

-- Moi luot dung gan voi mot booking. 'applied' = dang giu / da dung, 'released' = booking bi huy, tu choi hoac het han nen tra lai.
CREATE TABLE voucher_redemptions (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    voucher_id      uuid NOT NULL REFERENCES vouchers (id) ON DELETE RESTRICT,
    user_id         uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    booking_id      uuid NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    original_amount numeric(12,2) NOT NULL,
    discount_amount numeric(12,2) NOT NULL,
    status          varchar(20) NOT NULL DEFAULT 'applied',
    created_at      timestamptz NOT NULL DEFAULT now(),
    released_at     timestamptz,
    CONSTRAINT vr_amount_check CHECK (original_amount >= 0 AND discount_amount >= 0 AND discount_amount <= original_amount),
    CONSTRAINT vr_status_check CHECK (status IN ('applied', 'released'))
);
CREATE UNIQUE INDEX vr_booking_unique ON voucher_redemptions (booking_id);
-- Moi nguoi chi dung mot voucher mot lan (khong tinh luot da tra lai).
CREATE UNIQUE INDEX vr_user_voucher_once ON voucher_redemptions (voucher_id, user_id) WHERE status = 'applied';
CREATE INDEX vr_voucher_idx ON voucher_redemptions (voucher_id, status);

ALTER TABLE bookings
    ADD COLUMN voucher_id      uuid REFERENCES vouchers (id) ON DELETE SET NULL,
    ADD COLUMN discount_amount numeric(12,2) NOT NULL DEFAULT 0,
    ADD CONSTRAINT bookings_discount_check CHECK (discount_amount >= 0 AND discount_amount <= total_amount);

-- Hoa hong tinh tren gia goc, tru phan Courtly boi hoan: commission_amount la cong no con lai cua chu san.
-- Phan giam vuot hoa hong la khoan Courtly con no chu san (courtly_owed).
ALTER TABLE booking_commissions
    ADD COLUMN voucher_discount numeric(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN courtly_owed     numeric(12,2) NOT NULL DEFAULT 0;

INSERT INTO permissions (code, name)
VALUES ('voucher.manage', 'Quan ly voucher giam gia')
ON CONFLICT (code) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'voucher.manage' WHERE r.code = 'admin'
ON CONFLICT DO NOTHING;
