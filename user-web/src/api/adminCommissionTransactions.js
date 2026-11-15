/** 2.3.43 — danh sách giao dịch đối soát (hoa hồng từng booking) cho quản trị viên. */

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
