import { request } from './client'

function toMatchHistory(item) {
  return {
    id: item.id,
    matchType: item.matchType,
    status: item.status,
    dataSource: item.dataSource,
    scheduledAt: item.scheduledAt,
    venue: item.venue ?? null,
    venueName: item.venue?.name ?? 'Không ghi nhận sân',
    team1: item.team1.map((player) => ({ ...player, name: player.fullName })),
    team2: item.team2.map((player) => ({ ...player, name: player.fullName })),
    games: item.games.map((game) => [game.team1Score, game.team2Score]),
    winningTeam: item.winningTeam ?? null,
    ratingChange: item.ratingChange == null ? null : Number(item.ratingChange),
    currentUserNeedsConfirmation: Boolean(item.currentUserNeedsConfirmation),
  }
}

export async function fetchMatchHistory({ state = 'all', page = 0, size = 10 } = {}) {
  const search = new URLSearchParams({ state, page: String(page), size: String(size) })
  const data = await request(`/api/v1/matches?${search.toString()}`)
  return {
    matches: data.content.map(toMatchHistory),
    page: data.page,
    size: data.size,
    totalElements: data.totalElements,
    totalPages: data.totalPages,
  }
}

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
