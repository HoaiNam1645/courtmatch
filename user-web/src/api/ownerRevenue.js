/** 2.3.17 — thống kê doanh thu gộp của các sân. */

import { request } from './client'

export async function fetchRevenueStats({ venueId = '', from = '', to = '' } = {}) {
  const search = new URLSearchParams()
  if (venueId) search.set('venueId', venueId)
  if (from) search.set('from', from)
  if (to) search.set('to', to)
  const data = await request(`/api/v1/owner/revenue${search.size > 0 ? `?${search.toString()}` : ''}`)
  return {
    from: data.from,
    to: data.to,
    totalRevenue: Number(data.totalRevenue ?? 0),
    bookingCount: Number(data.bookingCount ?? 0),
    averagePerBooking: Number(data.averagePerBooking ?? 0),
    previous: {
      from: data.previous.from,
      to: data.previous.to,
      totalRevenue: Number(data.previous.totalRevenue ?? 0),
      bookingCount: Number(data.previous.bookingCount ?? 0),
    },
    // null = kỳ trước bằng 0 nên không có % thay đổi
    changePercent: data.changePercent == null ? null : Number(data.changePercent),
    daily: data.daily.map((point) => ({ date: point.date, revenue: Number(point.revenue ?? 0), bookings: Number(point.bookings ?? 0) })),
    byVenue: data.byVenue.map((venue) => ({ venueId: venue.venueId, venueName: venue.venueName, revenue: Number(venue.revenue ?? 0), bookings: Number(venue.bookings ?? 0) })),
  }
}
