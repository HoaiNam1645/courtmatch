-- Danh sach doi tac da xep hang cua mot lan tim (2.2.29): cac trang sau dung dung mot model va thu tu, ben vung qua khoi dong lai.
CREATE TABLE partner_search_lists (
    id             uuid PRIMARY KEY,
    requester_id   uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    model_id       uuid REFERENCES ai_models (id) ON DELETE SET NULL,
    scoring_source varchar(20) NOT NULL,
    fallback_reason varchar(60),
    payload_json   jsonb NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),
    expires_at     timestamptz NOT NULL,
    CONSTRAINT psl_source_check CHECK (scoring_source IN ('ai', 'baseline'))
);
CREATE INDEX psl_expires_idx ON partner_search_lists (expires_at);
