import { request } from './client'

const num = (value) => (value == null ? null : Number(value))

function toPlayer(item) {
  return {
    id: item.id,
    fullName: item.fullName,
    avatarUrl: item.avatarUrl ?? null,
    gender: item.gender,
    ratingScore: num(item.ratingScore),
    totalMatches: item.totalMatches,
    provisional: Boolean(item.provisional),
    winRate: num(item.winRate),
    strength: num(item.strength),
    confidence: num(item.confidence),
  }
}

function toTeam(team) {
  return { playerIds: team.playerIds, strength: num(team.strength), compatibility: num(team.compatibility) }
}

export function toSuggestion(data) {
  return {
    batchId: data.batchId,
    matchType: data.matchType,
    scoringSource: data.scoringSource,
    modelVersion: data.modelVersion,
    createdAt: data.createdAt,
    players: data.players.map(toPlayer),
    pairs: data.pairs.map((pair) => ({
      playerIds: pair.playerIds,
      skillScore: num(pair.skillScore),
      styleScore: num(pair.styleScore),
      timeScore: num(pair.timeScore),
      locationScore: num(pair.locationScore),
      totalScore: num(pair.totalScore),
    })),
    options: data.options.map((option) => ({
      rank: option.rank,
      recommended: Boolean(option.recommended),
      team1: toTeam(option.team1),
      team2: toTeam(option.team2),
      balanceScore: num(option.balanceScore),
      balanceLabel: option.balanceLabel,
      team1WinProbability: num(option.team1WinProbability),
      reason: option.reason,
    })),
  }
}

/** 2.3.63-2.3.69: chọn 3 người chơi cùng mình, nhận các phương án chia đội. */
export async function createDoubleTeamSuggestion({ matchType, playerIds }) {
  return toSuggestion(await request('/api/v1/double-teams/suggestions', { method: 'POST', body: { matchType, playerIds } }))
}

/** 2.3.69: xem lại đề xuất đã tạo. */
export async function getDoubleTeamSuggestion(batchId) {
  return toSuggestion(await request(`/api/v1/double-teams/suggestions/${batchId}`))
}
