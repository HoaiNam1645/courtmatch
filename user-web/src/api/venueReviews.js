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

function toOwnerReview(item) {
  return {
    id: item.id,
    venueId: item.venueId,
    venueName: item.venueName,
    rating: Number(item.rating),
    comment: item.comment ?? '',
    reviewerName: item.reviewerName,
    createdAt: item.createdAt,
    edited: Boolean(item.edited),
    reply: item.reply ? { content: item.reply.content, repliedAt: item.reply.repliedAt, edited: Boolean(item.reply.edited) } : null,
  }
}

/** 2.3.81: chủ sân xem đánh giá hiển thị của các sân mình, lọc theo đã/chưa trả lời, sân, số sao. */
export async function fetchOwnerVenueReviews({ venueId = '', filter = 'all', rating = '', page = 0, size = 10 } = {}) {
  const search = new URLSearchParams({ filter, page: String(page), size: String(size) })
  if (venueId) search.set('venueId', venueId)
  if (rating) search.set('rating', String(rating))
  const data = await request(`/api/v1/owner/venue-reviews?${search.toString()}`)
  return {
    items: data.items.map(toOwnerReview),
    page: Number(data.page),
    totalPages: Number(data.totalPages),
    totalElements: Number(data.totalElements),
    unanswered: Number(data.unanswered),
  }
}

/** 2.3.82: sửa câu trả lời đã đăng. */
export async function updateVenueReviewReply(reviewId, content) {
  return toOwnerReview(await request(`/api/v1/owner/venue-reviews/${reviewId}/reply`, { method: 'PUT', body: { content } }))
}

/** 2.3.81: trả lời công khai một đánh giá. */
export async function replyToVenueReview(reviewId, content) {
  return toOwnerReview(await request(`/api/v1/owner/venue-reviews/${reviewId}/reply`, { method: 'POST', body: { content } }))
}
