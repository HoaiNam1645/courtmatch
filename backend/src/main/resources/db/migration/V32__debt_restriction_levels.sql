-- 2.3.39 - 2.3.41: nguong han che chu san co cong no hoa hong qua han (2 muc).
-- Muc 1 chan nhan booking moi, muc 2 tam an san. Dat mot trong hai nguong (so tien, so ngay) la ap dung.
-- Mac dinh tat (enabled = false) de khong han che ai cho toi khi quan tri vien bat.
CREATE TABLE debt_restriction_levels (
    level              smallint PRIMARY KEY,
    enabled            boolean NOT NULL DEFAULT false,
    min_overdue_amount numeric(14,2),
    min_overdue_days   integer,
    updated_by         uuid REFERENCES users (id) ON DELETE SET NULL,
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT drl_level_check CHECK (level IN (1, 2)),
    CONSTRAINT drl_amount_check CHECK (min_overdue_amount IS NULL OR min_overdue_amount > 0),
    CONSTRAINT drl_days_check CHECK (min_overdue_days IS NULL OR min_overdue_days > 0),
    CONSTRAINT drl_threshold_check CHECK (NOT enabled OR min_overdue_amount IS NOT NULL OR min_overdue_days IS NOT NULL)
);

INSERT INTO debt_restriction_levels (level, enabled, min_overdue_amount, min_overdue_days)
VALUES (1, false, 2000000, 14), (2, false, 5000000, 30);

COMMENT ON TABLE debt_restriction_levels IS 'Nguong cong no qua han de han che chu san: muc 1 chan booking moi, muc 2 tam an san';
