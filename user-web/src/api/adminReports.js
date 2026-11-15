/** 2.3.36 — quản trị viên xử lý báo cáo người dùng. */

import { request } from './client'

export const REPORT_TABS = ['pending', 'reviewing', 'resolved', 'rejected', 'all']

export const REPORT_STATUS_META = {
  pending: { label: 'Chờ xử lý', className: 'bg-amber-100 text-amber-800' },
  reviewing: { label: 'Đang xử lý', className: 'bg-sky-100 text-sky-700' },
  resolved: { label: 'Đã xử lý', className: 'bg-emerald-100 text-emerald-700' },
  rejected: { label: 'Đã bác bỏ', className: 'bg-slate-200 text-slate-600' },
}

export function reportStatusMeta(status) {
  return REPORT_STATUS_META[status] ?? { label: status ?? 'Không rõ', className: 'bg-stone-200 text-stone-600' }
}

/** Hướng xử lý khi chấp nhận báo cáo; hai hướng đầu tác động tới tài khoản bị báo cáo. */
export const RESOLVE_ACTIONS = [
  { value: 'warning', label: 'Cảnh cáo', hint: 'Nhắc nhở người dùng, tài khoản giữ nguyên.', locks: false },
  { value: 'account_banned', label: 'Khóa tài khoản', hint: 'Người dùng không đăng nhập được nữa.', locks: true },
  { value: 'account_inactive', label: 'Ngừng tài khoản', hint: 'Tạm ngừng hoạt động để xác minh thêm.', locks: true },
  { value: 'other', label: 'Hướng xử lý khác', hint: 'Ghi rõ trong ghi chú.', locks: false },
]

export function resolveActionLabel(value) {
  return RESOLVE_ACTIONS.find((action) => action.value === value)?.label ?? value ?? ''
}

export const ACCOUNT_STATUS_LABEL = { active: 'Đang hoạt động', inactive: 'Ngừng hoạt động', banned: 'Bị khóa', pending: 'Chưa xác minh' }

function mapPerson(person) {
  return { id: person.id, fullName: person.fullName ?? '', email: person.email ?? '' }
}

function mapTarget(target) {
  return { id: target.id, fullName: target.fullName ?? '', email: target.email ?? '', status: target.status ?? null, exists: Boolean(target.exists) }
}

export async function fetchUserReports({ status = 'all', reason = 'all', q = '', page = 0, size = 20 } = {}) {
  const search = new URLSearchParams({ status, reason, page: String(page), size: String(size) })
  if (q.trim()) search.set('q', q.trim())
  const data = await request(`/api/v1/admin/user-reports?${search.toString()}`)
  return {
    reports: data.content.map((item) => ({
      id: item.id,
      status: item.status,
      reason: item.reason,
      descriptionPreview: item.descriptionPreview ?? '',
      reporter: mapPerson(item.reporter),
      target: mapTarget(item.target),
      createdAt: item.createdAt,
      handledAt: item.handledAt ?? null,
      handledBy: item.handledBy ?? '',
    })),
    page: data.page,
    totalPages: data.totalPages,
    totalElements: data.totalElements,
    statusCounts: data.statusCounts ?? {},
    reasons: data.reasons ?? [],
  }
}

function mapDetail(data) {
  return {
    id: data.id,
    status: data.status,
    reason: data.reason,
    description: data.description ?? '',
    reporter: mapPerson(data.reporter),
    target: mapTarget(data.target),
    roles: data.roles ?? [],
    totalReceived: Number(data.totalReceived ?? 0),
    related: data.related ?? [],
    createdAt: data.createdAt,
    handling: data.handling ?? null,
    canStart: Boolean(data.canStart),
    canConclude: Boolean(data.canConclude),
    canLockAccount: Boolean(data.canLockAccount),
    selfTarget: Boolean(data.selfTarget),
  }
}

export async function fetchUserReport(reportId) {
  return mapDetail(await request(`/api/v1/admin/user-reports/${encodeURIComponent(reportId)}`))
}

export async function startUserReport(reportId) {
  return mapDetail(await request(`/api/v1/admin/user-reports/${encodeURIComponent(reportId)}/start`, { method: 'POST' }))
}

export async function resolveUserReport(reportId, { action, note }) {
  return mapDetail(await request(`/api/v1/admin/user-reports/${encodeURIComponent(reportId)}/resolve`, {
    method: 'POST', body: { action, note: note.trim() },
  }))
}

export async function rejectUserReport(reportId, { note }) {
  return mapDetail(await request(`/api/v1/admin/user-reports/${encodeURIComponent(reportId)}/reject`, {
    method: 'POST', body: { note: note.trim() },
  }))
}
