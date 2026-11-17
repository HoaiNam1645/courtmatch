-- =============================================================================
-- V43: Nhat ky moi lan tim doi tac / chia doi (2.3.70 - 2.3.76)
-- Moi lan chay ghi 1 dong matching_runs; cac goi y gan voi lan chay qua search_id.
-- =============================================================================

CREATE TABLE matching_runs (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    run_type       varchar(30) NOT NULL,
    user_id        uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    model_id       uuid REFERENCES ai_models (id) ON DELETE SET NULL,
    scoring_source varchar(20) NOT NULL DEFAULT 'baseline',
    result_count   int NOT NULL DEFAULT 0,
    duration_ms    int NOT NULL DEFAULT 0,
    status         varchar(20) NOT NULL DEFAULT 'success',
    error_message  varchar(300),
    params_json    jsonb,
    created_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT mr_type_check CHECK (run_type IN ('partner_search', 'double_team')),
    CONSTRAINT mr_source_check CHECK (scoring_source IN ('ai', 'baseline')),
    CONSTRAINT mr_status_check CHECK (status IN ('success', 'failed')),
    CONSTRAINT mr_counts_check CHECK (result_count >= 0 AND duration_ms >= 0)
);
CREATE INDEX mr_type_created_idx ON matching_runs (run_type, created_at DESC);
CREATE INDEX mr_user_created_idx ON matching_runs (user_id, created_at DESC);
CREATE INDEX mr_model_idx ON matching_runs (model_id);

COMMENT ON TABLE matching_runs IS
    'Nhat ky tim doi tac (partner_search) va chia doi (double_team); id cua lan chia doi trung batch_id';

ALTER TABLE partner_suggestions
    ADD COLUMN search_id uuid REFERENCES matching_runs (id) ON DELETE SET NULL,
    ADD COLUMN rank_no smallint,
    ADD CONSTRAINT psg_rank_check CHECK (rank_no IS NULL OR rank_no >= 1);
CREATE INDEX psg_search_idx ON partner_suggestions (search_id);
CREATE INDEX psg_pair_created_idx ON partner_suggestions (user_id, suggested_user_id, created_at DESC);

-- Quyen xem phan tich ghep cap (chi quan tri vien). Them chu dong vi seeder chi chay khi chua co vai tro.
INSERT INTO permissions (code, name)
VALUES ('matching.view', 'Xem phan tich va lich su ghep cap')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'matching.view'
WHERE r.code = 'admin'
ON CONFLICT DO NOTHING;
