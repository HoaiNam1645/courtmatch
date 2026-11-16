/** 2.3.47 — xác nhận chủ sân đã thanh toán công nợ; 2.3.50 — lịch sử thanh toán hoa hồng (quản trị viên). */

import { request } from './client'

const money = (value) => Number(value ?? 0)

export function mapPayment(item) {
  return {
    id: item.id,
    ownerId: item.ownerId,
    ownerName: item.ownerName,
    ownerEmail: item.ownerEmail,
    settlement: item.settlement ? { id: item.settlement.id, periodFrom: item.settlement.periodFrom, periodTo: item.settlement.periodTo, status: item.settlement.status } : null,
    amount: money(item.amount),
    courtlyOffsetAmount: money(item.courtlyOffsetAmount),
    paidOn: item.paidOn,
    referenceCode: item.referenceCode,
    proofUrl: item.proofUrl ?? null,
    note: item.note ?? null,
    status: item.status,
    reviewNote: item.reviewNote ?? null,
    reviewedByName: item.reviewedByName ?? null,
    reviewedAt: item.reviewedAt ?? null,
    createdAt: item.createdAt,
    recordedByAdmin: Boolean(item.recordedByAdmin),
  }
}

export async function fetchAdminPayments({ q = '', status = 'all', ownerId = '', settlementId = '', from = '', to = '', page = 0, size = 20 } = {}) {
  const search = new URLSearchParams({ status, page: String(page), size: String(size) })
  if (q.trim()) search.set('q', q.trim())
  if (ownerId) search.set('ownerId', ownerId)
  if (settlementId) search.set('settlementId', settlementId)
  if (from) search.set('from', from)
  if (to) search.set('to', to)
  const data = await request(`/api/v1/admin/finance/commission-payments?${search.toString()}`)
  return {
    rows: data.content.map(mapPayment),
    page: data.page,
    totalPages: data.totalPages,
    totalElements: data.totalElements,
    totals: { count: Number(data.totals.count), amount: money(data.totals.amount) },
    statusCounts: data.statusCounts,
    owners: data.owners,
    ownerSummary: data.ownerSummary ? {
      ownerId: data.ownerSummary.ownerId,
      ownerName: data.ownerSummary.ownerName,
      confirmedAmount: money(data.ownerSummary.confirmedAmount),
      pendingAmount: money(data.ownerSummary.pendingAmount),
      outstanding: money(data.ownerSummary.outstanding),
      credit: money(data.ownerSummary.credit),
      owedByCourtly: money(data.ownerSummary.owedByCourtly),
      restrictionLevel: Number(data.ownerSummary.restrictionLevel ?? 0),
    } : null,
  }
}

export async function confirmAdminPayment(reportId, note) {
  return mapPayment(await request(`/api/v1/admin/finance/commission-payments/${encodeURIComponent(reportId)}/confirm`, {
    method: 'POST',
    body: { note: note?.trim() || undefined },
  }))
}

export async function rejectAdminPayment(reportId, reason) {
  return mapPayment(await request(`/api/v1/admin/finance/commission-payments/${encodeURIComponent(reportId)}/reject`, {
    method: 'POST',
    body: { reason: reason.trim() },
  }))
}

export async function recordAdminPayment({ ownerId, settlementId, amount, paidOn, referenceCode, proofUrl, note }) {
  return mapPayment(await request('/api/v1/admin/finance/commission-payments', {
    method: 'POST',
    body: {
      ownerId,
      settlementId: settlementId || undefined,
      amount: Number(amount),
      paidOn,
      referenceCode: referenceCode.trim(),
      proofUrl: proofUrl?.trim() || undefined,
      note: note?.trim() || undefined,
    },
  }))
}

export const PAYMENT_TABS = ['all', 'pending_review', 'confirmed', 'rejected']
