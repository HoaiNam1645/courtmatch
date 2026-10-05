-- =============================================================================
-- V9: Nhom 8 - Danh gia, bao cao (3 bang)
-- =============================================================================

-- 8.1 venue_reviews -------------------------------------------------------
CREATE TABLE venue_reviews (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    venue_id   uuid NOT NULL REFERENCES venues (id) ON DELETE CASCADE,
    user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    booking_id uuid REFERENCES bookings (id) ON DELETE SET NULL,
    rating     smallint NOT NULL,
    comment    text,
    status     varchar(30) NOT NULL DEFAULT 'visible',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT vr_rating_check CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT vr_status_check CHECK (status IN ('visible', 'hidden', 'reported')),
    CONSTRAINT vr_unique UNIQUE (venue_id, user_id, booking_id)
);
CREATE INDEX vr_venue_idx ON venue_reviews (venue_id, created_at DESC);
CREATE INDEX vr_user_idx ON venue_reviews (user_id);
CREATE TRIGGER vr_set_updated_at BEFORE UPDATE ON venue_reviews
    FOR EACH ROW EXECUTE FUNCTION courtly_set_updated_at();

-- 8.2 partner_reviews -----------------------------------------------------
CREATE TABLE partner_reviews (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reviewer_id      uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    reviewed_user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    match_id         uuid REFERENCES matches (id) ON DELETE SET NULL,
    rating           smallint NOT NULL,
    comment          text,
    status           varchar(30) NOT NULL DEFAULT 'visible',
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ptr_rating_check CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ptr_status_check CHECK (status IN ('visible', 'hidden', 'reported')),
    CONSTRAINT ptr_self_check CHECK (reviewer_id <> reviewed_user_id)
);
CREATE INDEX ptr_reviewed_idx ON partner_reviews (reviewed_user_id, created_at DESC);
CREATE INDEX ptr_reviewer_idx ON partner_reviews (reviewer_id);
-- Moi tran chi danh gia mot nguoi mot lan.
CREATE UNIQUE INDEX ptr_match_unique ON partner_reviews (reviewer_id, reviewed_user_id, match_id)
    WHERE match_id IS NOT NULL;

-- 8.3 reports -------------------------------------------------------------
CREATE TABLE reports (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    target_type varchar(50) NOT NULL,
    target_id   uuid NOT NULL,
    reason      varchar(100) NOT NULL,
    description text,
    status      varchar(30) NOT NULL DEFAULT 'pending',
    handled_by  uuid REFERENCES users (id) ON DELETE SET NULL,
    handled_at  timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT reports_target_check CHECK (target_type IN ('user', 'venue', 'venue_review', 'partner_review')),
    CONSTRAINT reports_status_check CHECK (status IN ('pending', 'reviewing', 'resolved', 'rejected'))
);
CREATE INDEX reports_target_idx ON reports (target_type, target_id);
CREATE INDEX reports_status_idx ON reports (status, created_at DESC);

COMMENT ON COLUMN reports.target_id IS 'Khoa da hinh, khong co FK vi tro toi nhieu bang khac nhau';
