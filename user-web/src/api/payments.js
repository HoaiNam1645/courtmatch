/** Direct payment to the venue owner; Courtly only tracks confirmation state. */

import { request } from './client'

const STATUS_LABEL = {
  awaiting_payment: 'Chờ thanh toán',
  payer_reported: 'Chờ chủ sân xác nhận',
  confirmed: 'Chủ sân đã xác nhận',
  rejected: 'Chủ sân chưa nhận được tiền',
  expired: 'Đã hết hạn',
  disputed: 'Đang tranh chấp',
  partially_refunded: 'Đã hoàn một phần',
  refunded: 'Đã hoàn tiền',
}

function toUiPayment(payment) {
  return {
    id: payment.id,
    bookingId: payment.bookingId,
    bookingCode: payment.bookingCode,
    method: payment.method,
    amount: Number(payment.amount ?? 0),
    status: payment.status,
    statusLabel: STATUS_LABEL[payment.status] ?? payment.status,
    bankName: payment.bankName ?? '',
    bankAccountNo: payment.bankAccountNo ?? '',
    bankAccountName: payment.bankAccountName ?? '',
    referenceContent: payment.referenceContent ?? '',
    proofUrl: payment.proofUrl ?? '',
    payerReportedAt: payment.payerReportedAt ?? null,
    confirmedAt: payment.confirmedAt ?? null,
    rejectionReason: payment.rejectionReason ?? '',
    expiredAt: payment.expiredAt ?? null,
    secondsRemaining: Number(payment.secondsRemaining ?? 0),
    createdAt: payment.createdAt,
    updatedAt: payment.updatedAt ?? null,
  }
}

export async function fetchPayment(paymentId) {
  return toUiPayment(await request(`/api/v1/payments/${encodeURIComponent(paymentId)}`))
}

export async function fetchPaymentByBooking(bookingId) {
  return toUiPayment(await request(`/api/v1/payments/by-booking/${encodeURIComponent(bookingId)}`))
}

export async function reportPayment(paymentId, { method, proofUrl = '' }) {
  return toUiPayment(await request(`/api/v1/payments/${encodeURIComponent(paymentId)}/report-paid`, {
    method: 'POST',
    body: { method, proofUrl: proofUrl.trim() || null },
  }))
}
