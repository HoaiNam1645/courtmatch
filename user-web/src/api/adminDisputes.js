/** 2.3.98 - 2.3.102: quản trị viên xử lý báo cáo về đơn đặt sân. */

import { request } from './client'
import { toEvidence } from './bookingDisputes'

export const ADMIN_DISPUTE_TABS = [
  ['pending', 'Chờ xử lý'],
  ['awaiting', 'Chờ chủ sân giải trình'],
  ['refunding', 'Đang hoàn tiền'],
  ['resolved', 'Đã giải quyết'],
  ['rejected', 'Đã bác bỏ'],
  ['cancelled', 'Đã huỷ'],
  ['all', 'Tất cả'],
]

export const ADMIN_DISPUTE_STATUS = {
  open: { label: 'Chờ giải trình', className: 'bg-amber-100 text-amber-800' },
  awaiting_owner_response: { label: 'Chờ chủ sân giải trình', className: 'bg-amber-100 text-amber-800' },
  reviewing: { label: 'Chờ xử lý', className: 'bg-violet-100 text-violet-700' },
  resolved: { label: 'Đã giải quyết', className: 'bg-emerald-100 text-emerald-700' },
  rejected: { label: 'Đã bác bỏ', className: 'bg-red-100 text-red-700' },
  cancelled: { label: 'Người chơi đã huỷ', className: 'bg-stone-200 text-stone-600' },
}

export const DISPUTE_CATEGORY_OPTIONS = [
  ['venue_closed', 'Sân đóng cửa'],
  ['venue_mismatch', 'Sân không đúng mô tả'],
  ['owner_cancelled', 'Chủ sân tự huỷ'],
  ['extra_charge', 'Thu thêm tiền'],
  ['other', 'Vấn đề khác'],
]

export const ADMIN_ACTOR_LABEL = { player: 'Người chơi', owner: 'Chủ sân', admin: 'Quản trị viên', system: 'Hệ thống' }

const num = (value) => (value == null ? null : Number(value))

export async function fetchAdminDisputes({ status = 'pending', q = '', category = 'all', refund = 'all', venueId = '', from = '', to = '', page = 0, size = 20 } = {}) {
  const search = new URLSearchParams({ status, category, refund, page: String(page), size: String(size) })
  if (venueId) search.set('venueId', venueId)
  if (q.trim()) search.set('q', q.trim())
  if (from) search.set('from', from)
  if (to) search.set('to', to)
  const data = await request(`/api/v1/admin/booking-disputes?${search.toString()}`)
  return {
    items: data.items.map((item) => ({ ...item, refundAmount: num(item.refundAmount), priorVenueDisputes: Number(item.priorVenueDisputes) })),
    page: data.page,
    totalPages: data.totalPages,
    totalElements: data.totalElements,
    counts: data.counts ?? {},
  }
}

function mapDetail(data) {
  return {
    ...data,
    refundAmount: num(data.refundAmount),
    fullAmount: num(data.fullAmount),
    halfAmount: num(data.halfAmount),
    money: {
      payable: Number(data.money.payable),
      confirmedPaid: Number(data.money.confirmedPaid),
      refundedBefore: Number(data.money.refundedBefore),
      refundable: Number(data.money.refundable),
      payments: data.money.payments.map((payment) => ({ ...payment, amount: Number(payment.amount) })),
    },
    commission: data.commission ? { ...data.commission, amount: Number(data.commission.amount), voucherDiscount: Number(data.commission.voucherDiscount ?? 0) } : null,
    timeline: data.timeline.map((entry) => ({ type: entry.type, actor: entry.actor, at: entry.at, text: entry.text ?? '', label: entry.label ?? null, evidences: (entry.evidences ?? []).map(toEvidence) })),
    canDecide: Boolean(data.canDecide),
    canRefund: Boolean(data.canRefund),
  }
}

export async function fetchAdminDispute(disputeId) {
  return mapDetail(await request(`/api/v1/admin/booking-disputes/${disputeId}`))
}

/** 2.3.100 - 2.3.101: quyết định hoàn tiền (toàn phần / một nửa) kèm ghi chú bắt buộc. */
export async function decideRefund(disputeId, kind, note) {
  return mapDetail(await request(`/api/v1/admin/booking-disputes/${disputeId}/${kind === 'reject' ? 'reject' : `refund-${kind}`}`, { method: 'POST', body: { note: note.trim() } }))
}

export const NOTE_LIMITS = { min: 5, max: 1000 }

export const VENUE_STAT_SORTS = [['lost', 'Số lần xử thua'], ['total', 'Tổng báo cáo'], ['rate', 'Tỷ lệ xử thua'], ['refund', 'Tiền hoàn']]

/** 2.3.103: thống kê báo cáo theo từng sân. */
export async function fetchVenueStats({ from = '', to = '', q = '', sort = 'lost', page = 0, size = 20 } = {}) {
  const search = new URLSearchParams({ sort, page: String(page), size: String(size) })
  if (q.trim()) search.set('q', q.trim())
  if (from) search.set('from', from)
  if (to) search.set('to', to)
  const data = await request(`/api/v1/admin/booking-disputes/venue-stats?${search.toString()}`)
  return {
    items: data.items.map((item) => ({ ...item, refundAmount: Number(item.refundAmount ?? 0), lostRate: Number(item.lostRate ?? 0) })),
    page: data.page,
    totalPages: data.totalPages,
    totalElements: data.totalElements,
    suspendThreshold: data.suspendThreshold,
    suspendWindowDays: data.suspendWindowDays,
  }
}

/** 2.3.104: bật lại sân bị tự động tạm ngưng, ghi chú bắt buộc. */
export async function reactivateVenue(venueId, note) {
  await request(`/api/v1/admin/venues/${venueId}/reactivate`, { method: 'POST', body: { note: note.trim() } })
}
