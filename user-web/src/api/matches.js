import { request } from './client'

export async function fetchMatchParticipants({ q = '', page = 0, size = 50 } = {}) {
  const search = new URLSearchParams({ page: String(page), size: String(size) })
  if (q.trim()) search.set('q', q.trim())
  const data = await request(`/api/v1/matches/participants?${search.toString()}`)
  return { players: data.content, totalElements: data.totalElements }
}

export function createMatch(payload) {
  return request('/api/v1/matches', { method: 'POST', body: payload })
}

export function fetchMatchDetail(matchId) {
  return request(`/api/v1/matches/${encodeURIComponent(matchId)}`)
}

export function updateMatchGames(matchId, games) {
  return request(`/api/v1/matches/${encodeURIComponent(matchId)}/games`, {
    method: 'PUT',
    body: { games },
  })
}

export function confirmMatch(matchId) {
  return request(`/api/v1/matches/${encodeURIComponent(matchId)}/confirm`, { method: 'POST' })
}

export function disputeMatch(matchId, reason) {
  return request(`/api/v1/matches/${encodeURIComponent(matchId)}/dispute`, {
    method: 'POST',
    body: { reason },
  })
}

export function cancelMatch(matchId) {
  return request(`/api/v1/matches/${encodeURIComponent(matchId)}`, { method: 'DELETE' })
}
