import { request } from './client'

function numberOrNull(value) {
  return value == null ? null : Number(value)
}

function toPerformance(data) {
  return {
    summary: {
      ratingScore: Number(data.summary.ratingScore),
      ratingLabel: data.summary.ratingLabel,
      isProvisional: Boolean(data.summary.isProvisional),
      totalMatches: data.summary.totalMatches,
      totalWins: data.summary.totalWins,
      totalLosses: data.summary.totalLosses,
      rawWinRate: Number(data.summary.rawWinRate),
      smoothedWinRate: Number(data.summary.smoothedWinRate),
      lastPlayedAt: data.summary.lastPlayedAt ?? null,
    },
    features: {
      skillScore: Number(data.features.skillScore),
      smoothedWinRate: Number(data.features.smoothedWinRate),
      totalMatches: data.features.totalMatches,
      averagePointDifference: numberOrNull(data.features.averagePointDifference),
      performanceConsistency: numberOrNull(data.features.performanceConsistency),
      dataRecencyDays: data.features.dataRecencyDays ?? null,
    },
    dataQuality: {
      trainingEligible: Boolean(data.dataQuality.trainingEligible),
      minimumTrainingMatches: data.dataQuality.minimumTrainingMatches,
      excludedMatches: data.dataQuality.excludedMatches,
    },
  }
}

export async function fetchPlayerPerformance() {
  return toPerformance(await request('/api/v1/players/me/performance'))
}

export async function fetchPlayerRatingHistory({ page = 0, size = 50 } = {}) {
  const search = new URLSearchParams({ page: String(page), size: String(size) })
  const data = await request(`/api/v1/players/me/rating-history?${search.toString()}`)
  return {
    history: data.content.map((item) => ({
      ...item,
      oldRating: Number(item.oldRating),
      newRating: Number(item.newRating),
      changeAmount: Number(item.changeAmount),
    })),
    page: data.page,
    totalElements: data.totalElements,
  }
}
