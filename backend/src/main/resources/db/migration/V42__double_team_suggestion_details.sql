-- =============================================================================
-- V42: Chi tiet de xuat chia doi doi (2.3.63 - 2.3.69)
-- Moi lan nguoi choi chon 4 nguoi tao mot batch gom 3 phuong an chia doi.
-- Cac cot nullable de giu du lieu mau cu, rang buoc chi ap dung khi co gia tri.
-- =============================================================================

ALTER TABLE double_team_suggestions
    ADD COLUMN requested_by   uuid REFERENCES users (id) ON DELETE CASCADE,
    ADD COLUMN batch_id       uuid,
    ADD COLUMN match_type     varchar(20),
    ADD COLUMN rank_no        smallint,
    ADD COLUMN recommended    boolean NOT NULL DEFAULT false,
    ADD COLUMN scoring_source varchar(20) NOT NULL DEFAULT 'baseline',
    ADD COLUMN detail_json    jsonb,
    ADD CONSTRAINT dts_match_type_check CHECK (
        match_type IS NULL OR match_type IN ('mens', 'womens', 'mixed')
    ),
    ADD CONSTRAINT dts_source_check CHECK (scoring_source IN ('ai', 'baseline')),
    ADD CONSTRAINT dts_rank_check CHECK (rank_no IS NULL OR rank_no BETWEEN 1 AND 3),
    ADD CONSTRAINT dts_batch_rank_unique UNIQUE (batch_id, rank_no);

CREATE INDEX dts_requester_idx ON double_team_suggestions (requested_by, created_at DESC);
CREATE INDEX dts_batch_idx ON double_team_suggestions (batch_id);

COMMENT ON COLUMN double_team_suggestions.batch_id IS
    'Cac phuong an cua cung mot lan chon 4 nguoi choi; moi batch co 3 phuong an (3 cach chia 4 nguoi thanh 2 doi)';
COMMENT ON COLUMN double_team_suggestions.recommended IS
    'Phuong an can bang nhat cua batch (rank_no = 1)';
COMMENT ON COLUMN double_team_suggestions.detail_json IS
    'Suc manh tung nguoi, tuong thich tung cap va xac suat thang tai thoi diem de xuat';
