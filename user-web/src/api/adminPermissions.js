/** 2.3.30 — ma trận vai trò - quyền của quản trị viên. */

import { request } from './client'

function mapMatrix(data) {
  return {
    permissions: data.permissions.map((item) => ({ code: item.code, name: item.name })),
    roles: data.roles.map((role) => ({ code: role.code, name: role.name, permissions: role.permissions })),
    protectedPermissions: data.protectedPermissions ?? [],
  }
}

export async function fetchPermissionMatrix() {
  return mapMatrix(await request('/api/v1/admin/permissions'))
}

/** Thay toàn bộ tập quyền của một vai trò. */
export async function updateRolePermissions(roleCode, permissions) {
  return mapMatrix(await request(`/api/v1/admin/permissions/roles/${encodeURIComponent(roleCode)}`, { method: 'PUT', body: { permissions } }))
}
