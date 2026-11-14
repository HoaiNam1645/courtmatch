/** 2.3.16 — thanh toán trực tiếp gửi tới các sân của chủ sân (chỉ xem). */

import { request } from './client'

/** Thứ tự các tab trạng thái thanh toán; nhãn lấy từ PAYMENT_STATUS_META. */
export const PAYMENT_TABS = ['all', 'awaiting_payment', 'payer_reported', 'confirmed', 'rejected', 'expired', 'disputed']

export async function fetchOwnerPayments({ venueId = '', status = 'all', page = 0, size = 20 } = {}) {
  const search = new URLSearchParams({ status, page: String(page), size: String(size) })
  if (venueId) search.set('venueId', venueId)
  const data = await request(`/api/v1/owner/payments?${search.toString()}`)
  return {
    payments: data.content.map((item) => ({
      paymentId: item.paymentId,
      bookingId: item.bookingId,
      bookingCode: item.bookingCode,
      venueName: item.venueName,
      playerName: item.playerName,
      playerPhone: item.playerPhone ?? '',
      amount: Number(item.amount ?? 0),
      method: item.method,
      status: item.status,
      bankName: item.bankName ?? '',
      bankAccountNo: item.bankAccountNo ?? '',
      bankAccountName: item.bankAccountName ?? '',
      referenceContent: item.referenceContent ?? '',
      proofUrl: item.proofUrl ?? '',
      payerReportedAt: item.payerReportedAt ?? null,
      confirmedAt: item.confirmedAt ?? null,
      expiredAt: item.expiredAt ?? null,
      rejectionReason: item.rejectionReason ?? '',
      createdAt: item.createdAt,
    })),
    page: data.page,
    totalPages: data.totalPages,
    totalElements: data.totalElements,
    statusCounts: data.statusCounts ?? {},
  }
}

/** Chỉ cho mở liên kết bằng chứng nếu là http(s); tránh `javascript:` và các scheme lạ. */
export function safeProofUrl(url) {
  try {
    const parsed = new URL(url)
    return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? parsed.href : null
  } catch {
    return null
  }
}

function mapPaymentDetail(data) {
  return {
    paymentId: data.paymentId,
    status: data.status,
    method: data.method,
    amount: Number(data.amount ?? 0),
    bankName: data.bankName ?? '',
    bankAccountNo: data.bankAccountNo ?? '',
    bankAccountName: data.bankAccountName ?? '',
    referenceContent: data.referenceContent ?? '',
    proofUrl: data.proofUrl ?? '',
    payerReportedAt: data.payerReportedAt ?? null,
    confirmedAt: data.confirmedAt ?? null,
    rejectedAt: data.rejectedAt ?? null,
    rejectionReason: data.rejectionReason ?? '',
    expiredAt: data.expiredAt ?? null,
    expired: Boolean(data.expired),
    canConfirm: Boolean(data.canConfirm),
    canReject: Boolean(data.canReject),
    booking: {
      id: data.booking.id,
      bookingCode: data.booking.bookingCode,
      status: data.booking.status,
      venueName: data.booking.venueName,
      playerName: data.booking.playerName,
      playerPhone: data.booking.playerPhone ?? '',
      totalAmount: Number(data.booking.totalAmount ?? 0),
      note: data.booking.note ?? '',
      slots: data.booking.slots,
    },
    timeline: data.timeline,
  }
}

/** 2.3.27 — chi tiết xác nhận thanh toán trực tiếp của một booking. */
export async function fetchOwnerPaymentDetail(paymentId) {
  return mapPaymentDetail(await request(`/api/v1/owner/payments/${encodeURIComponent(paymentId)}`))
}

/** 2.3.27 — xác nhận đã thu tiền. */
export async function confirmOwnerPayment(paymentId) {
  return mapPaymentDetail(await request(`/api/v1/owner/payments/${encodeURIComponent(paymentId)}/confirm`, { method: 'POST' }))
}

/** 2.3.27 — từ chối báo đã thanh toán kèm lý do. */
export async function rejectOwnerPayment(paymentId, reason) {
  return mapPaymentDetail(await request(`/api/v1/owner/payments/${encodeURIComponent(paymentId)}/reject`, { method: 'POST', body: { reason: reason.trim() } }))
}
