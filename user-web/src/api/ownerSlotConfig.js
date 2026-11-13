/** 2.3.9 — bước sinh khung giờ đặt sân. */

import { request } from './client'

function toUi(data) {
  return {
    stepMinutes: data.stepMinutes,
    allowedSteps: data.allowedSteps,
    days: data.days.map((day) => ({
      dayOfWeek: day.dayOfWeek,
      closed: Boolean(day.closed),
      openTime: day.openTime ?? null,
      closeTime: day.closeTime ?? null,
      slotCount: day.slotCount,
      unpricedSlots: day.unpricedSlots ?? {},
    })),
  }
}

function path(venueId) {
  return `/api/v1/owner/venues/${encodeURIComponent(venueId)}/slot-config`
}

export async function fetchSlotConfig(venueId) {
  return toUi(await request(path(venueId)))
}

export async function updateSlotStep(venueId, stepMinutes) {
  return toUi(await request(path(venueId), { method: 'PUT', body: { stepMinutes } }))
}
