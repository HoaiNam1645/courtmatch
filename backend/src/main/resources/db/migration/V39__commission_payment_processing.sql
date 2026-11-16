-- =============================================================================
-- V39: Xu ly thanh toan cong no hoa hong (2.3.47 - 2.3.50)
-- =============================================================================
-- Tien chu san da thanh toan nhung chua du de dong mot hoa hong nguyen ven (thanh toan khong gan ky) duoc giu lam so du,
-- tru dan vao cac hoa hong cu nhat o lan xac nhan sau.
ALTER TABLE court_owner_profiles
    ADD COLUMN commission_credit numeric(12,2) NOT NULL DEFAULT 0,
    ADD CONSTRAINT cop_commission_credit_check CHECK (commission_credit >= 0);

-- Phan Courtly no chu san (courtly_owed) duoc bu tru vao cong no khi xac nhan thanh toan nay.
ALTER TABLE commission_payment_reports
    ADD COLUMN courtly_offset_amount numeric(12,2) NOT NULL DEFAULT 0,
    ADD CONSTRAINT cpr_offset_check CHECK (courtly_offset_amount >= 0);
