/** 2.3.61 — lịch sử thông báo; 2.3.62 — tùy chọn nhận thông báo (mọi vai trò, dữ liệu của chính mình). */

import { request } from './client'

/** Nhãn và màu theo loại thông báo; một chỗ duy nhất cho chuông, trang lịch sử và bộ lọc. */
export const NOTIFICATION_TYPE_META = {
  booking_confirmed: { label: 'Xác nhận đặt sân', className: 'bg-emerald-100 text-emerald-700' },
  booking_cancelled: { label: 'Hủy đặt sân', className: 'bg-red-100 text-red-700' },
  payment_status: { label: 'Thanh toán', className: 'bg-sky-100 text-sky-700' },
  booking_reminder: { label: 'Nhắc đặt sân', className: 'bg-amber-100 text-amber-800' },
  partner_request: { label: 'Ghép cặp', className: 'bg-violet-100 text-violet-700' },
  partner_request_response: { label: 'Phản hồi ghép cặp', className: 'bg-violet-100 text-violet-700' },
  partner_request_expired: { label: 'Ghép cặp hết hạn', className: 'bg-stone-200 text-stone-600' },
  venue_review_replied: { label: 'Trả lời đánh giá', className: 'bg-amber-100 text-amber-800' },
  venue_review_hidden: { label: 'Đánh giá bị ẩn', className: 'bg-red-100 text-red-700' },
  booking_dispute_opened: { label: 'Báo cáo đơn', className: 'bg-amber-100 text-amber-800' },
  match_scheduled: { label: 'Trận đấu', className: 'bg-lime-100 text-lime-800' },
  match_reminder: { label: 'Nhắc trận đấu', className: 'bg-amber-100 text-amber-800' },
  debt_restriction: { label: 'Công nợ', className: 'bg-red-100 text-red-700' },
}

export const NOTIFICATION_FILTERS = ['all', 'booking_confirmed', 'booking_cancelled', 'payment_status', 'booking_reminder',
  'partner_request', 'partner_request_response', 'partner_request_expired', 'venue_review_replied', 'venue_review_hidden', 'booking_dispute_opened', 'match_scheduled', 'match_reminder', 'debt_restriction']

export function notificationTypeMeta(type) {
  return NOTIFICATION_TYPE_META[type] ?? { label: type ?? 'Thông báo', className: 'bg-stone-200 text-stone-600' }
}

function mapItem(item) {
  return {
    id: item.id,
    type: item.type,
    title: item.title,
    content: item.content ?? '',
    route: typeof item.data?.route === 'string' ? item.data.route : null,
    read: Boolean(item.read),
    readAt: item.readAt ?? null,
    createdAt: item.createdAt,
  }
}

export async function fetchNotifications({ type = 'all', unreadOnly = false, page = 0, size = 20 } = {}) {
  const search = new URLSearchParams({ type, unreadOnly: String(unreadOnly), page: String(page), size: String(size) })
  const data = await request(`/api/v1/notifications?${search.toString()}`)
  return {
    items: data.page.content.map(mapItem),
    page: data.page.page,
    totalPages: data.page.totalPages,
    totalElements: data.page.totalElements,
    unreadCount: Number(data.unreadCount ?? 0),
  }
}

export async function fetchUnreadCount() {
  const data = await request('/api/v1/notifications/unread-count')
  return Number(data.unreadCount ?? 0)
}

export async function markNotificationRead(notificationId) {
  return mapItem(await request(`/api/v1/notifications/${encodeURIComponent(notificationId)}/read`, { method: 'POST' }))
}

export async function markAllNotificationsRead(type = 'all') {
  const data = await request(`/api/v1/notifications/read-all?type=${encodeURIComponent(type)}`, { method: 'POST' })
  return { updated: Number(data.updated ?? 0), unreadCount: Number(data.unreadCount ?? 0) }
}

export async function fetchNotificationPreferences() {
  const data = await request('/api/v1/notifications/preferences')
  return data.preferences.map((item) => ({
    type: item.type,
    label: item.label,
    description: item.description,
    inAppEnabled: Boolean(item.inAppEnabled),
    emailEnabled: Boolean(item.emailEnabled),
    pushSupported: Boolean(item.pushSupported),
    mandatory: Boolean(item.mandatory),
  }))
}

export async function saveNotificationPreferences(preferences) {
  const data = await request('/api/v1/notifications/preferences', {
    method: 'PUT',
    body: { preferences: preferences.map((item) => ({ type: item.type, inAppEnabled: item.inAppEnabled, emailEnabled: item.emailEnabled })) },
  })
  return data.preferences.map((item) => ({
    type: item.type, label: item.label, description: item.description, inAppEnabled: Boolean(item.inAppEnabled),
    emailEnabled: Boolean(item.emailEnabled), pushSupported: Boolean(item.pushSupported), mandatory: Boolean(item.mandatory),
  }))
}
