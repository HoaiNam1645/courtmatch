-- =============================================================================
-- V44: Chu san tra loi cong khai danh gia san (2.3.81, 2.3.82)
-- Moi danh gia toi da mot cau tra loi; xoa danh gia thi cau tra loi xoa theo.
-- =============================================================================

CREATE TABLE venue_review_replies (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id  uuid NOT NULL REFERENCES venue_reviews (id) ON DELETE CASCADE,
    owner_id   uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    content    text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT vrr_review_unique UNIQUE (review_id),
    CONSTRAINT vrr_content_check CHECK (length(btrim(content)) BETWEEN 1 AND 1000)
);
CREATE INDEX vrr_owner_idx ON venue_review_replies (owner_id, created_at DESC);
CREATE TRIGGER vrr_set_updated_at BEFORE UPDATE ON venue_review_replies
    FOR EACH ROW EXECUTE FUNCTION courtly_set_updated_at();

COMMENT ON TABLE venue_review_replies IS 'Cau tra loi cong khai cua chu san duoi danh gia san; moi danh gia mot cau';
