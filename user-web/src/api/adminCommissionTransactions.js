/** 2.3.43 — danh sách giao dịch đối soát; 2.3.44 — chi tiết đối soát của một booking. */

import { request } from './client'

const money = (value) => Number(value ?? 0)

export async function fetchCommissionTransactions({ q = '', status = 'all', venueId = '', settlementId = '', from = '', to = '', overdue = false, page = 0, size = 20 } = {}) {
  const search = new URLSearchParams({ status, page: String(page), size: String(size) })
  if (q.trim()) search.set('q', q.trim())
  if (venueId) search.set('venueId', venueId)
  if (settlementId) search.set('settlementId', settlementId)
  if (from) search.set('from', from)
  if (to) search.set('to', to)
  if (overdue) search.set('overdue', 'true')
  const data = await request(`/api/v1/admin/finance/commission-transactions?${search.toString()}`)
  return {
    rows: data.content.map((row) => ({
      commissionId: row.commissionId,
      bookingId: row.bookingId,
      bookingCode: row.bookingCode,
      venueName: row.venueName,
      ownerName: row.ownerName,
      grossAmount: money(row.grossAmount),
      commissionAmount: money(row.commissionAmount),
      status: row.status,
      calculatedAt: row.calculatedAt,
      dueAt: row.dueAt,
      overdue: Boolean(row.overdue),
      settlement: row.settlement ? { id: row.settlement.id, periodFrom: row.settlement.periodFrom, periodTo: row.settlement.periodTo, status: row.settlement.status } : null,
      settledAt: row.settledAt ?? null,
    })),
    page: data.page,
    totalPages: data.totalPages,
    totalElements: data.totalElements,
    totals: { count: Number(data.totals.count), grossAmount: money(data.totals.grossAmount), commissionAmount: money(data.totals.commissionAmount) },
    venues: data.venues,
    settlements: data.settlements.map((item) => ({ id: item.id, ownerName: item.ownerName, periodFrom: item.periodFrom, periodTo: item.periodTo, status: item.status })),
  }
}

export const REFUND_STATUS_LABEL = {
  requested: 'Đã yêu cầu', approved: 'Đã duyệt', awaiting_owner_refund: 'Chờ chủ sân hoàn', owner_reported_refunded: 'Chủ sân báo đã hoàn',
  confirmed: 'Đã xác nhận', rejected: 'Từ chối', failed: 'Thất bại',
}
export const DISPUTE_STATUS_LABEL = {
  open: 'Mới mở', awaiting_owner_response: 'Chờ chủ sân phản hồi', reviewing: 'Đang xem xét', resolved: 'Đã giải quyết', rejected: 'Bị từ chối', cancelled: 'Đã hủy',
}
export const HISTORY_ACTION_LABEL = {
  'commission.adjust': 'Điều chỉnh hoa hồng',
  'commission.waive': 'Miễn hoa hồng',
  'commission.refund_adjust': 'Điều chỉnh theo hoàn tiền',
  'commission.courtly_owed.clear': 'Bù trừ khoản Courtly nợ',
  'settlement.create': 'Chốt kỳ công nợ',
  'settlement.payment.confirm': 'Xác nhận thanh toán công nợ',
  'settlement.payment.reject': 'Từ chối báo cáo thanh toán',
  'settlement.payment.record': 'Ghi nhận giao dịch đối soát',
  'settlement.adjust': 'Điều chỉnh công nợ',
}

function mapDetail(data) {
  const c = data.commission
  return {
    commission: {
      id: c.id, status: c.status, feeType: c.feeType, feeValue: money(c.feeValue), feePolicyName: c.feePolicyName ?? null,
      grossAmount: money(c.grossAmount), baseCommission: money(c.baseCommission), voucherDiscount: money(c.voucherDiscount),
      commissionAmount: money(c.commissionAmount), courtlyOwed: money(c.courtlyOwed), courtlyOwedClearedAt: c.courtlyOwedClearedAt ?? null,
      calculatedAt: c.calculatedAt, dueAt: c.dueAt, overdue: Boolean(c.overdue), settledAt: c.settledAt ?? null,
    },
    booking: {
      ...data.booking,
      totalAmount: money(data.booking.totalAmount), discountAmount: money(data.booking.discountAmount), payableAmount: money(data.booking.payableAmount),
      voucherCode: data.booking.voucherCode ?? null,
    },
    owner: { ...data.owner, phone: data.owner.phone ?? null },
    payment: data.payment ? { ...data.payment, amount: money(data.payment.amount) } : null,
    unconfirmedPayment: Boolean(data.unconfirmedPayment),
    settlement: data.settlement ? { ...data.settlement, amountDue: money(data.settlement.amountDue), amountPaid: money(data.settlement.amountPaid) } : null,
    refunds: data.refunds.map((item) => ({ ...item, amount: money(item.amount) })),
    disputes: data.disputes,
    history: data.history,
  }
}

export async function fetchCommissionDetail(commissionId) {
  return mapDetail(await request(`/api/v1/admin/finance/commission-transactions/${commissionId}`))
}

/** 2.3.51 — điều chỉnh hoa hồng của một booking (số tiền chủ sân phải trả) kèm lý do; trả về chi tiết mới. */
export async function adjustCommission(commissionId, { newAmount, reason }) {
  return mapDetail(await request(`/api/v1/admin/finance/commission-transactions/${commissionId}/adjustments`, {
    method: 'POST',
    body: { newAmount: Number(newAmount), reason: reason.trim() },
  }))
}
