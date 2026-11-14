/** 2.3.23 — các kỳ đối soát hoa hồng giữa chủ sân và Courtly. */

import { request } from './client'

export const SETTLEMENT_STATUS_META = {
  open: { label: 'Đang mở', className: 'bg-stone-200 text-stone-700' },
  pending_review: { label: 'Chờ Courtly xác nhận', className: 'bg-sky-100 text-sky-700' },
  partial: { label: 'Đã trả một phần', className: 'bg-amber-100 text-amber-800' },
  paid: { label: 'Đã thanh toán', className: 'bg-emerald-100 text-emerald-700' },
  overdue: { label: 'Quá hạn', className: 'bg-red-100 text-red-700' },
  disputed: { label: 'Đang tranh chấp', className: 'bg-violet-100 text-violet-700' },
}

export const SETTLEMENT_TABS = ['all', 'open', 'pending_review', 'partial', 'overdue', 'paid', 'disputed']

export function settlementStatusMeta(status) {
  return SETTLEMENT_STATUS_META[status] ?? { label: status ?? 'Không rõ', className: 'bg-stone-200 text-stone-600' }
}

function mapSummary(item) {
  const money = (value) => Number(value ?? 0)
  return {
    id: item.id,
    periodFrom: item.periodFrom,
    periodTo: item.periodTo,
    openingOutstanding: money(item.openingOutstanding),
    commissionTotal: money(item.commissionTotal),
    adjustmentTotal: money(item.adjustmentTotal),
    amountDue: money(item.amountDue),
    amountPaid: money(item.amountPaid),
    remainingAmount: money(item.remainingAmount),
    status: item.status,
    bookingCount: Number(item.bookingCount ?? 0),
    ownerSubmittedAt: item.ownerSubmittedAt ?? null,
    reviewedAt: item.reviewedAt ?? null,
    paidAt: item.paidAt ?? null,
    note: item.note ?? '',
    createdAt: item.createdAt,
  }
}

export async function fetchSettlements({ status = 'all', page = 0, size = 10 } = {}) {
  const search = new URLSearchParams({ status, page: String(page), size: String(size) })
  const data = await request(`/api/v1/owner/settlements?${search.toString()}`)
  return {
    settlements: data.content.map(mapSummary),
    page: data.page,
    totalPages: data.totalPages,
    totalElements: data.totalElements,
    statusCounts: data.statusCounts,
  }
}

export async function fetchSettlementDetail(settlementId, { page = 0, size = 20 } = {}) {
  const search = new URLSearchParams({ page: String(page), size: String(size) })
  const data = await request(`/api/v1/owner/settlements/${encodeURIComponent(settlementId)}?${search.toString()}`)
  return {
    settlement: mapSummary(data.settlement),
    bookings: data.bookings.map((item) => ({
      bookingId: item.bookingId,
      bookingCode: item.bookingCode,
      venueName: item.venueName,
      playerName: item.playerName,
      grossAmount: Number(item.grossAmount ?? 0),
      commissionAmount: Number(item.commissionAmount ?? 0),
      status: item.status,
      calculatedAt: item.calculatedAt,
    })),
    page: data.page,
    totalPages: data.totalPages,
    totalBookings: data.totalBookings,
  }
}
