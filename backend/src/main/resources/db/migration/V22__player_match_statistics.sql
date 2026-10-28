-- Sprint 2.2.8: read-optimized match statistics for player history and performance screens.
ALTER TABLE player_statistics
    ADD COLUMN current_win_streak int NOT NULL DEFAULT 0,
    ADD COLUMN longest_win_streak int NOT NULL DEFAULT 0,
    ADD COLUMN total_games_won int NOT NULL DEFAULT 0,
    ADD COLUMN total_games_lost int NOT NULL DEFAULT 0,
    ADD COLUMN match_type_statistics jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD CONSTRAINT ps_streaks_check CHECK (
        current_win_streak >= 0 AND longest_win_streak >= current_win_streak
    ),
    ADD CONSTRAINT ps_games_check CHECK (total_games_won >= 0 AND total_games_lost >= 0),
    ADD CONSTRAINT ps_match_type_statistics_check
        CHECK (jsonb_typeof(match_type_statistics) = 'object');

WITH per_match AS (
    SELECT mp.user_id,
           m.id AS match_id,
           m.match_type,
           m.scheduled_at,
           m.created_at,
           (mp.team_no = mr.winning_team) AS won,
           sum(CASE WHEN mp.team_no = 1 THEN mg.team1_score ELSE mg.team2_score END) AS scored,
           sum(CASE WHEN mp.team_no = 1 THEN mg.team2_score ELSE mg.team1_score END) AS conceded,
           count(*) FILTER (WHERE
               (mp.team_no = 1 AND mg.team1_score > mg.team2_score)
               OR (mp.team_no = 2 AND mg.team2_score > mg.team1_score)) AS games_won,
           count(*) FILTER (WHERE
               (mp.team_no = 1 AND mg.team1_score < mg.team2_score)
               OR (mp.team_no = 2 AND mg.team2_score < mg.team1_score)) AS games_lost
    FROM match_players mp
    JOIN matches m ON m.id = mp.match_id
    JOIN match_results mr ON mr.match_id = m.id
    JOIN match_games mg ON mg.match_id = m.id
    WHERE m.status = 'completed'
      AND mr.confirmed_at IS NOT NULL
      AND NOT (m.excluded_from_rating = true
          AND m.exclusion_reason = 'INVALID_BADMINTON_SCORE_LEGACY')
    GROUP BY mp.user_id, mp.team_no, m.id, m.match_type,
             m.scheduled_at, m.created_at, mr.winning_team
), numbered AS (
    SELECT per_match.*,
           row_number() OVER (
               PARTITION BY user_id ORDER BY scheduled_at ASC NULLS FIRST, created_at ASC, match_id ASC
           ) AS match_number,
           sum(CASE WHEN won THEN 0 ELSE 1 END) OVER (
               PARTITION BY user_id ORDER BY scheduled_at ASC NULLS FIRST, created_at ASC, match_id ASC
           ) AS loss_group
    FROM per_match
), streaks AS (
    SELECT user_id,
           max(streak_length)::int AS longest_win_streak
    FROM (
        SELECT user_id, loss_group, count(*) AS streak_length
        FROM numbered
        WHERE won
        GROUP BY user_id, loss_group
    ) winning_runs
    GROUP BY user_id
), last_losses AS (
    SELECT user_id,
           coalesce(max(match_number) FILTER (WHERE NOT won), 0) AS last_loss_number
    FROM numbered
    GROUP BY user_id
), match_types AS (
    SELECT user_id,
           jsonb_object_agg(match_type, jsonb_build_object(
               'totalMatches', total_matches,
               'totalWins', total_wins,
               'totalLosses', total_matches - total_wins,
               'winRate', round(total_wins * 100.0 / total_matches, 2)
           )) AS statistics
    FROM (
        SELECT user_id, match_type, count(*) AS total_matches,
               count(*) FILTER (WHERE won) AS total_wins
        FROM per_match
        GROUP BY user_id, match_type
    ) grouped_types
    GROUP BY user_id
), aggregate_statistics AS (
    SELECT numbered.user_id,
           count(*)::int AS total_matches,
           count(*) FILTER (WHERE won)::int AS total_wins,
           count(*) FILTER (WHERE NOT won)::int AS total_losses,
           count(*) FILTER (
               WHERE won AND match_number > last_losses.last_loss_number
           )::int AS current_win_streak,
           coalesce(streaks.longest_win_streak, 0) AS longest_win_streak,
           sum(games_won)::int AS total_games_won,
           sum(games_lost)::int AS total_games_lost,
           avg(scored)::numeric AS avg_points_scored,
           avg(conceded)::numeric AS avg_points_conceded,
           max(scheduled_at) AS last_played_at,
           match_types.statistics AS match_type_statistics
    FROM numbered
    JOIN last_losses ON last_losses.user_id = numbered.user_id
    LEFT JOIN streaks ON streaks.user_id = numbered.user_id
    JOIN match_types ON match_types.user_id = numbered.user_id
    GROUP BY numbered.user_id, last_losses.last_loss_number,
             streaks.longest_win_streak, match_types.statistics
)
UPDATE player_statistics statistics
SET total_matches = aggregate.total_matches,
    total_wins = aggregate.total_wins,
    total_losses = aggregate.total_losses,
    win_rate = round(aggregate.total_wins * 100.0 / aggregate.total_matches, 2),
    smoothed_win_rate = round(((aggregate.total_wins + 2.5)
        / (aggregate.total_matches + 5)) * 100, 2),
    current_win_streak = aggregate.current_win_streak,
    longest_win_streak = aggregate.longest_win_streak,
    total_games_won = aggregate.total_games_won,
    total_games_lost = aggregate.total_games_lost,
    avg_points_scored = round(aggregate.avg_points_scored, 2),
    avg_points_conceded = round(aggregate.avg_points_conceded, 2),
    last_played_at = aggregate.last_played_at,
    match_type_statistics = aggregate.match_type_statistics,
    updated_at = now()
FROM aggregate_statistics aggregate
WHERE aggregate.user_id = statistics.user_id;
