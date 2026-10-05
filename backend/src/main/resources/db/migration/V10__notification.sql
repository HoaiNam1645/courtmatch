-- =============================================================================
-- V10: Nhom 9 - Thong bao (2 bang)
-- =============================================================================

-- 9.1 notifications -------------------------------------------------------
CREATE TABLE notifications (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       varchar(80) NOT NULL,
    title      varchar(255) NOT NULL,
    content    text,
    data_json  jsonb,
    read_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX notifications_inbox_idx ON notifications (user_id, read_at, created_at DESC);
CREATE INDEX notifications_unread_idx ON notifications (user_id, created_at DESC) WHERE read_at IS NULL;

COMMENT ON COLUMN notifications.data_json IS 'Metadata dieu huong, vi du {"bookingId": "...", "route": "/bookings/..."}';

-- 9.2 notification_preferences --------------------------------------------
CREATE TABLE notification_preferences (
    user_id        uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type           varchar(80) NOT NULL,
    in_app_enabled boolean NOT NULL DEFAULT true,
    email_enabled  boolean NOT NULL DEFAULT true,
    push_enabled   boolean NOT NULL DEFAULT false,
    updated_at     timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, type)
);
CREATE TRIGGER np_set_updated_at BEFORE UPDATE ON notification_preferences
    FOR EACH ROW EXECUTE FUNCTION courtly_set_updated_at();
