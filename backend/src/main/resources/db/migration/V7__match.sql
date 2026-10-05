-- =============================================================================
-- V7: Nhom 6 - Tran dau, thong ke nguoi choi (6 bang)
-- =============================================================================

-- 6.1 matches -------------------------------------------------------------
CREATE TABLE matches (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id   uuid REFERENCES bookings (id) ON DELETE SET NULL,
    venue_id     uuid REFERENCES venues (id) ON DELETE SET NULL,
    match_type   varchar(30) NOT NULL,
    scheduled_at timestamptz,
    status       varchar(30) NOT NULL DEFAULT 'scheduled',
    created_by   uuid REFERENCES users (id) ON DELETE SET NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT matches_type_check CHECK (match_type IN ('single', 'double', 'mixed_double')),
    CONSTRAINT matches_status_check CHECK (status IN ('scheduled', 'completed', 'cancelled'))
);
CREATE INDEX matches_scheduled_idx ON matches (scheduled_at DESC);
CREATE INDEX matches_status_idx ON matches (status);
CREATE INDEX matches_booking_idx ON matches (booking_id);
CREATE TRIGGER matches_set_updated_at BEFORE UPDATE ON matches
    FOR EACH ROW EXECUTE FUNCTION courtly_set_updated_at();

-- 6.2 match_players -------------------------------------------------------
CREATE TABLE match_players (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    match_id      uuid NOT NULL REFERENCES matches (id) ON DELETE CASCADE,
    user_id       uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    team_no       smallint NOT NULL,
    position_no   smallint,
    joined_status varchar(30) NOT NULL DEFAULT 'invited',
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT match_players_unique UNIQUE (match_id, user_id),
    CONSTRAINT match_players_team_check CHECK (team_no IN (1, 2)),
    CONSTRAINT match_players_joined_check CHECK (joined_status IN ('invited', 'accepted', 'declined', 'joined'))
);
CREATE INDEX match_players_user_idx ON match_players (user_id);
CREATE INDEX match_players_match_idx ON match_players (match_id, team_no);

-- 6.3 match_games ---------------------------------------------------------
CREATE TABLE match_games (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    match_id    uuid NOT NULL REFERENCES matches (id) ON DELETE CASCADE,
    game_no     smallint NOT NULL,
    team1_score int NOT NULL DEFAULT 0,
    team2_score int NOT NULL DEFAULT 0,
    CONSTRAINT match_games_unique UNIQUE (match_id, game_no),
    CONSTRAINT match_games_no_check CHECK (game_no >= 1),
    CONSTRAINT match_games_score_check CHECK (team1_score >= 0 AND team2_score >= 0)
);

-- 6.4 match_results -------------------------------------------------------
CREATE TABLE match_results (
    match_id     uuid PRIMARY KEY REFERENCES matches (id) ON DELETE CASCADE,
    winning_team smallint NOT NULL,
    recorded_by  uuid REFERENCES users (id) ON DELETE SET NULL,
    confirmed_at timestamptz,
    note         text,
    CONSTRAINT match_results_team_check CHECK (winning_team IN (1, 2))
);

-- 6.5 player_statistics ---------------------------------------------------
CREATE TABLE player_statistics (
    user_id             uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    total_matches       int NOT NULL DEFAULT 0,
    total_wins          int NOT NULL DEFAULT 0,
    total_losses        int NOT NULL DEFAULT 0,
    win_rate            numeric(5,2) NOT NULL DEFAULT 0,
    rating_score        numeric(8,2) NOT NULL DEFAULT 1000,
    avg_points_scored   numeric(8,2) NOT NULL DEFAULT 0,
    avg_points_conceded numeric(8,2) NOT NULL DEFAULT 0,
    last_played_at      timestamptz,
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ps_counts_check CHECK (total_matches >= 0 AND total_wins >= 0 AND total_losses >= 0),
    CONSTRAINT ps_win_rate_check CHECK (win_rate >= 0 AND win_rate <= 100)
);
CREATE INDEX ps_rating_idx ON player_statistics (rating_score DESC);
CREATE TRIGGER ps_set_updated_at BEFORE UPDATE ON player_statistics
    FOR EACH ROW EXECUTE FUNCTION courtly_set_updated_at();

COMMENT ON TABLE player_statistics IS
    'Bang tong hop doc nhanh. Khong tinh lai thong ke moi lan mo man hinh ho so';

-- 6.6 player_rating_history -----------------------------------------------
CREATE TABLE player_rating_history (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    match_id      uuid REFERENCES matches (id) ON DELETE SET NULL,
    old_rating    numeric(8,2) NOT NULL,
    new_rating    numeric(8,2) NOT NULL,
    change_amount numeric(8,2) NOT NULL,
    reason        varchar(100),
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX prh_user_idx ON player_rating_history (user_id, created_at DESC);
CREATE INDEX prh_match_idx ON player_rating_history (match_id);
