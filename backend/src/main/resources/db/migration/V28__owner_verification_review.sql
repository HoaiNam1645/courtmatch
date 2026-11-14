-- =============================================================================
-- V28: Ghi lai nguoi va ly do khi quan tri vien xac minh ho so chu san (2.3.32)
-- =============================================================================

ALTER TABLE court_owner_profiles
    ADD COLUMN verification_note text,
    ADD COLUMN verified_by uuid REFERENCES users (id) ON DELETE SET NULL;

COMMENT ON COLUMN court_owner_profiles.verification_note IS 'Ly do khi quan tri vien tu choi hoac dat lai xac minh';
