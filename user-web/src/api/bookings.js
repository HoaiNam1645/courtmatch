/** Đặt sân: 2.1.28 tạo đơn, 2.1.29/2.1.30 xem, 2.1.31 huỷ, 2.1.32 lịch sử. */

import { request } from './client'

/** Nhãn tiếng Việt cho một bước trong timeline (2.1.30). */
const STATUS_LABEL = {
  pending_payment: 'Đã tạo yêu cầu, chờ thanh toán',
  confirmed: 'Đã xác nhận đặt sân',
  cancelled: 'Đã huỷ đặt sân',
  completed: 'Đã chơi xong',
  expired: 'Hết hạn thanh toán',
  refunded: 'Đã hoàn tiền',
}

function minutesBetween(startTime, endTime) {
  if (!startTime || !endTime) return 0
  return Math.round((new Date(endTime).getTime() - new Date(startTime).getTime()) / 60000)
}

function toUiBooking(booking) {
  const first = (booking.items ?? [])[0]
  return {
    id: booking.id,
    bookingCode: booking.bookingCode,
    status: booking.status,
    totalAmount: Number(booking.totalAmount ?? 0),
    note: booking.note ?? '',
    expiresAt: booking.expiresAt ?? null,
    confirmedAt: booking.confirmedAt ?? null,
    cancelledAt: booking.cancelledAt ?? null,
    cancellationReason: booking.cancellationReason ?? '',
    createdAt: booking.createdAt,
    // Server quyết định có huỷ được không; giao diện không tự suy từ trạng thái.
    cancellable: Boolean(booking.cancellable),
    // Các trường dẫn xuất để phần hiển thị không phải tự tính.
    startTime: first?.startTime ?? null,
    endTime: first?.endTime ?? null,
    durationMinutes: minutesBetween(first?.startTime, first?.endTime),
    // Người chơi trả đúng tiền sân. Phí nền tảng trừ vào doanh thu chủ sân
    // (owner_balance_transactions), không cộng vào hoá đơn người chơi.
    price: Number(booking.totalAmount ?? 0),
    court: first ? { id: first.courtId, name: first.courtName } : null,
    venue: {
      id: booking.venue.id,
      slug: booking.venue.slug,
      name: booking.venue.name,
      address: booking.venue.address ?? '',
      phone: booking.venue.phone ?? '',
      latitude: booking.venue.latitude == null ? null : Number(booking.venue.latitude),
      longitude: booking.venue.longitude == null ? null : Number(booking.venue.longitude),
      image: booking.venue.coverImageUrl ?? null,
    },
    items: (booking.items ?? []).map((item) => ({
      id: item.id,
      courtId: item.courtId,
      courtName: item.courtName,
      startTime: item.startTime,
      endTime: item.endTime,
      price: Number(item.price ?? 0),
      status: item.status,
    })),
    // `oldStatus` vắng mặt ở bước đầu tiên vì API lược bỏ trường null.
    statusHistory: (booking.statusHistory ?? []).map((change) => ({
      oldStatus: change.oldStatus ?? null,
      newStatus: change.newStatus,
      reason: change.reason ?? '',
      bySystem: Boolean(change.bySystem),
      createdAt: change.createdAt,
    })),
    // Timeline dựng sẵn cho phần hiển thị (2.1.30).
    history: (booking.statusHistory ?? []).map((change) => ({
      status: change.newStatus,
      label: STATUS_LABEL[change.newStatus] ?? change.newStatus,
      reason: change.reason ?? '',
      bySystem: Boolean(change.bySystem),
      createdAt: change.createdAt,
    })),
  }
}

function toUiSummary(booking) {
  return {
    id: booking.id,
    bookingCode: booking.bookingCode,
    status: booking.status,
    totalAmount: Number(booking.totalAmount ?? 0),
    venueId: booking.venueId,
    venueSlug: booking.venueSlug,
    venueName: booking.venueName,
    venueAddress: booking.venueAddress ?? '',
    venueImage: booking.venueCoverImageUrl ?? null,
    courtName: booking.courtName ?? '',
    durationMinutes: minutesBetween(booking.startTime, booking.endTime),
    startTime: booking.startTime ?? null,
    endTime: booking.endTime ?? null,
    expiresAt: booking.expiresAt ?? null,
    createdAt: booking.createdAt,
  }
}

/**
 * 2.1.28 — tạo yêu cầu đặt sân.
 *
 * Chỉ gửi lựa chọn, **không gửi số tiền**. Server tính lại từ court_price_rules
 * và kiểm tra lại khung giờ còn trống.
 */
export async function createBooking({ courtId, startTime, durationMinutes, note }) {
  const data = await request('/api/v1/bookings', {
    method: 'POST',
    body: { courtId, startTime, durationMinutes, note: note || null },
  })
  return toUiBooking(data)
}

/** 2.1.32 — lịch sử đặt sân theo tab và từ khoá. */
export async function fetchBookings({ tab = 'all', q = '', page = 0, size = 20 } = {}) {
  const search = new URLSearchParams({ tab, page: String(page), size: String(size) })
  if (q.trim()) search.set('q', q.trim())

  const data = await request(`/api/v1/bookings?${search.toString()}`)
  return {
    bookings: data.content.map(toUiSummary),
    page: data.page,
    totalElements: data.totalElements,
    totalPages: data.totalPages,
  }
}

/** 2.1.29, 2.1.30 — chi tiết đơn kèm timeline trạng thái. */
export async function fetchBookingDetail(bookingId) {
  return toUiBooking(await request(`/api/v1/bookings/${encodeURIComponent(bookingId)}`))
}

/** 2.1.31 — huỷ đặt sân. */
export async function cancelBooking(bookingId, reason) {
  const data = await request(`/api/v1/bookings/${encodeURIComponent(bookingId)}/cancel`, {
    method: 'POST',
    body: { reason },
  })
  return toUiBooking(data)
}
