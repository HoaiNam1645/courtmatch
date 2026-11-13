-- =============================================================================
-- V26: 2.3.9 - Buoc khung gio dat san cua tung san
-- =============================================================================
-- Khung gio duoc sinh tu gio mo cua + bang gia theo buoc nay (30 hoac 60 phut).
-- Mac dinh 60 de giu nguyen hanh vi hien tai cua cac san da co.

ALTER TABLE venues
    ADD COLUMN slot_step_minutes smallint NOT NULL DEFAULT 60,
    ADD CONSTRAINT venues_slot_step_check CHECK (slot_step_minutes IN (30, 60));

COMMENT ON COLUMN venues.slot_step_minutes IS
    'Buoc sinh khung gio dat san, tinh tu gio mo cua (30 hoac 60 phut) (2.3.9)';
