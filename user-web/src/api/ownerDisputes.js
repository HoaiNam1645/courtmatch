/** 2.3.93 - 2.3.96: chủ sân xem báo cáo về đơn thuộc sân của mình và giải trình. */

import { request } from './client'
import { toEvidence } from './bookingDisputes'

export const OWNER_DISPUTE_TABS = [['all', 'Tất cả'], ['awaiting', 'Chờ giải trình'], ['answered', 'Đã giải trình'], ['closed', 'Đã đóng']]

/** Trạng thái báo cáo dưới góc nhìn chủ sân. */
export const OWNER_STATUS_LABEL = {
  open: 'Chờ giải trình',
  awaiting_owner_response: 'Chờ bạn giải trình',
  reviewing: 'Quản trị viên đang xem xét',
  resolved: 'Đã giải quyết',
  rejected: 'Đã bác bỏ',
  cancelled: 'Người chơi đã huỷ',
}

export const OWNER_ACTOR_LABEL = { player: 'Người chơi', you: 'Bạn', admin: 'Quản trị viên', system: 'Hệ thống' }

function toItem(item) {
  return {
    id: item.id,
    bookingId: item.bookingId,
    bookingCode: item.bookingCode,
    venueId: item.venueId,
    venueName: item.venueName,
    playerName: item.playerName,
    category: item.category,
    categoryLabel: item.categoryLabel,
    status: item.status,
    closed: Boolean(item.closed),
    refundRequested: Boolean(item.refundRequested),
    refundAmount: item.refundAmount == null ? null : Number(item.refundAmount),
    evidenceCount: Number(item.evidenceCount ?? 0),
    createdAt: item.createdAt,
    ownerResponseDeadline: item.ownerResponseDeadline ?? null,
    overdue: Boolean(item.overdue),
    expiringSoon: Boolean(item.expiringSoon),
    resolution: item.resolution ?? null,
  }
}

/** 2.3.93: danh sách báo cáo của các sân của chủ sân; chờ giải trình xếp lên đầu theo hạn gần nhất. */
export async function fetchOwnerDisputes({ venueId = '', status = 'all', page = 0, size = 20 } = {}) {
  const search = new URLSearchParams({ status, page: String(page), size: String(size) })
  if (venueId) search.set('venueId', venueId)
  const data = await request(`/api/v1/owner/booking-disputes?${search.toString()}`)
  return {
    items: data.items.map(toItem),
    page: Number(data.page),
    totalPages: Number(data.totalPages),
    totalElements: Number(data.totalElements),
    counts: { awaiting: Number(data.counts.awaiting), expiringSoon: Number(data.counts.expiringSoon), overdue: Number(data.counts.overdue) },
  }
}

/** 2.3.94: chi tiết báo cáo kèm bằng chứng của người chơi và dòng thời gian. */
export async function fetchOwnerDispute(disputeId) {
  const data = await request(`/api/v1/owner/booking-disputes/${disputeId}`)
  return {
    id: data.id,
    category: data.category,
    categoryLabel: data.categoryLabel,
    status: data.status,
    closed: Boolean(data.closed),
    refundRequested: Boolean(data.refundRequested),
    refundAmount: data.refundAmount == null ? null : Number(data.refundAmount),
    createdAt: data.createdAt,
    ownerResponseDeadline: data.ownerResponseDeadline ?? null,
    overdue: Boolean(data.overdue),
    player: { id: data.player.id, fullName: data.player.fullName, phone: data.player.phone ?? '' },
    booking: {
      id: data.booking.id,
      bookingCode: data.booking.bookingCode,
      venueName: data.booking.venueName,
      courtName: data.booking.courtName ?? '',
      startTime: data.booking.startTime ?? null,
      endTime: data.booking.endTime ?? null,
      payableAmount: Number(data.booking.payableAmount ?? 0),
    },
    timeline: data.timeline.map((entry) => ({ type: entry.type, actor: entry.actor, at: entry.at, text: entry.text ?? '', label: entry.label ?? null, evidences: (entry.evidences ?? []).map(toEvidence) })),
    canRespond: Boolean(data.canRespond),
    draftEvidences: (data.draftEvidences ?? []).map(toEvidence),
  }
}

export const OWNER_RESPONSE_LIMITS = { min: 10, max: 2000, maxImages: 5 }

/** 2.3.95: gửi giải trình (một lần, không sửa). */
export async function respondToDispute(disputeId, content) {
  await request(`/api/v1/owner/booking-disputes/${disputeId}/response`, { method: 'POST', body: { content: content.trim() } })
}

/** 2.3.96: tải lên / xóa một ảnh giải trình trước khi gửi. */
export async function uploadOwnerEvidence(disputeId, file) {
  const body = new FormData()
  body.append('file', file)
  return toEvidence(await request(`/api/v1/owner/booking-disputes/${disputeId}/evidences`, { method: 'POST', body, timeoutMs: 60000 }))
}

export async function deleteOwnerEvidence(disputeId, evidenceId) {
  await request(`/api/v1/owner/booking-disputes/${disputeId}/evidences/${evidenceId}`, { method: 'DELETE' })
}

/** 2.3.97: hoa hồng đang tạm hoãn đối soát do tranh chấp (chỉ đọc). */
export async function fetchSuspendedCommissions() {
  const data = await request('/api/v1/owner/commissions/suspended')
  return {
    totalAmount: Number(data.totalAmount ?? 0),
    items: data.items.map((item) => ({
      bookingId: item.bookingId,
      bookingCode: item.bookingCode,
      venueName: item.venueName,
      commissionAmount: Number(item.commissionAmount),
      voucherDiscount: Number(item.voucherDiscount ?? 0),
      disputeId: item.disputeId ?? null,
      suspendedAt: item.suspendedAt,
    })),
  }
}
