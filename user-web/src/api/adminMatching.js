/** 2.3.70 - 2.3.76: phân tích và đánh giá hệ thống ghép cặp (quản trị viên, chỉ đọc). */

import { request } from './client'

export const SOURCE_LABEL = { baseline: 'Baseline (công thức)', ai: 'Mô hình AI' }

function query(params) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  })
  const text = search.toString()
  return text ? `?${text}` : ''
}

/** Chỉ số có thể vắng mặt (null) khi mẫu số bằng 0: chuyển về null để giao diện hiện "—". */
function metrics(item) {
  const rate = (value) => (value === undefined || value === null ? null : Number(value))
  return {
    suggestions: Number(item.suggestions),
    requested: Number(item.requested),
    requestRate: rate(item.requestRate),
    accepted: Number(item.accepted),
    rejected: Number(item.rejected),
    expired: Number(item.expired),
    cancelled: Number(item.cancelled),
    pending: Number(item.pending),
    answered: Number(item.answered),
    acceptanceRate: rate(item.acceptanceRate),
    playedTogether: Number(item.playedTogether),
    playedRate: rate(item.playedRate),
    sufficient: Boolean(item.sufficient),
  }
}

export async function fetchPartnerAccuracy({ from, to, source } = {}) {
  const data = await request(`/api/v1/admin/matching/partner-accuracy${query({ from, to, source })}`)
  const group = (item) => ({ key: item.key, label: item.label, metrics: metrics(item.metrics) })
  return {
    from: data.from,
    to: data.to,
    source: data.source ?? null,
    minSample: Number(data.minSample),
    attributionDays: Number(data.attributionDays),
    overall: metrics(data.overall),
    byRank: data.byRank.map(group),
    byScore: data.byScore.map(group),
  }
}

function teamMetrics(item) {
  const optional = (value) => (value === undefined || value === null ? null : Number(value))
  return {
    reconciled: Number(item.reconciled),
    evaluated: Number(item.evaluated),
    correct: Number(item.correct),
    accuracy: optional(item.accuracy),
    brier: optional(item.brier),
    avgBalanceGap: optional(item.avgBalanceGap),
    avgPointMargin: optional(item.avgPointMargin),
    sufficient: Boolean(item.sufficient),
  }
}

/** 2.3.71: đối chiếu đề xuất chia đội với trận thật của đúng 4 người đó. */
export async function fetchTeamAccuracy({ from, to, source } = {}) {
  const data = await request(`/api/v1/admin/matching/team-accuracy${query({ from, to, source })}`)
  return {
    from: data.from,
    to: data.to,
    source: data.source ?? null,
    minSample: Number(data.minSample),
    reconcileDays: Number(data.reconcileDays),
    batches: Number(data.batches),
    options: Number(data.options),
    overall: teamMetrics(data.overall),
    recommended: teamMetrics(data.recommended),
    others: teamMetrics(data.others),
    recommendedChosenRate: data.recommendedChosenRate === undefined || data.recommendedChosenRate === null ? null : Number(data.recommendedChosenRate),
    samples: data.samples.map((item) => ({
      batchId: item.batchId,
      matchId: item.matchId,
      rank: Number(item.rank),
      recommended: Boolean(item.recommended),
      team1WinProbability: Number(item.team1WinProbability),
      winningTeam: Number(item.winningTeam),
      predictedWinningTeam: Number(item.predictedWinningTeam),
      correct: Boolean(item.correct),
      balanceScore: item.balanceScore === undefined || item.balanceScore === null ? null : Number(item.balanceScore),
      suggestedAt: item.suggestedAt,
      playedAt: item.playedAt,
    })),
  }
}
