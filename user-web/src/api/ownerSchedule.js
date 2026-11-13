/** 2.3.8 — lịch sân của chủ sân theo ngày / tuần. */

import { request } from './client'

/** Trạng thái một ô lịch; nhãn định nghĩa ở một chỗ. */
export const SLOT_STATUS_META = {
  available: { label: 'Trống', className: 'bg-white text-emerald-800 border-emerald-200 hover:bg-emerald-50' },
  pending: { label: 'Chờ duyệt', className: 'bg-amber-100 text-amber-900 border-amber-300' },
  confirmed: { label: 'Đã xác nhận', className: 'bg-emerald-600 text-white border-emerald-700' },
  blocked: { label: 'Đã khóa', className: 'bg-stone-300 text-stone-800 border-stone-400' },
  past: { label: 'Đã qua', className: 'bg-stone-100 text-stone-400 border-stone-200' },
  unavailable: { label: 'Sân tạm ngưng', className: 'bg-stone-100 text-stone-500 border-dashed border-stone-300' },
}

export function slotMeta(status) {
  return SLOT_STATUS_META[status] ?? { label: status ?? 'Không rõ', className: 'bg-stone-100 text-stone-600 border-stone-200' }
}

export async function fetchVenueSchedule(venueId, { date, days = 1 }) {
  const data = await request(`/api/v1/owner/venues/${encodeURIComponent(venueId)}/schedule?date=${date}&days=${days}`)
  return {
    venueId: data.venueId,
    startDate: data.startDate,
    stepMinutes: data.stepMinutes,
    days: data.days.map((day) => ({
      date: day.date,
      dayOfWeek: day.dayOfWeek,
      closed: Boolean(day.closed),
      openTime: day.openTime ?? null,
      closeTime: day.closeTime ?? null,
      courts: day.courts.map((court) => ({
        courtId: court.courtId,
        courtName: court.courtName,
        courtCode: court.courtCode,
        courtStatus: court.courtStatus,
        slots: court.slots.map((slot) => ({
          startTime: slot.startTime,
          endTime: slot.endTime,
          startAt: slot.startAt,
          endAt: slot.endAt,
          status: slot.status,
          price: slot.price == null ? null : Number(slot.price),
          booking: slot.booking ?? null,
          block: slot.block ?? null,
        })),
      })),
    })),
  }
}
