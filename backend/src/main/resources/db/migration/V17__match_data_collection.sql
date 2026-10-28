-- Sprint 2.2.1-2.2.3: collect match results, confirm them and build player statistics.

ALTER TABLE matches
    ADD COLUMN data_source varchar(30) NOT NULL DEFAULT 'manual',
    ADD COLUMN excluded_from_rating boolean NOT NULL DEFAULT false,
    ADD COLUMN exclusion_reason text;

ALTER TABLE matches DROP CONSTRAINT matches_status_check;
ALTER TABLE matches ADD CONSTRAINT matches_status_check
    CHECK (status IN ('scheduled', 'completed', 'disputed', 'cancelled'));
ALTER TABLE matches ADD CONSTRAINT matches_data_source_check
    CHECK (data_source IN ('booking', 'manual', 'seeded'));
ALTER TABLE matches ADD CONSTRAINT matches_exclusion_reason_check
    CHECK (excluded_from_rating OR exclusion_reason IS NULL);

ALTER TABLE match_players
    ADD COLUMN result_confirmed_at timestamptz,
    ADD COLUMN result_disputed_at timestamptz,
    ADD CONSTRAINT match_players_result_response_check
        CHECK (result_confirmed_at IS NULL OR result_disputed_at IS NULL);

ALTER TABLE match_games DROP CONSTRAINT match_games_no_check;
ALTER TABLE match_games DROP CONSTRAINT match_games_score_check;
ALTER TABLE match_games ADD CONSTRAINT match_games_no_check CHECK (game_no BETWEEN 1 AND 3);
ALTER TABLE match_games ADD CONSTRAINT match_games_score_check
    CHECK (team1_score BETWEEN 0 AND 30 AND team2_score BETWEEN 0 AND 30
           AND team1_score <> team2_score);

CREATE UNIQUE INDEX matches_active_booking_unique
    ON matches (booking_id)
    WHERE booking_id IS NOT NULL AND status <> 'cancelled';
CREATE INDEX matches_pending_confirmation_idx
    ON matches (created_at)
    WHERE status = 'scheduled';
CREATE INDEX match_players_disputed_idx
    ON match_players (result_disputed_at)
    WHERE result_disputed_at IS NOT NULL;
CREATE INDEX match_results_confirmed_idx
    ON match_results (confirmed_at)
    WHERE confirmed_at IS NOT NULL;
