/** B4 — voucher giảm giá của người chơi (Courtly chịu phần giảm). */

import { request } from './client'

/** Nhãn điều kiện chưa đạt do backend trả về. */
export const CONDITION_LABEL = {
  elo: 'Elo',
  booking_matches: 'trận có đặt sân',
  active_weeks: 'tuần hoạt động liên tiếp',
}

export async function fetchMyVouchers() {
  const data = await request('/api/v1/vouchers')
  return data.map((item) => ({
    id: item.id,
    code: item.code,
    name: item.name,
    description: item.description ?? '',
    discountPercent: Number(item.discountPercent),
    maxDiscountAmount: item.maxDiscountAmount == null ? null : Number(item.maxDiscountAmount),
    validUntil: item.validUntil,
    remaining: item.remaining == null ? null : Number(item.remaining),
    status: item.status,
    eligible: Boolean(item.eligible),
    unmet: (item.unmet ?? []).map((unmet) => ({ code: unmet.code, required: Number(unmet.required), actual: Number(unmet.actual) })),
  }))
}

/** Xem trước mức giảm cho lựa chọn đặt sân; server tính số tiền. */
export async function previewVoucher({ voucherCode, courtId, startTime, durationMinutes }) {
  const data = await request('/api/v1/vouchers/preview', {
    method: 'POST',
    body: { voucherCode, courtId, startTime, durationMinutes },
  })
  return {
    code: data.code,
    name: data.name,
    discountPercent: Number(data.discountPercent),
    originalAmount: Number(data.originalAmount),
    discountAmount: Number(data.discountAmount),
    payableAmount: Number(data.payableAmount),
  }
}

/** "Giảm 10% (tối đa 30.000đ)". */
export function discountLabel(voucher, formatMoney) {
  const percent = `${voucher.discountPercent.toLocaleString('vi-VN')}%`
  return voucher.maxDiscountAmount == null ? `Giảm ${percent}` : `Giảm ${percent} (tối đa ${formatMoney(voucher.maxDiscountAmount)})`
}

/** Điều kiện chưa đạt, ví dụ "Cần Elo 1.200 (bạn có 1.000)". */
export function unmetLabel(unmet) {
  return `Cần ${CONDITION_LABEL[unmet.code] ?? unmet.code} ${unmet.required.toLocaleString('vi-VN')} (bạn có ${unmet.actual.toLocaleString('vi-VN')})`
}
