-- =============================================================================
-- V48: Quan ly model AI (2.2.16, 2.2.19) - quyen nhap / kich hoat / quay lai phien ban
-- =============================================================================

INSERT INTO permissions (code, name)
VALUES ('matching.manage', 'Nhap, kich hoat va quay lai phien ban model AI')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'matching.manage'
WHERE r.code = 'admin'
ON CONFLICT DO NOTHING;
