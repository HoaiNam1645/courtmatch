import { request } from './client'

export function createPartnerReview({ matchId, reviewedUserId, rating, comment }) {
  return request('/api/v1/partner-reviews', {
    method: 'POST',
    body: { matchId, reviewedUserId, rating, comment: comment.trim() || null },
  })
}

export function getPartnerReviews(playerId, { page = 0, size = 10 } = {}) {
  const search = new URLSearchParams({ page: String(page), size: String(size) })
  return request(`/api/v1/players/${encodeURIComponent(playerId)}/partner-reviews?${search.toString()}`)
}
