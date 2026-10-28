-- Backfill the 2.2.5 feature vector for users that already had statistics before V20.
INSERT INTO player_match_profiles (
    user_id,
    skill_score,
    win_rate,
    total_matches,
    training_eligible,
    updated_at
)
SELECT ps.user_id,
       ps.rating_score,
       ps.smoothed_win_rate,
       ps.total_matches,
       ps.total_matches >= 3,
       now()
FROM player_statistics ps
ON CONFLICT (user_id) DO UPDATE
SET skill_score = EXCLUDED.skill_score,
    win_rate = EXCLUDED.win_rate,
    total_matches = EXCLUDED.total_matches,
    training_eligible = EXCLUDED.training_eligible,
    updated_at = now();

WITH per_match AS (
    SELECT mp.user_id,
           m.id AS match_id,
           sum(CASE WHEN mp.team_no = 1 THEN mg.team1_score ELSE mg.team2_score END) AS scored,
           sum(CASE WHEN mp.team_no = 1 THEN mg.team2_score ELSE mg.team1_score END) AS conceded,
           m.scheduled_at
    FROM match_players mp
    JOIN matches m ON m.id = mp.match_id
    JOIN match_results mr ON mr.match_id = m.id
    JOIN match_games mg ON mg.match_id = m.id
    WHERE m.status = 'completed'
      AND mr.confirmed_at IS NOT NULL
      AND m.excluded_from_rating = false
    GROUP BY mp.user_id, m.id, m.scheduled_at
), aggregate_features AS (
    SELECT user_id,
           avg(scored - conceded)::numeric AS average_point_difference,
           stddev_samp(scored - conceded)::numeric AS performance_consistency,
           GREATEST(0, floor(extract(epoch FROM (now() - max(scheduled_at))) / 86400))::int
               AS data_recency_days
    FROM per_match
    GROUP BY user_id
)
UPDATE player_match_profiles profile
SET average_point_difference = round(features.average_point_difference, 2),
    performance_consistency = round(features.performance_consistency, 2),
    data_recency_days = features.data_recency_days,
    updated_at = now()
FROM aggregate_features features
WHERE features.user_id = profile.user_id;
