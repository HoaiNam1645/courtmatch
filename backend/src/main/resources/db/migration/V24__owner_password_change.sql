-- =============================================================================
-- V24: 2.3.1 - Tai khoan chu san do quan tri vien cap
-- =============================================================================
-- Quan tri vien tao tai khoan chu san kem mat khau tam. Chu san phai tu dat mat khau
-- moi truoc khi dung bat ky chuc nang quan ly nao.

ALTER TABLE users
    ADD COLUMN password_change_required boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN users.password_change_required IS
    'true khi mat khau hien tai la mat khau tam do quan tri vien cap (2.3.1)';
