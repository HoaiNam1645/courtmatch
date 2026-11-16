/** 2.3.45 — danh sách kỳ công nợ hoa hồng và chốt kỳ; 2.3.46 — chi tiết một kỳ (quản trị viên). */

import { request } from './client'

const money = (value) => Number(value ?? 0)

function mapRow(item) {
  return {
    id: item.id,
    ownerId: item.ownerId,
    ownerName: item.ownerName,
    ownerEmail: item.ownerEmail,
    periodFrom: item.periodFrom,
    periodTo: item.periodTo,
    commissionTotal: money(item.commissionTotal),
    adjustmentTotal: money(item.adjustmentTotal),
    amountDue: money(item.amountDue),
    amountPaid: money(item.amountPaid),
    remaining: money(item.remaining),
    status: item.status,
    overdue: Boolean(item.overdue),
    bookingCount: Number(item.bookingCount ?? 0),
    pendingReports: Number(item.pendingReports ?? 0),
    createdAt: item.createdAt,
    paidAt: item.paidAt ?? null,
  }
}

export async function fetchAdminSettlements({ q = '', status = 'all', ownerId = '', from = '', to = '', overdue = false, page = 0, size = 20 } = {}) {
  const search = new URLSearchParams({ status, page: String(page), size: String(size) })
  if (q.trim()) search.set('q', q.trim())
  if (ownerId) search.set('ownerId', ownerId)
  if (from) search.set('from', from)
  if (to) search.set('to', to)
  if (overdue) search.set('overdue', 'true')
  const data = await request(`/api/v1/admin/finance/settlements?${search.toString()}`)
  return {
    rows: data.content.map(mapRow),
    page: data.page,
    totalPages: data.totalPages,
    totalElements: data.totalElements,
    totals: { count: Number(data.totals.count), amountDue: money(data.totals.amountDue), amountPaid: money(data.totals.amountPaid), remaining: money(data.totals.remaining) },
    statusCounts: data.statusCounts,
    owners: data.owners,
  }
}

export async function createAdminSettlement({ ownerId, periodFrom, periodTo, note }) {
  return mapRow(await request('/api/v1/admin/finance/settlements', {
    method: 'POST',
    body: { ownerId, periodFrom, periodTo, note: note?.trim() || undefined },
  }))
}

export async function fetchAdminSettlementDetail(settlementId, { page = 0, size = 10 } = {}) {
  const data = await request(`/api/v1/admin/finance/settlements/${encodeURIComponent(settlementId)}?page=${page}&size=${size}`)
  return {
    settlement: mapRow(data.settlement),
    ownerPhone: data.ownerPhone ?? null,
    restrictionLevel: Number(data.restrictionLevel ?? 0),
    owedByCourtly: money(data.owedByCourtly),
    reports: data.reports.map((item) => ({ ...item, amount: money(item.amount), proofUrl: item.proofUrl ?? null, note: item.note ?? null, reviewNote: item.reviewNote ?? null, reviewedByName: item.reviewedByName ?? null, reviewedAt: item.reviewedAt ?? null })),
    bookings: {
      rows: data.bookings.content.map((item) => ({ ...item, grossAmount: money(item.grossAmount), commissionAmount: money(item.commissionAmount), overdue: Boolean(item.overdue), unconfirmedPayment: Boolean(item.unconfirmedPayment) })),
      page: data.bookings.page,
      totalPages: data.bookings.totalPages,
      totalElements: data.bookings.totalElements,
    },
    history: data.history,
  }
}
