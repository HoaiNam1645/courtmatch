-- =============================================================================
-- V12: Chuan hoa gia tri playing_style
--
-- Giao dien /profile/preferences chi co ba lua chon: attacking, balanced, defensive.
-- Truoc day cot nay khong co rang buoc nen du lieu mau ghi 'all_round' - gia tri
-- khong co trong danh sach hien thi. Doi ve 'balanced' va chot bang CHECK constraint.
-- =============================================================================

UPDATE player_profiles SET playing_style = 'balanced' WHERE playing_style = 'all_round';
UPDATE player_match_profiles SET playing_style = 'balanced' WHERE playing_style = 'all_round';

ALTER TABLE player_profiles
    ADD CONSTRAINT player_profiles_playing_style_check
    CHECK (playing_style IS NULL OR playing_style IN ('attacking', 'balanced', 'defensive'));

ALTER TABLE player_match_profiles
    ADD CONSTRAINT pmp_playing_style_check
    CHECK (playing_style IS NULL OR playing_style IN ('attacking', 'balanced', 'defensive'));

COMMENT ON COLUMN player_profiles.playing_style IS
    'attacking | balanced | defensive - khop voi playingStyleOptions o frontend';
