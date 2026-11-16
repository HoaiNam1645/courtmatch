-- =============================================================================
-- V41: Nhac nho (2.3.59, 2.3.60) - dau da gui de job chay lai khong gui trung
-- =============================================================================
CREATE TABLE notification_reminders (
    type       varchar(80)  NOT NULL,
    ref_id     uuid         NOT NULL,
    slot       varchar(40)  NOT NULL,
    user_id    uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    sent_at    timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (type, ref_id, slot, user_id)
);
CREATE INDEX nr_sent_idx ON notification_reminders (sent_at);

COMMENT ON TABLE notification_reminders IS
    'Moi (loai, doi tuong, moc, nguoi nhan) chi gui nhac mot lan; chen trung bi bo qua (ON CONFLICT DO NOTHING)';
