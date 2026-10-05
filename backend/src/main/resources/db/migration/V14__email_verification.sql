-- =============================================================================
-- V14: Xac minh email khi dang ky (2.1.1)
--
-- Bang rieng chu khong dung chung password_reset_requests: hai luong co vong doi
-- va y nghia khac nhau, gop chung thi phai them cot "purpose" roi moi cau truy van
-- deu phai nho loc theo cot do - som muon se quen mot cho.
-- =============================================================================

CREATE TABLE email_verification_requests (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    email             varchar(255) NOT NULL,
    verification_hash text NOT NULL,
    expires_at        timestamptz NOT NULL,
    verified_at       timestamptz,
    used_at           timestamptz,
    attempt_count     smallint NOT NULL DEFAULT 0,
    resend_count      smallint NOT NULL DEFAULT 0,
    requested_ip      inet,
    user_agent        text,
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT evr_attempt_check CHECK (attempt_count >= 0),
    CONSTRAINT evr_resend_check CHECK (resend_count >= 0)
);

CREATE INDEX evr_user_created_idx ON email_verification_requests (user_id, created_at DESC);
CREATE INDEX evr_email_created_idx ON email_verification_requests (email, created_at DESC);
CREATE INDEX evr_pending_idx ON email_verification_requests (expires_at) WHERE used_at IS NULL;

COMMENT ON TABLE email_verification_requests IS
    'Ma OTP xac minh email luc dang ky; chi luu hash, khong bao gio luu ma goc';
COMMENT ON COLUMN email_verification_requests.used_at IS
    'Da dung xong hoac bi thay the boi ma moi hon - khong con hieu luc';
COMMENT ON COLUMN email_verification_requests.verified_at IS
    'Chi dat khi day dung la ma da xac minh thanh cong';
