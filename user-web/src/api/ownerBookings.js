/** 2.3.15 — lịch sử đặt sân của chủ sân. */

import { request } from './client'

/** Nhóm trạng thái ở màn hình lịch sử; khóa khớp backend. */
export const HISTORY_GROUPS = [
  { id: 'all', label: 'Tất cả' },
  { id: 'active', label: 'Đang xử lý' },
  { id: 'completed', label: 'Đã hoàn thành' },
  { id: 'closed', label: 'Đã đóng' },
  { id: 'disputed', label: 'Tranh chấp' },
]

/** Nhãn trạng thái thanh toán trực tiếp; một chỗ duy nhất. */
export const PAYMENT_STATUS_META = {
  awaiting_payment: { label: 'Chờ thanh toán', className: 'bg-orange-100 text-orange-700' },
  payer_reported: { label: 'Chờ chủ sân xác nhận', className: 'bg-amber-100 text-amber-800' },
  confirmed: { label: 'Đã thu tiền', className: 'bg-emerald-100 text-emerald-700' },
  rejected: { label: 'Chưa nhận được tiền', className: 'bg-red-100 text-red-700' },
  expired: { label: 'Hết hạn', className: 'bg-stone-200 text-stone-600' },
  disputed: { label: 'Tranh chấp', className: 'bg-amber-100 text-amber-800' },
  partially_refunded: { label: 'Đã hoàn một phần', className: 'bg-violet-100 text-violet-700' },
  refunded: { label: 'Đã hoàn tiền', className: 'bg-violet-100 text-violet-700' },
}

export function paymentStatusMeta(status) {
  return PAYMENT_STATUS_META[status] ?? { label: status ?? 'Không rõ', className: 'bg-stone-200 text-stone-600' }
}

export const PAYMENT_METHOD_LABEL = { cash: 'Tiền mặt tại sân', owner_bank_transfer: 'Chuyển khoản vào tài khoản chủ sân' }

export async function fetchBookingHistory({ venueId = '', group = 'all', q = '', from = '', to = '', page = 0, size = 20 } = {}) {
  const search = new URLSearchParams({ group, page: String(page), size: String(size) })
  if (venueId) search.set('venueId', venueId)
  if (q.trim()) search.set('q', q.trim())
  if (from) search.set('from', from)
  if (to) search.set('to', to)
  const data = await request(`/api/v1/owner/bookings?${search.toString()}`)
  return {
    bookings: data.content.map((item) => ({
      bookingId: item.bookingId,
      bookingCode: item.bookingCode,
      status: item.status,
      venueName: item.venueName,
      playerName: item.playerName,
      playerPhone: item.playerPhone ?? '',
      slots: item.slots,
      totalAmount: Number(item.totalAmount ?? 0),
      paymentStatus: item.paymentStatus ?? null,
      createdAt: item.createdAt,
    })),
    page: data.page,
    totalPages: data.totalPages,
    totalElements: data.totalElements,
  }
}

export async function fetchBookingDetail(bookingId) {
  const data = await request(`/api/v1/owner/bookings/${encodeURIComponent(bookingId)}`)
  return {
    bookingId: data.bookingId,
    bookingCode: data.bookingCode,
    status: data.status,
    venueName: data.venueName,
    playerName: data.playerName,
    playerPhone: data.playerPhone ?? '',
    slots: data.slots,
    totalAmount: Number(data.totalAmount ?? 0),
    note: data.note ?? '',
    rejectionReason: data.rejectionReason ?? '',
    cancellationReason: data.cancellationReason ?? '',
    payment: data.payment ?? null,
    // 2.3.18 — estimated = chưa ghi nhận, ước tính theo chính sách hiện hành
    fee: data.fee ? { ...data.fee, feeValue: Number(data.fee.feeValue), grossAmount: Number(data.fee.grossAmount), commissionAmount: Number(data.fee.commissionAmount) } : null,
    timeline: data.timeline,
    createdAt: data.createdAt,
  }
}

/** 2.3.22 — lịch sử đối soát hoa hồng của một booking. */
export async function fetchBookingReconciliation(bookingId) {
  const data = await request(`/api/v1/owner/bookings/${encodeURIComponent(bookingId)}/reconciliation`)
  return {
    commission: data.commission
      ? { ...data.commission, grossAmount: Number(data.commission.grossAmount), commissionAmount: Number(data.commission.commissionAmount) }
      : null,
    settlement: data.settlement ?? null,
    events: data.events.map((event) => ({ ...event, amount: Number(event.amount ?? 0), note: event.note ?? '' })),
  }
}

/** Nhãn tiếng Việt của sự kiện đối soát, một chỗ duy nhất. */
export const RECONCILIATION_EVENT_LABEL = {
  recorded: 'Ghi nhận hoa hồng cho booking',
  assigned_to_settlement: 'Đưa vào kỳ đối soát',
  suspended: 'Tạm hoãn do tranh chấp',
  adjusted: 'Hoa hồng được điều chỉnh',
  waived: 'Hoa hồng được miễn',
  settled: 'Đã đối soát xong',
}
