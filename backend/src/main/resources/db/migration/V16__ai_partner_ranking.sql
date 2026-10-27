-- =============================================================================
-- V16: Thay phan cum K-Means/DBSCAN bang model AI xep hang doi tac
-- Khong them bang: algorithm_runs -> ai_models, bo player_clusters.
-- =============================================================================

-- Ket qua phan cum khong con thuoc nghiep vu Sprint 2.
DROP TABLE player_clusters;
DROP INDEX pmp_cluster_idx;
ALTER TABLE player_match_profiles DROP COLUMN cluster_label;

-- Tong quat hoa metadata lan chay cu thanh metadata model co phien ban.
ALTER TABLE algorithm_runs DROP CONSTRAINT ar_type_check;
ALTER TABLE algorithm_runs DROP CONSTRAINT ar_status_check;

-- Cac run phan cum khong con duoc tham chieu sau khi bo player_clusters.
DELETE FROM algorithm_runs WHERE algorithm_type IN ('kmeans', 'dbscan');
UPDATE algorithm_runs
SET algorithm_type = CASE algorithm_type
        WHEN 'partner_matching' THEN 'baseline_ranker'
        WHEN 'double_team_matching' THEN 'double_team_ranker'
        ELSE algorithm_type
    END,
    status = CASE status
        WHEN 'running' THEN 'training'
        WHEN 'success' THEN 'ready'
        ELSE status
    END;

ALTER TABLE algorithm_runs RENAME TO ai_models;
ALTER TABLE ai_models RENAME COLUMN algorithm_type TO model_type;
ALTER TABLE ai_models RENAME COLUMN algorithm_version TO model_version;
ALTER TABLE ai_models RENAME COLUMN started_at TO training_started_at;
ALTER TABLE ai_models RENAME COLUMN finished_at TO trained_at;
ALTER INDEX ar_type_started_idx RENAME TO aim_type_started_idx;

-- V8 cho phep version rong va trung lap. Chuan hoa du lieu cu truoc khi them
-- NOT NULL/UNIQUE de migration van chay duoc tren database da hoat dong.
UPDATE ai_models
SET model_version = 'legacy-' || left(id::text, 8)
WHERE model_version IS NULL OR btrim(model_version) = '';

WITH duplicate_versions AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY model_type, model_version
               ORDER BY training_started_at, id
           ) AS duplicate_number
    FROM ai_models
)
UPDATE ai_models model
SET model_version = left(model.model_version, 36) || '-' || left(model.id::text, 8)
FROM duplicate_versions duplicate
WHERE model.id = duplicate.id
  AND duplicate.duplicate_number > 1;

ALTER TABLE ai_models
    ADD COLUMN artifact_path varchar(500),
    ADD COLUMN artifact_checksum varchar(128),
    ADD COLUMN feature_schema_json jsonb,
    ADD COLUMN activated_at timestamptz,
    ALTER COLUMN model_version SET NOT NULL,
    ALTER COLUMN status SET DEFAULT 'training';

ALTER TABLE ai_models
    ADD CONSTRAINT aim_type_check CHECK (
        model_type IN ('catboost_ranker', 'baseline_ranker', 'double_team_ranker')
    ),
    ADD CONSTRAINT aim_status_check CHECK (
        status IN ('training', 'ready', 'active', 'failed', 'archived')
    ),
    ADD CONSTRAINT aim_type_version_unique UNIQUE (model_type, model_version),
    ADD CONSTRAINT aim_activation_check CHECK (
        status <> 'active' OR activated_at IS NOT NULL
    );

CREATE UNIQUE INDEX aim_one_active_type_idx
    ON ai_models (model_type) WHERE status = 'active';

COMMENT ON TABLE ai_models IS
    'Metadata va artifact cua model AI/baseline dung de xep hang doi tac hoac can bang doi';
COMMENT ON COLUMN ai_models.artifact_path IS
    'Duong dan artifact .cbm trong AI service; nullable doi voi baseline khong co model file';
COMMENT ON COLUMN ai_models.feature_schema_json IS
    'Schema va thu tu feature bat buoc giong nhau giua training va inference';
COMMENT ON COLUMN ai_models.metrics_json IS
    'Vi du: {"ndcg_at_10": 0.84, "precision_at_10": 0.76, "recall_at_10": 0.71}';

-- Moi de xuat truy vet duoc model/baseline da tao ra no.
ALTER TABLE partner_suggestions RENAME COLUMN run_id TO model_id;
ALTER INDEX psg_run_idx RENAME TO psg_model_idx;
ALTER TABLE partner_suggestions
    ADD COLUMN confidence_score numeric(5,4),
    ADD COLUMN scoring_source varchar(20) NOT NULL DEFAULT 'baseline',
    ADD CONSTRAINT psg_confidence_check CHECK (
        confidence_score IS NULL OR confidence_score BETWEEN 0 AND 1
    ),
    ADD CONSTRAINT psg_source_check CHECK (scoring_source IN ('ai', 'baseline')),
    ADD CONSTRAINT psg_model_source_check CHECK (
        scoring_source <> 'ai' OR model_id IS NOT NULL
    );

COMMENT ON COLUMN partner_suggestions.confidence_score IS
    'Do tin cay 0..1 do AI service tra ve; nullable khi baseline khong tinh confidence';
COMMENT ON COLUMN partner_suggestions.scoring_source IS
    'ai neu CatBoostRanker cham diem, baseline neu dung cong thuc fallback';

ALTER TABLE double_team_suggestions RENAME COLUMN run_id TO model_id;
ALTER INDEX dts_run_idx RENAME TO dts_model_idx;
