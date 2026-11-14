/** 2.3.28 — quản trị viên quản lý tài khoản người dùng. */

import { request } from './client'

export const USER_STATUS_META = {
  active: { label: 'Đang hoạt động', className: 'bg-emerald-100 text-emerald-700' },
  inactive: { label: 'Tạm ngừng', className: 'bg-stone-200 text-stone-700' },
  banned: { label: 'Bị khóa', className: 'bg-red-100 text-red-700' },
  pending: { label: 'Chưa xác minh', className: 'bg-amber-100 text-amber-800' },
}

export const ROLE_LABEL = { player: 'Người chơi', owner: 'Chủ sân', staff: 'Nhân viên', admin: 'Quản trị viên' }

export const USER_STATUS_TABS = ['all', 'active', 'inactive', 'banned', 'pending']

export function userStatusMeta(status) {
  return USER_STATUS_META[status] ?? { label: status ?? 'Không rõ', className: 'bg-stone-200 text-stone-600' }
}

function mapSummary(item) {
  return {
    id: item.id,
    fullName: item.fullName,
    email: item.email ?? '',
    phone: item.phone ?? '',
    status: item.status,
    roles: item.roles ?? [],
    emailVerified: Boolean(item.emailVerified),
    phoneVerified: Boolean(item.phoneVerified),
    lastLoginAt: item.lastLoginAt ?? null,
    createdAt: item.createdAt,
  }
}

export async function fetchAdminUsers({ q = '', role = 'all', status = 'all', page = 0, size = 20 } = {}) {
  const search = new URLSearchParams({ role, status, page: String(page), size: String(size) })
  if (q.trim()) search.set('q', q.trim())
  const data = await request(`/api/v1/admin/users?${search.toString()}`)
  return {
    users: data.content.map(mapSummary),
    page: data.page,
    totalPages: data.totalPages,
    totalElements: data.totalElements,
    statusCounts: data.statusCounts ?? {},
  }
}

function mapDetail(data) {
  return {
    ...mapSummary(data),
    avatarUrl: data.avatarUrl ?? '',
    emailVerifiedAt: data.emailVerifiedAt ?? null,
    phoneVerifiedAt: data.phoneVerifiedAt ?? null,
    passwordChangeRequired: Boolean(data.passwordChangeRequired),
    updatedAt: data.updatedAt,
    lastStatusChange: data.lastStatusChange
      ? { status: data.lastStatusChange.status, reason: data.lastStatusChange.reason ?? '', actor: data.lastStatusChange.actor ?? '', at: data.lastStatusChange.at }
      : null,
  }
}

export async function fetchAdminUser(userId) {
  return mapDetail(await request(`/api/v1/admin/users/${encodeURIComponent(userId)}`))
}

/** status: active | inactive | banned; reason bắt buộc khi khóa hoặc ngừng. */
export async function changeAdminUserStatus(userId, { status, reason = '' }) {
  return mapDetail(await request(`/api/v1/admin/users/${encodeURIComponent(userId)}/status`, {
    method: 'POST',
    body: { status, reason: reason.trim() || null },
  }))
}
