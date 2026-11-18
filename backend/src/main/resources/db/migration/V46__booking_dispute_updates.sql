-- =============================================================================
-- V46: Cac lan bo sung thong tin cua nguoi bao cao (2.3.90, 2.3.91)
-- Thiet ke khong co bang tin nhan rieng cho tranh chap: cac lan bo sung luu cung ho so,
-- dang [{id, content, createdAt}], khong sua/xoa de giu nguyen lich su.
-- =============================================================================

ALTER TABLE booking_disputes ADD COLUMN reporter_updates_json jsonb;

COMMENT ON COLUMN booking_disputes.reporter_updates_json IS
    'Cac lan nguoi bao cao bo sung thong tin: [{id, content, createdAt}]; anh dinh kem nam trong reporter_evidences_json voi supplementId tuong ung';
