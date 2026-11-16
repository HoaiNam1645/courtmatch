-- 2.3.45 - 2.3.52: quyen xu ly cong no hoa hong (chot ky, xac nhan thanh toan, dieu chinh). Chi quan tri vien.
INSERT INTO permissions (code, name)
VALUES ('finance.commission.manage', 'Xu ly cong no hoa hong')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'finance.commission.manage'
WHERE r.code = 'admin'
ON CONFLICT DO NOTHING;
