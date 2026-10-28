-- Sprint 2.2.4-2.2.7: data quality, model features, Elo and smoothed win rate.

ALTER TABLE match_games DROP CONSTRAINT match_games_score_check;

UPDATE matches
SET excluded_from_rating = true,
    exclusion_reason = 'INVALID_BADMINTON_SCORE_LEGACY'
WHERE id IN (
    SELECT DISTINCT match_id
    FROM match_games
    WHERE NOT (
        (GREATEST(team1_score, team2_score) = 21 AND LEAST(team1_score, team2_score) <= 19)
        OR (GREATEST(team1_score, team2_score) BETWEEN 22 AND 29
            AND ABS(team1_score - team2_score) = 2)
        OR (GREATEST(team1_score, team2_score) = 30
            AND LEAST(team1_score, team2_score) = 29)
    )
);

-- NOT VALID preserves legacy seed rows, while PostgreSQL still rejects every new invalid score.
ALTER TABLE match_games ADD CONSTRAINT match_games_score_check CHECK (
    (GREATEST(team1_score, team2_score) = 21 AND LEAST(team1_score, team2_score) <= 19)
    OR (GREATEST(team1_score, team2_score) BETWEEN 22 AND 29
        AND ABS(team1_score - team2_score) = 2)
    OR (GREATEST(team1_score, team2_score) = 30
        AND LEAST(team1_score, team2_score) = 29)
) NOT VALID;

ALTER TABLE player_statistics
    ADD COLUMN smoothed_win_rate numeric(5,2) NOT NULL DEFAULT 50,
    ADD CONSTRAINT ps_smoothed_win_rate_check
        CHECK (smoothed_win_rate BETWEEN 0 AND 100),
    ADD CONSTRAINT ps_rating_floor_check CHECK (rating_score >= 100);

UPDATE player_statistics
SET smoothed_win_rate = round(((total_wins + 2.5) / (total_matches + 5)) * 100, 2);

ALTER TABLE player_statistics
    ALTER COLUMN avg_points_scored DROP NOT NULL,
    ALTER COLUMN avg_points_scored DROP DEFAULT,
    ALTER COLUMN avg_points_conceded DROP NOT NULL,
    ALTER COLUMN avg_points_conceded DROP DEFAULT;

-- Unknown scoring data stays NULL instead of being imputed with zero or an average.
UPDATE player_statistics
SET avg_points_scored = NULL,
    avg_points_conceded = NULL
WHERE total_matches = 0;

ALTER TABLE player_match_profiles
    ADD COLUMN average_point_difference numeric(8,2),
    ADD COLUMN performance_consistency numeric(8,2),
    ADD COLUMN data_recency_days int,
    ADD COLUMN training_eligible boolean NOT NULL DEFAULT false,
    ADD CONSTRAINT pmp_recency_check
        CHECK (data_recency_days IS NULL OR data_recency_days >= 0),
    ADD CONSTRAINT pmp_win_rate_check CHECK (win_rate BETWEEN 0 AND 100),
    ADD CONSTRAINT pmp_total_matches_check CHECK (total_matches >= 0);

CREATE UNIQUE INDEX prh_user_match_unique
    ON player_rating_history (user_id, match_id)
    WHERE match_id IS NOT NULL;

CREATE INDEX matches_rating_quality_idx
    ON matches (scheduled_at, created_at)
    WHERE status = 'completed';
