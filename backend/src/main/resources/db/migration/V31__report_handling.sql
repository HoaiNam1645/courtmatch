-- 2.3.36 - luu huong xu ly va ghi chu khi quan tri vien ket luan bao cao.
ALTER TABLE reports
    ADD COLUMN handling_action varchar(30),
    ADD COLUMN handling_note   text;

ALTER TABLE reports
    ADD CONSTRAINT reports_handling_action_check
        CHECK (handling_action IS NULL OR handling_action IN ('warning', 'account_banned', 'account_inactive', 'other', 'review_hidden', 'review_kept'));

COMMENT ON COLUMN reports.handling_action IS 'Huong xu ly khi ket luan: warning, account_banned, account_inactive, other, review_hidden, review_kept';
COMMENT ON COLUMN reports.handling_note IS 'Ghi chu cua quan tri vien khi ket luan bao cao';
