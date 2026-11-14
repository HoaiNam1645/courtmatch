-- =============================================================================
-- V29: Ghi lai nguoi va thoi diem quan tri vien phe duyet san (2.3.34)
-- =============================================================================

ALTER TABLE venues
    ADD COLUMN approved_by uuid REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN approved_at timestamptz;

CREATE INDEX venues_approval_created_idx ON venues (approval_status, created_at DESC);
