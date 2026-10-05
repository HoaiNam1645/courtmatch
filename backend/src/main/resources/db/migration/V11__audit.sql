-- =============================================================================
-- V11: Nhom 10 - Nhat ky he thong (1 bang)
-- =============================================================================

-- 10.1 audit_logs ---------------------------------------------------------
CREATE TABLE audit_logs (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id    uuid REFERENCES users (id) ON DELETE SET NULL,
    action      varchar(100) NOT NULL,
    entity_type varchar(80) NOT NULL,
    entity_id   uuid,
    old_data    jsonb,
    new_data    jsonb,
    ip_address  varchar(60),
    user_agent  text,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_logs_actor_idx ON audit_logs (actor_id, created_at DESC);
CREATE INDEX audit_logs_entity_idx ON audit_logs (entity_type, entity_id);

COMMENT ON COLUMN audit_logs.actor_id IS 'NULL khi hanh dong do he thong tu thuc hien';
