-- =============================================================================
-- V8: Nhom 7 - Ghep cap, phan cum, goi y doi tac (7 bang)
-- =============================================================================

-- 7.1 player_match_profiles -----------------------------------------------
CREATE TABLE player_match_profiles (
    user_id             uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    skill_score         numeric(8,2) NOT NULL DEFAULT 0,
    win_rate            numeric(5,2) NOT NULL DEFAULT 0,
    total_matches       int NOT NULL DEFAULT 0,
    preferred_location  geography(Point, 4326),
    preferred_radius_km numeric(6,2) NOT NULL DEFAULT 5,
    availability_score  jsonb,
    playing_style       varchar(50),
    cluster_label       varchar(50),
    updated_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX pmp_location_gix ON player_match_profiles USING gist (preferred_location);
CREATE INDEX pmp_cluster_idx ON player_match_profiles (cluster_label);
CREATE TRIGGER pmp_set_updated_at BEFORE UPDATE ON player_match_profiles
    FOR EACH ROW EXECUTE FUNCTION courtly_set_updated_at();

COMMENT ON TABLE player_match_profiles IS
    'Vector dac trung tong hop tu player_profiles, player_statistics, availability va preferred_locations';
COMMENT ON COLUMN player_match_profiles.availability_score IS
    'Vi du: {"1": [18,19,20], "6": [8,9,10]} - thu trong tuan -> cac gio thuong ranh';

-- 7.2 algorithm_runs ------------------------------------------------------
CREATE TABLE algorithm_runs (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    algorithm_type    varchar(50) NOT NULL,
    algorithm_version varchar(50),
    parameters_json   jsonb,
    metrics_json      jsonb,
    started_at        timestamptz NOT NULL DEFAULT now(),
    finished_at       timestamptz,
    status            varchar(30) NOT NULL DEFAULT 'running',
    CONSTRAINT ar_type_check CHECK (
        algorithm_type IN ('kmeans', 'dbscan', 'partner_matching', 'double_team_matching')
    ),
    CONSTRAINT ar_status_check CHECK (status IN ('running', 'success', 'failed'))
);
CREATE INDEX ar_type_started_idx ON algorithm_runs (algorithm_type, started_at DESC);

COMMENT ON COLUMN algorithm_runs.metrics_json IS
    'Vi du: {"silhouette": 0.62, "davies_bouldin": 0.81, "n_clusters": 4}';

-- 7.3 player_clusters -----------------------------------------------------
CREATE TABLE player_clusters (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id                uuid NOT NULL REFERENCES algorithm_runs (id) ON DELETE CASCADE,
    user_id               uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    cluster_label         varchar(50) NOT NULL,
    distance_to_centroid  numeric(10,4),
    created_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pc_run_user_unique UNIQUE (run_id, user_id)
);
CREATE INDEX pc_cluster_idx ON player_clusters (cluster_label);
CREATE INDEX pc_user_idx ON player_clusters (user_id);

-- 7.4 partner_suggestions -------------------------------------------------
CREATE TABLE partner_suggestions (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id            uuid REFERENCES algorithm_runs (id) ON DELETE SET NULL,
    user_id           uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    suggested_user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    skill_score       numeric(6,2),
    location_score    numeric(6,2),
    time_score        numeric(6,2),
    style_score       numeric(6,2),
    total_score       numeric(6,2) NOT NULL DEFAULT 0,
    reason_json       jsonb,
    status            varchar(30) NOT NULL DEFAULT 'active',
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT psg_status_check CHECK (status IN ('active', 'hidden', 'expired')),
    CONSTRAINT psg_self_check CHECK (user_id <> suggested_user_id),
    CONSTRAINT psg_unique UNIQUE (user_id, suggested_user_id, created_at)
);
CREATE INDEX psg_ranking_idx ON partner_suggestions (user_id, total_score DESC);
CREATE INDEX psg_run_idx ON partner_suggestions (run_id);

-- 7.5 partner_requests ----------------------------------------------------
CREATE TABLE partner_requests (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    receiver_id  uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    message      text,
    status       varchar(30) NOT NULL DEFAULT 'pending',
    responded_at timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pr_status_check CHECK (status IN ('pending', 'accepted', 'rejected', 'cancelled', 'expired')),
    CONSTRAINT pr_self_check CHECK (requester_id <> receiver_id)
);
CREATE INDEX pr_receiver_idx ON partner_requests (receiver_id, status);
CREATE INDEX pr_requester_idx ON partner_requests (requester_id, status);
-- Khong gui trung yeu cau khi yeu cau cu con dang cho.
CREATE UNIQUE INDEX pr_pending_unique ON partner_requests (requester_id, receiver_id) WHERE status = 'pending';

-- 7.6 player_connections --------------------------------------------------
CREATE TABLE player_connections (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    partner_id        uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source_request_id uuid REFERENCES partner_requests (id) ON DELETE SET NULL,
    status            varchar(30) NOT NULL DEFAULT 'active',
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT plc_unique UNIQUE (user_id, partner_id),
    CONSTRAINT plc_status_check CHECK (status IN ('active', 'blocked', 'removed')),
    CONSTRAINT plc_self_check CHECK (user_id <> partner_id)
);
CREATE INDEX plc_partner_idx ON player_connections (partner_id);

COMMENT ON TABLE player_connections IS
    'Luu hai chieu: khi chap nhan yeu cau ghep cap thi tao 2 ban ghi (a->b va b->a)';

-- 7.7 double_team_suggestions ---------------------------------------------
CREATE TABLE double_team_suggestions (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id         uuid NOT NULL REFERENCES algorithm_runs (id) ON DELETE CASCADE,
    match_id       uuid REFERENCES matches (id) ON DELETE SET NULL,
    team1_players  jsonb NOT NULL,
    team2_players  jsonb NOT NULL,
    team1_strength numeric(8,2),
    team2_strength numeric(8,2),
    balance_score  numeric(8,2),
    created_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX dts_run_idx ON double_team_suggestions (run_id);
CREATE INDEX dts_balance_idx ON double_team_suggestions (balance_score DESC);

COMMENT ON COLUMN double_team_suggestions.team1_players IS 'Mang user_id, vi du ["uuid-a", "uuid-b"]';
