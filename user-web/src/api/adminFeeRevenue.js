/** 2.3.42 — doanh thu phí nền tảng (hoa hồng Courtly thu từ chủ sân). */

import { request } from './client'

const money = (value) => Number(value ?? 0)

function mapPoint(point) {
  return {
    period: point.period,
    bookingCount: Number(point.bookingCount),
    grossAmount: money(point.grossAmount),
    recorded: money(point.recorded),
    collected: money(point.collected),
    outstanding: money(point.outstanding),
  }
}

export async function fetchFeeRevenue({ from, to, granularity = 'day', venueId = '' } = {}) {
  const search = new URLSearchParams({ granularity })
  if (from) search.set('from', from)
  if (to) search.set('to', to)
  if (venueId) search.set('venueId', venueId)
  const data = await request(`/api/v1/admin/finance/fee-revenue?${search.toString()}`)
  const summary = data.summary
  return {
    summary: {
      bookingCount: Number(summary.bookingCount),
      grossAmount: money(summary.grossAmount),
      recorded: money(summary.recorded),
      collected: money(summary.collected),
      outstanding: money(summary.outstanding),
      effectiveRate: summary.effectiveRate == null ? null : Number(summary.effectiveRate),
      suspendedCount: Number(summary.suspendedCount),
      suspended: money(summary.suspended),
      waivedCount: Number(summary.waivedCount),
      waived: money(summary.waived),
    },
    trend: { from: data.trend.from, to: data.trend.to, granularity: data.trend.granularity, points: data.trend.points.map(mapPoint) },
    venues: data.venues.map((venue) => ({
      venueId: venue.venueId,
      venueName: venue.venueName,
      ownerName: venue.ownerName,
      bookingCount: Number(venue.bookingCount),
      grossAmount: money(venue.grossAmount),
      recorded: money(venue.recorded),
      collected: money(venue.collected),
      outstanding: money(venue.outstanding),
    })),
    venueOptions: data.venueOptions,
  }
}
