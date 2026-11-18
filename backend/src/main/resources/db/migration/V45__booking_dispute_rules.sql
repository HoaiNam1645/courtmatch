-- =============================================================================
-- V45: Quy tac bao cao ve don dat san (2.3.85, 2.3.86)
-- Loai bao cao nam trong danh muc co dinh; moi don chi co mot bao cao dang xu ly.
-- =============================================================================

ALTER TABLE booking_disputes
    ADD CONSTRAINT bd_category_check CHECK (
        category IN ('venue_closed', 'venue_mismatch', 'owner_cancelled', 'extra_charge', 'other')
    );

CREATE UNIQUE INDEX bd_one_active_per_booking
    ON booking_disputes (booking_id)
    WHERE status IN ('open', 'awaiting_owner_response', 'reviewing');
