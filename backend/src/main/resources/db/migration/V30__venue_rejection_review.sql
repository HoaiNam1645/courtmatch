-- =============================================================================
-- V30: Ghi lai nguoi, thoi diem va ly do khi quan tri vien tu choi san (2.3.35)
-- =============================================================================

ALTER TABLE venues
    ADD COLUMN rejected_by uuid REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN rejected_at timestamptz,
    ADD COLUMN rejection_code varchar(50),
    ADD COLUMN rejection_reason text;
