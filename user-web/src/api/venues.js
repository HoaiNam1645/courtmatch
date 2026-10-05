/** Duyệt sân: 2.1.13 tìm kiếm, 2.1.14–2.1.17 bản đồ, 2.1.18 chi tiết. */

import { request } from './client'

const priceFormatter = new Intl.NumberFormat('vi-VN')

/** Định dạng tiền để hiển thị. Số tiền gốc vẫn giữ trong `minPricePerHour`. */
function formatPriceLabel(minPricePerHour) {
  if (minPricePerHour == null) return 'Chưa có giá'
  return `${priceFormatter.format(Number(minPricePerHour))}đ/giờ`
}

/** Giờ mở cửa của hôm nay, do backend trả theo thứ hiện tại. */
function formatOpenHours(venue) {
  if (venue.closedToday) return 'Hôm nay nghỉ'
  if (!venue.todayOpenTime || !venue.todayCloseTime) return 'Chưa có giờ mở cửa'
  return `${venue.todayOpenTime} - ${venue.todayCloseTime}`
}

/**
 * Đổi phản hồi API sang đúng tên trường mà giao diện đang dùng.
 *
 * Lưu ý `courtCount` là **số sân con đang hoạt động**, không phải số sân còn trống —
 * tính sân trống cần dựng lịch từ booking_items, thuộc 2.1.26.
 */
function toUiVenue(venue) {
  return {
    id: venue.id,
    slug: venue.slug,
    name: venue.name,
    district: venue.district ?? '',
    address: venue.address ?? '',
    phone: venue.phone ?? '',
    latitude: venue.latitude == null ? null : Number(venue.latitude),
    longitude: venue.longitude == null ? null : Number(venue.longitude),
    image: venue.coverImageUrl ?? null,
    rating: venue.averageRating == null ? null : Number(venue.averageRating),
    reviewCount: venue.reviewCount ?? 0,
    courtCount: venue.courtCount ?? 0,
    minPricePerHour: venue.minPricePerHour == null ? null : Number(venue.minPricePerHour),
    priceLabel: formatPriceLabel(venue.minPricePerHour),
    openHours: formatOpenHours(venue),
    closedToday: Boolean(venue.closedToday),
    // null khi lời gọi không kèm toạ độ người dùng.
    distanceKm: venue.distanceKm == null ? null : Number(venue.distanceKm),
  }
}

/**
 * Tìm kiếm sân. Mọi việc lọc, sắp xếp và phân trang đều do backend làm.
 *
 * @param {{q?: string, district?: string, lat?: number, lng?: number, radiusKm?: number,
 *          maxPricePerHour?: number, minRating?: number, page?: number, size?: number}} params
 */
export async function searchVenues(params = {}) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  })

  const data = await request(`/api/v1/venues?${search.toString()}`, { auth: false })
  return {
    venues: data.content.map(toUiVenue),
    page: data.page,
    size: data.size,
    totalElements: data.totalElements,
    totalPages: data.totalPages,
  }
}

/** Khu vực có sân, dùng dựng danh sách trong modal bộ lọc (2.1.19). */
export async function fetchDistricts() {
  const data = await request('/api/v1/venues/districts', { auth: false })
  return data.map((item) => ({ name: item.district, venueCount: item.venueCount }))
}

/** Đánh giá của sân, hiện ở trang chi tiết (2.1.23). */
export async function fetchVenueReviews(slugOrId, { page = 0, size = 10 } = {}) {
  const data = await request(
    `/api/v1/venues/${encodeURIComponent(slugOrId)}/reviews?page=${page}&size=${size}`,
    { auth: false },
  )
  return {
    reviews: data.content.map((review) => ({
      id: review.id,
      name: review.reviewerName,
      avatarUrl: review.reviewerAvatarUrl ?? null,
      rating: review.rating,
      text: review.comment ?? '',
      createdAt: review.createdAt,
    })),
    totalElements: data.totalElements,
  }
}

/**
 * Lịch trống của sân trong một ngày (2.1.26).
 *
 * Trả về tất cả sân con trong một lần gọi để đổi sân con không phải chờ tải lại.
 */
export async function fetchAvailability(venueId, { date, durationMinutes = 60 }) {
  const data = await request(
    `/api/v1/venues/${encodeURIComponent(venueId)}/availability?date=${date}&durationMinutes=${durationMinutes}`,
    { auth: false },
  )
  return {
    date: data.date,
    closed: data.closed,
    openTime: data.openTime,
    closeTime: data.closeTime,
    durationMinutes: data.durationMinutes,
    courts: data.courts.map((court) => ({
      courtId: court.courtId,
      courtName: court.courtName,
      courtCode: court.courtCode,
      availableCount: court.availableCount,
      slots: court.slots.map((slot) => ({
        time: slot.startTime,
        endTime: slot.endTime,
        startAt: slot.startAt,
        endAt: slot.endAt,
        status: slot.status,
        totalPrice: slot.totalPrice == null ? null : Number(slot.totalPrice),
      })),
    })),
  }
}

/**
 * Tạm tính tiền cho lựa chọn đặt sân (2.1.27).
 *
 * Số tiền do server tính từ court_price_rules — frontend **không tự nhân giá**.
 */
export async function fetchQuote({ courtId, startTime, durationMinutes }) {
  const data = await request('/api/v1/bookings/quote', {
    method: 'POST',
    auth: false,
    body: { courtId, startTime, durationMinutes },
  })
  return {
    venueId: data.venueId,
    venueName: data.venueName,
    courtId: data.courtId,
    courtName: data.courtName,
    date: data.date,
    startTime: data.startTime,
    endTime: data.endTime,
    startTimeOfDay: data.startTimeOfDay,
    endTimeOfDay: data.endTimeOfDay,
    durationMinutes: data.durationMinutes,
    totalAmount: data.totalAmount == null ? null : Number(data.totalAmount),
    available: data.available,
    unavailableReason: data.unavailableReason ?? null,
    segments: (data.segments ?? []).map((segment) => ({
      startTime: segment.startTime,
      endTime: segment.endTime,
      pricePerHour: Number(segment.pricePerHour),
      amount: Number(segment.amount),
    })),
  }
}

/** Chi tiết một sân (2.1.18). Nhận slug hoặc id. */
export async function fetchVenueDetail(slugOrId) {
  const data = await request(`/api/v1/venues/${encodeURIComponent(slugOrId)}`, { auth: false })
  return {
    ...toUiVenue(data),
    description: data.description ?? '',
    ward: data.ward ?? '',
    province: data.province ?? '',
    email: data.email ?? '',
    maxPricePerHour: data.maxPricePerHour == null ? null : Number(data.maxPricePerHour),
    images: data.images ?? [],
    services: data.services ?? [],
    operatingHours: data.operatingHours ?? [],
    courts: data.courts ?? [],
  }
}

/**
 * Đổi phản hồi chi tiết sân sang dạng bảng phẳng mà màn hình /san/:venueId đang dùng
 * (`courts`, `court_price_rules`, `venue_images`...).
 *
 * Đây là lớp chuyển tiếp: giữ nguyên phần hiển thị 379 dòng của màn hình đó thay vì
 * viết lại. Khi màn hình được tách nhỏ thì bỏ hàm này, không phải đụng vào API.
 */
export function toVenueDetailTables(detail) {
  const venueId = detail.id

  return {
    venue_images: detail.images.map((image) => ({
      id: image.id,
      venue_id: venueId,
      image_url: image.imageUrl,
      caption: image.caption,
      display_order: image.displayOrder,
      is_cover: image.cover,
    })),
    courts: detail.courts.map((court) => ({
      id: court.id,
      venue_id: venueId,
      name: court.name,
      court_code: court.courtCode,
      court_type: court.courtType,
      surface_type: court.surfaceType,
      indoor: court.indoor,
      status: 'active',
    })),
    court_price_rules: detail.courts.flatMap((court) =>
      court.priceRules.map((rule) => ({
        court_id: court.id,
        day_of_week: rule.dayOfWeek,
        start_time: rule.startTime,
        end_time: rule.endTime,
        price_per_hour: rule.pricePerHour,
        status: 'active',
      })),
    ),
    // Danh mục dịch vụ và bảng gán dùng chung `code` làm khoá, đủ để hiển thị.
    services: detail.services.map((service) => ({
      id: service.code,
      code: service.code,
      name: service.name,
      description: service.description,
    })),
    venue_services: detail.services.map((service) => ({
      venue_id: venueId,
      service_id: service.code,
      price: service.price,
      note: service.note,
    })),
    venue_operating_hours: detail.operatingHours.map((hours) => ({
      venue_id: venueId,
      day_of_week: hours.dayOfWeek,
      open_time: hours.openTime,
      close_time: hours.closeTime,
      is_closed: hours.closed,
    })),
  }
}
