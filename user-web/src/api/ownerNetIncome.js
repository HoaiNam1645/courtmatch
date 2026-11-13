/** 2.3.19 — số tiền thực nhận của từng booking. */

import { request } from './client'

export async function fetchNetIncome({ venueId = '', from = '', to = '', page = 0, size = 20 } = {}) {
  const search = new URLSearchParams({ page: String(page), size: String(size) })
  if (venueId) search.set('venueId', venueId)
  if (from) search.set('from', from)
  if (to) search.set('to', to)
  const data = await request(`/api/v1/owner/net-income?${search.toString()}`)
  const money = (value) => Number(value ?? 0)
  return {
    from: data.from,
    to: data.to,
    items: data.content.map((item) => ({
      paymentId: item.paymentId,
      bookingId: item.bookingId,
      bookingCode: item.bookingCode,
      venueName: item.venueName,
      playerName: item.playerName,
      confirmedAt: item.confirmedAt,
      grossAmount: money(item.grossAmount),
      commissionAmount: money(item.commissionAmount),
      // true = chưa ghi nhận hoa hồng, đang dùng mức ước tính theo chính sách hiện hành
      commissionEstimated: Boolean(item.commissionEstimated),
      commissionStatus: item.commissionStatus ?? null,
      refundedAmount: money(item.refundedAmount),
      pendingRefundAmount: money(item.pendingRefundAmount),
      netAmount: money(item.netAmount),
    })),
    page: data.page,
    totalPages: data.totalPages,
    totalElements: data.totalElements,
    totals: {
      grossAmount: money(data.totals.grossAmount),
      commissionAmount: money(data.totals.commissionAmount),
      refundedAmount: money(data.totals.refundedAmount),
      pendingRefundAmount: money(data.totals.pendingRefundAmount),
      netAmount: money(data.totals.netAmount),
      bookings: Number(data.totals.bookings ?? 0),
    },
  }
}
