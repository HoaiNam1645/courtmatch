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
    byVenue: data.byVenue.map((venue) => ({
      venueId: venue.venueId,
      venueName: venue.venueName,
      outstanding: money(venue.outstanding),
      overdue: money(venue.overdue),
      bookings: Number(venue.bookings ?? 0),
    })),
  }
}
