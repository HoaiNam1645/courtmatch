-- 2.3.42: quyen xem doanh thu phi nen tang. Co trong seeder nhung database seed tu phien ban cu thi chua co,
-- nen them chu dong va gan cho quan tri vien va chu san (khop RoleAndPermissionSeeder). Chay lai khong loi.
INSERT INTO permissions (code, name)
VALUES ('finance.commission.view', 'Xem cong no hoa hong')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'finance.commission.view'
WHERE r.code IN ('admin', 'owner')
ON CONFLICT DO NOTHING;
