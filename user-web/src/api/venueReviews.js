/** 2.3.77 - 2.3.79: người chơi viết, sửa, xóa đánh giá sân của chính mình. */

import { request } from './client'

function toMyReview(data) {
  return {
    id: data.id,
    venueId: data.venueId,
    bookingId: data.bookingId ?? null,
    rating: Number(data.rating),
    comment: data.comment ?? '',
    status: data.status,
    createdAt: data.createdAt,
    updatedAt: data.updatedAt,
  }
}

/** 2.3.78: sửa đánh giá của chính mình. */
export async function updateVenueReview(reviewId, { rating, comment }) {
  return toMyReview(await request(`/api/v1/venue-reviews/${reviewId}`, { method: 'PUT', body: { rating, comment: comment || null } }))
}

/** 2.3.79: xóa đánh giá của chính mình. */
export async function deleteVenueReview(reviewId) {
  await request(`/api/v1/venue-reviews/${reviewId}`, { method: 'DELETE' })
}

/** 2.3.77: viết đánh giá cho một đơn đã hoàn thành. */
export async function createVenueReview(bookingId, { rating, comment }) {
  return toMyReview(await request(`/api/v1/bookings/${bookingId}/venue-review`, { method: 'POST', body: { rating, comment: comment || null } }))
}
