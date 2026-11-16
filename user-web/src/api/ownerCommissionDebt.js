/** 2.3.20 — tổng công nợ hoa hồng của chủ sân với Courtly. */

import { request } from './client'

export async function fetchCommissionDebt() {
  const data = await request('/api/v1/owner/commission-debt')
  const money = (value) => Number(value ?? 0)
  return {
    asOf: data.asOf,
    totalOutstanding: money(data.totalOutstanding),
    outstandingCount: Number(data.outstandingCount ?? 0),
    overdueAmount: money(data.overdueAmount),
    overdueCount: Number(data.overdueCount ?? 0),
    upcomingAmount: money(data.upcomingAmount),
    upcomingCount: Number(data.upcomingCount ?? 0),
    suspendedAmount: money(data.suspendedAmount),
    suspendedCount: Number(data.suspendedCount ?? 0),
    settledAmount: money(data.settledAmount),
    oldestOverdueAt: data.oldestOverdueAt ?? null,
    nextDueAt: data.nextDueAt ?? null,
    restriction: {
      level: Number(data.restriction?.level ?? 0),
      byAmount: Boolean(data.restriction?.byAmount),
      byDays: Boolean(data.restriction?.byDays),
      overdueAmount: money(data.restriction?.overdueAmount),
      overdueDays: Number(data.restriction?.overdueDays ?? 0),
      levels: (data.restriction?.levels ?? []).map((item) => ({
        level: Number(item.level),
        enabled: Boolean(item.enabled),
        minOverdueAmount: item.minOverdueAmount == null ? null : Number(item.minOverdueAmount),
        minOverdueDays: item.minOverdueDays == null ? null : Number(item.minOverdueDays),
      })),
    },
    courtlyOwed: {
      amount: money(data.courtlyOwed?.amount),
      count: Number(data.courtlyOwed?.count ?? 0),
      items: (data.courtlyOwed?.items ?? []).map((item) => ({
        bookingId: item.bookingId,
        bookingCode: item.bookingCode,
        venueName: item.venueName,
        grossAmount: money(item.grossAmount),
        voucherDiscount: money(item.voucherDiscount),
        owedAmount: money(item.owedAmount),
        calculatedAt: item.calculatedAt,
      })),
    },
    byVenue: data.byVenue.map((venue) => ({
      venueId: venue.venueId,
      venueName: venue.venueName,
      outstanding: money(venue.outstanding),
      overdue: money(venue.overdue),
      bookings: Number(venue.bookings ?? 0),
    })),
  }
}

/** 2.3.21 — các booking cấu thành công nợ; bucket: all | overdue | upcoming. */
export async function fetchDebtBookings({ venueId = '', bucket = 'all', page = 0, size = 10 } = {}) {
  const search = new URLSearchParams({ bucket, page: String(page), size: String(size) })
  if (venueId) search.set('venueId', venueId)
  const data = await request(`/api/v1/owner/commission-debt/bookings?${search.toString()}`)
  return {
    bookings: data.content.map((item) => ({
      bookingId: item.bookingId,
      bookingCode: item.bookingCode,
      venueName: item.venueName,
      playerName: item.playerName,
      grossAmount: Number(item.grossAmount ?? 0),
      commissionAmount: Number(item.commissionAmount ?? 0),
      status: item.status,
      overdue: Boolean(item.overdue),
      calculatedAt: item.calculatedAt,
      dueAt: item.dueAt ?? null,
    })),
    page: data.page,
    totalPages: data.totalPages,
    totalElements: data.totalElements,
    totalAmount: Number(data.totalAmount ?? 0),
  }
}
