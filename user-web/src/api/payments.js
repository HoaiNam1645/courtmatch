/** Thanh toán SePay: 2.1.34 tạo giao dịch, 2.1.35/36 QR, 2.1.39 trạng thái, 2.1.41 thử lại. */

import { request } from './client'

/** Nhãn tiếng Việt cho từng trạng thái giao dịch (2.1.39). */
const STATUS_LABEL = {
  pending: 'Chờ thanh toán',
  paid: 'Đã thanh toán',
  failed: 'Thanh toán thất bại',
  expired: 'Giao dịch hết hạn',
  refunded: 'Đã hoàn tiền',
}

function toUiPayment(payment) {
  return {
    id: payment.id,
    bookingId: payment.bookingId,
    bookingCode: payment.bookingCode,
    provider: payment.provider,
    amount: Number(payment.amount ?? 0),
    status: payment.status,
    statusLabel: STATUS_LABEL[payment.status] ?? payment.status,
    qrUrl: payment.qrUrl ?? null,
    bankName: payment.bankName ?? '',
    bankAccountNo: payment.bankAccountNo ?? '',
    bankAccountName: payment.bankAccountName ?? '',
    transferContent: payment.transferContent ?? '',
    providerTransactionId: payment.providerTransactionId ?? null,
    paidAt: payment.paidAt ?? null,
    expiredAt: payment.expiredAt ?? null,
    // Server tính giúp số giây còn lại; không tự trừ bằng đồng hồ máy người dùng
    // vì đồng hồ lệch sẽ làm đếm ngược sai so với lúc server thật sự cho hết hạn.
    secondsRemaining: Number(payment.secondsRemaining ?? 0),
    createdAt: payment.createdAt,
    updatedAt: payment.updatedAt ?? null,
  }
}

/** 2.1.34 – 2.1.36, 2.1.39 — chi tiết giao dịch, cũng dùng để hỏi lại trạng thái. */
export async function fetchPayment(paymentId) {
  return toUiPayment(await request(`/api/v1/payments/${encodeURIComponent(paymentId)}`))
}

/** 2.1.40 — màn hình đặt sân thành công tra giao dịch theo mã đơn. */
export async function fetchPaymentByBooking(bookingId) {
  return toUiPayment(await request(`/api/v1/payments/by-booking/${encodeURIComponent(bookingId)}`))
}

/** 2.1.41, 2.1.42 — mở lại cửa sổ thanh toán sau khi thất bại hoặc hết hạn. */
export async function retryPayment(paymentId) {
  return toUiPayment(await request(`/api/v1/payments/${encodeURIComponent(paymentId)}/retry`, {
    method: 'POST',
  }))
}
