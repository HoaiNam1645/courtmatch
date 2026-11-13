/** 2.3.12 — các yêu cầu đặt sân gửi tới chủ sân (chỉ xem). */

import { request } from './client'

/** Nhãn trạng thái booking; một chỗ duy nhất cho khu quản lý chủ sân. */
export const BOOKING_STATUS_META = {
  pending_owner_confirmation: { label: 'Chờ chủ sân duyệt', className: 'bg-amber-100 text-amber-800' },
  awaiting_payment: { label: 'Chờ thanh toán', className: 'bg-orange-100 text-orange-700' },
  confirmed: { label: 'Đã xác nhận', className: 'bg-emerald-100 text-emerald-700' },
  rejected: { label: 'Đã từ chối', className: 'bg-red-100 text-red-700' },
  cancelled: { label: 'Đã hủy', className: 'bg-red-100 text-red-700' },
  completed: { label: 'Đã hoàn thành', className: 'bg-sky-100 text-sky-700' },
  expired: { label: 'Đã hết hạn', className: 'bg-stone-200 text-stone-600' },
  disputed: { label: 'Đang tranh chấp', className: 'bg-amber-100 text-amber-800' },
  refunded: { label: 'Đã hoàn tiền', className: 'bg-violet-100 text-violet-700' },
}

export function bookingStatusMeta(status) {
  return BOOKING_STATUS_META[status] ?? { label: status ?? 'Không rõ', className: 'bg-stone-200 text-stone-600' }
}

export async function fetchBookingRequests({ venueId = '', status = 'pending_owner_confirmation', date = '', page = 0, size = 20 } = {}) {
  const search = new URLSearchParams({ status, page: String(page), size: String(size) })
  if (venueId) search.set('venueId', venueId)
  if (date) search.set('date', date)
  const data = await request(`/api/v1/owner/booking-requests?${search.toString()}`)
  return {
    requests: data.content.map((item) => ({
      bookingId: item.bookingId,
      bookingCode: item.bookingCode,
      status: item.status,
      venueId: item.venueId,
      venueName: item.venueName,
      playerName: item.playerName,
      playerPhone: item.playerPhone ?? '',
      slots: item.slots,
      totalAmount: Number(item.totalAmount ?? 0),
      note: item.note ?? '',
      respondBy: item.respondBy ?? null,
      secondsRemaining: Number(item.secondsRemaining ?? 0),
      expiringSoon: Boolean(item.expiringSoon),
      createdAt: item.createdAt,
      ownerRespondedAt: item.ownerRespondedAt ?? null,
      ownerRespondedByName: item.ownerRespondedByName ?? '',
      paymentExpiresAt: item.paymentExpiresAt ?? null,
      rejectionReason: item.rejectionReason ?? '',
    })),
    page: data.page,
    totalPages: data.totalPages,
    totalElements: data.totalElements,
    awaitingCount: Number(data.awaitingCount ?? 0),
    expiringSoonCount: Number(data.expiringSoonCount ?? 0),
  }
}

function toUiRequest(item) {
  return {
    bookingId: item.bookingId,
    bookingCode: item.bookingCode,
    status: item.status,
    ownerRespondedAt: item.ownerRespondedAt ?? null,
    ownerRespondedByName: item.ownerRespondedByName ?? '',
    paymentExpiresAt: item.paymentExpiresAt ?? null,
    rejectionReason: item.rejectionReason ?? '',
  }
}

/** 2.3.13 — xác nhận yêu cầu: booking chuyển sang chờ người chơi thanh toán. */
export async function acceptBookingRequest(bookingId) {
  return toUiRequest(await request(`/api/v1/owner/booking-requests/${encodeURIComponent(bookingId)}/accept`, { method: 'POST' }))
}

/** 2.3.14 — danh mục lý do từ chối lấy từ backend. */
export async function fetchRejectReasons() {
  const data = await request('/api/v1/owner/booking-requests/reject-reasons')
  return data.reasons.map((reason) => ({ code: reason.code, label: reason.label, noteRequired: Boolean(reason.noteRequired) }))
}

/** 2.3.14 — từ chối yêu cầu kèm lý do trong danh mục; khung giờ được giải phóng ngay. */
export async function rejectBookingRequest(bookingId, { reasonCode, note }) {
  return toUiRequest(await request(`/api/v1/owner/booking-requests/${encodeURIComponent(bookingId)}/reject`, {
    method: 'POST',
    body: { reasonCode, note: note?.trim() || null },
  }))
}
