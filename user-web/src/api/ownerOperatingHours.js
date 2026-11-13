/** 2.3.7 — giờ hoạt động của sân. */

import { request } from './client'

function toUi(data) {
  return {
    days: data.days.map((day) => ({
      dayOfWeek: day.dayOfWeek,
      closed: Boolean(day.closed),
      openTime: day.openTime ?? '',
      closeTime: day.closeTime ?? '',
      configured: Boolean(day.configured),
    })),
    // Booking chưa chơi nằm ngoài giờ mở cửa; backend KHÔNG tự huỷ các booking này.
    conflicts: (data.conflicts ?? []).map((conflict) => ({
      bookingId: conflict.bookingId,
      bookingCode: conflict.bookingCode,
      courtName: conflict.courtName,
      startTime: conflict.startTime,
      endTime: conflict.endTime,
      status: conflict.status,
    })),
  }
}

function path(venueId) {
  return `/api/v1/owner/venues/${encodeURIComponent(venueId)}/operating-hours`
}

export async function fetchOperatingHours(venueId) {
  return toUi(await request(path(venueId)))
}

export async function updateOperatingHours(venueId, days) {
  return toUi(await request(path(venueId), {
    method: 'PUT',
    body: {
      days: days.map((day) => ({
        dayOfWeek: day.dayOfWeek,
        closed: day.closed,
        openTime: day.closed ? null : day.openTime || null,
        closeTime: day.closed ? null : day.closeTime || null,
      })),
    },
  }))
}
