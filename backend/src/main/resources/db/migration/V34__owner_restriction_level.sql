-- B2: muc han che hien tai cua chu san co cong no hoa hong qua han.
-- 0 = khong han che, 1 = chan nhan booking moi, 2 = tam an san. Duoc he thong tinh lai dinh ky va khi doi nguong.
ALTER TABLE court_owner_profiles
    ADD COLUMN restriction_level      smallint NOT NULL DEFAULT 0,
    ADD COLUMN restriction_changed_at timestamptz,
    ADD CONSTRAINT cop_restriction_level_check CHECK (restriction_level IN (0, 1, 2));
