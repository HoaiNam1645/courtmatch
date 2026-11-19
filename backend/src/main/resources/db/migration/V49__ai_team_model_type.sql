-- Model AI chia doi dung loai rieng, tach khoi baseline chia doi (double_team_ranker) de moi loai co mot model active.
ALTER TABLE ai_models DROP CONSTRAINT aim_type_check;
ALTER TABLE ai_models ADD CONSTRAINT aim_type_check CHECK (
    model_type IN ('catboost_ranker', 'baseline_ranker', 'double_team_ranker', 'catboost_team_ranker')
);
