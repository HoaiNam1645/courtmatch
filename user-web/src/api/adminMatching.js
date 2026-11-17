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

const optionalNumber = (value) => (value === undefined || value === null ? null : Number(value))

/** 2.3.72: kết quả chấm điểm của từng model. Chỉ số vắng mặt (mẫu số 0) → null. */
export async function fetchModelAnalysis({ from, to, source } = {}) {
  const data = await request(`/api/v1/admin/matching/model-analysis${query({ from, to, source })}`)
  return {
    from: data.from,
    to: data.to,
    minSample: Number(data.minSample),
    models: data.models.map((model) => ({
      id: model.id,
      type: model.type,
      version: model.version,
      status: model.status,
      trainedAt: model.trainedAt ?? null,
      activatedAt: model.activatedAt ?? null,
      partner: model.partner ? {
        scored: Number(model.partner.scored),
        avgTotal: optionalNumber(model.partner.avgTotal),
        avgSkill: optionalNumber(model.partner.avgSkill),
        avgLocation: optionalNumber(model.partner.avgLocation),
        avgTime: optionalNumber(model.partner.avgTime),
        avgStyle: optionalNumber(model.partner.avgStyle),
        missingTimeRate: optionalNumber(model.partner.missingTimeRate),
        missingLocationRate: optionalNumber(model.partner.missingLocationRate),
        distribution: model.partner.distribution.map((item) => ({ label: item.label, count: Number(item.count) })),
        requested: Number(model.partner.requested),
        answered: Number(model.partner.answered),
        accepted: Number(model.partner.accepted),
        acceptanceRate: optionalNumber(model.partner.acceptanceRate),
        sufficient: Boolean(model.partner.sufficient),
      } : null,
      team: model.team ? {
        batches: Number(model.team.batches),
        avgBestGap: optionalNumber(model.team.avgBestGap),
        pairs: Number(model.team.pairs),
        missingTimeRate: optionalNumber(model.team.missingTimeRate),
        missingLocationRate: optionalNumber(model.team.missingLocationRate),
        sufficient: Boolean(model.team.sufficient),
      } : null,
    })),
  }
}

function runSummary(item) {
  return {
    runs: Number(item.runs),
    failed: Number(item.failed),
    failureRate: optionalNumber(item.failureRate),
    p50Ms: optionalNumber(item.p50Ms),
    p95Ms: optionalNumber(item.p95Ms),
    maxMs: optionalNumber(item.maxMs),
    baselineRuns: Number(item.baselineRuns),
    baselineRate: optionalNumber(item.baselineRate),
    avgResults: optionalNumber(item.avgResults),
  }
}

/** 2.3.73: hiệu suất xử lý và chất lượng model. */
export async function fetchMatchingPerformance({ from, to } = {}) {
  const data = await request(`/api/v1/admin/matching/performance${query({ from, to })}`)
  return {
    from: data.from,
    to: data.to,
    aiServiceActive: Boolean(data.aiServiceActive),
    thresholds: data.thresholds,
    partnerSearch: runSummary(data.partnerSearch),
    doubleTeam: runSummary(data.doubleTeam),
    daily: data.daily.map((item) => ({
      day: item.day,
      partnerSearches: Number(item.partnerSearches),
      doubleTeams: Number(item.doubleTeams),
      failed: Number(item.failed),
    })),
    models: data.models.map((item) => ({
      id: item.id,
      type: item.type,
      version: item.version,
      status: item.status,
      trainedAt: item.trainedAt ?? null,
      activatedAt: item.activatedAt ?? null,
      ageDays: optionalNumber(item.ageDays),
      metrics: item.metrics ?? null,
    })),
    alerts: data.alerts,
  }
}

export const MODEL_TYPE_LABEL = { baseline_ranker: 'Xếp hạng đối tác (baseline)', catboost_ranker: 'Xếp hạng đối tác (CatBoost)', double_team_ranker: 'Chia đội đôi' }
export const MODEL_STATUS_LABEL = { active: 'Đang hoạt động', ready: 'Sẵn sàng', training: 'Đang huấn luyện', failed: 'Lỗi', archived: 'Lưu trữ' }

export const STATS_METRICS = [
  { id: 'searches', label: 'Lượt tìm đối tác' },
  { id: 'suggestions', label: 'Gợi ý hiển thị' },
  { id: 'requests', label: 'Yêu cầu ghép cặp' },
  { id: 'accepted', label: 'Yêu cầu được chấp nhận' },
  { id: 'rejected', label: 'Yêu cầu bị từ chối' },
  { id: 'expired', label: 'Yêu cầu hết hạn' },
  { id: 'connections', label: 'Kết nối mới' },
  { id: 'teamRuns', label: 'Lượt chia đội' },
  { id: 'teamOptions', label: 'Phương án chia đội' },
]

function statsPoint(item) {
  return {
    period: item.period ?? null,
    searches: Number(item.searches),
    suggestions: Number(item.suggestions),
    requests: Number(item.requests),
    accepted: Number(item.accepted),
    rejected: Number(item.rejected),
    expired: Number(item.expired),
    cancelled: Number(item.cancelled),
    connections: Number(item.connections),
    teamRuns: Number(item.teamRuns),
    teamOptions: Number(item.teamOptions),
  }
}

/** 2.3.74: thống kê hiệu suất ghép cặp theo ngày / tuần / tháng. */
export async function fetchMatchingStats({ from, to, granularity = 'day' } = {}) {
  const data = await request(`/api/v1/admin/matching/stats${query({ from, to, granularity })}`)
  return {
    from: data.from,
    to: data.to,
    granularity: data.granularity,
    totals: statsPoint(data.totals),
    avgResponseMinutes: optionalNumber(data.avgResponseMinutes),
    responseSamples: Number(data.responseSamples),
    points: data.points.map(statsPoint),
  }
}

export const OUTCOME_LABEL = {
  none: 'Chưa có tương tác',
  pending: 'Đang chờ phản hồi',
  accepted: 'Đã chấp nhận',
  rejected: 'Bị từ chối',
  expired: 'Hết hạn',
  cancelled: 'Đã hủy',
}

const playerRef = (item) => ({ id: item.id, fullName: item.fullName, email: item.email })

function historyItem(item) {
  return {
    id: item.id,
    createdAt: item.createdAt,
    viewer: playerRef(item.viewer),
    suggested: playerRef(item.suggested),
    rankNo: optionalNumber(item.rankNo),
    totalScore: Number(item.totalScore),
    scoringSource: item.scoringSource,
    modelVersion: item.modelVersion ?? null,
    outcome: item.outcome,
    requestId: item.requestId ?? null,
    requestedAt: item.requestedAt ?? null,
    respondedAt: item.respondedAt ?? null,
  }
}

/** 2.3.75: lịch sử gợi ý và kết quả yêu cầu ghép cặp, phân trang. */
export async function fetchPartnerHistory({ from, to, source, q, outcome = 'all', page = 0, size = 20 } = {}) {
  const data = await request(`/api/v1/admin/matching/partner-history${query({ from, to, source, q, outcome, page, size })}`)
  return {
    items: data.content.map(historyItem),
    page: Number(data.page),
    size: Number(data.size),
    totalElements: Number(data.totalElements),
    totalPages: Number(data.totalPages),
  }
}

export async function fetchPartnerHistoryDetail(id) {
  const data = await request(`/api/v1/admin/matching/partner-history/${id}`)
  return {
    item: historyItem(data.item),
    skillScore: optionalNumber(data.skillScore),
    locationScore: optionalNumber(data.locationScore),
    timeScore: optionalNumber(data.timeScore),
    styleScore: optionalNumber(data.styleScore),
    confidenceScore: optionalNumber(data.confidenceScore),
    reason: data.reason ?? null,
    search: data.search ? {
      id: data.search.id,
      durationMs: optionalNumber(data.search.durationMs),
      resultCount: optionalNumber(data.search.resultCount),
      params: data.search.params ?? null,
    } : null,
  }
}

export const TEAM_TYPE_LABEL = { mens: 'Đôi nam', womens: 'Đôi nữ', mixed: 'Đôi nam nữ' }
export const PLAYED_LABEL = { all: 'Mọi trạng thái đối chiếu', played: 'Đã có trận thật', unplayed: 'Chưa có trận' }

function teamHistoryItem(item) {
  const reconciliation = item.reconciliation
  return {
    batchId: item.batchId,
    createdAt: item.createdAt,
    requester: playerRef(item.requester),
    matchType: item.matchType,
    players: item.players.map((player) => ({ id: player.id, fullName: player.fullName })),
    optionCount: Number(item.optionCount),
    recommended: {
      team1Strength: optionalNumber(item.recommended.team1Strength),
      team2Strength: optionalNumber(item.recommended.team2Strength),
      balanceScore: optionalNumber(item.recommended.balanceScore),
      balanceLabel: item.recommended.balanceLabel ?? null,
      team1WinProbability: optionalNumber(item.recommended.team1WinProbability),
    },
    scoringSource: item.scoringSource,
    modelVersion: item.modelVersion ?? null,
    reconciliation: {
      status: reconciliation.status,
      matchId: reconciliation.matchId ?? null,
      playedAt: reconciliation.playedAt ?? null,
      optionRank: optionalNumber(reconciliation.optionRank),
      optionRecommended: reconciliation.optionRecommended ?? null,
      predictedCorrect: reconciliation.predictedCorrect ?? null,
    },
  }
}

/** 2.3.76: lịch sử các lần chia đội của người chơi, phân trang. */
export async function fetchTeamHistory({ from, to, source, q, type = 'all', played = 'all', page = 0, size = 20 } = {}) {
  const data = await request(`/api/v1/admin/matching/team-history${query({ from, to, source, q, type, played, page, size })}`)
  return {
    items: data.content.map(teamHistoryItem),
    page: Number(data.page),
    totalPages: Number(data.totalPages),
    totalElements: Number(data.totalElements),
  }
}

export async function fetchTeamHistoryDetail(batchId) {
  const data = await request(`/api/v1/admin/matching/team-history/${batchId}`)
  const team = (item) => ({ playerIds: item.playerIds, strength: optionalNumber(item.strength), compatibility: optionalNumber(item.compatibility) })
  return {
    item: teamHistoryItem(data.item),
    players: data.players.map((player) => ({
      id: player.id,
      fullName: player.fullName,
      ratingScore: optionalNumber(player.ratingScore),
      totalMatches: optionalNumber(player.totalMatches),
      provisional: Boolean(player.provisional),
      strength: optionalNumber(player.strength),
    })),
    pairs: data.pairs.map((pair) => ({
      playerIds: pair.playerIds,
      skillScore: optionalNumber(pair.skillScore),
      styleScore: optionalNumber(pair.styleScore),
      timeScore: optionalNumber(pair.timeScore),
      locationScore: optionalNumber(pair.locationScore),
      totalScore: optionalNumber(pair.totalScore),
    })),
    options: data.options.map((option) => ({
      rank: Number(option.rank),
      recommended: Boolean(option.recommended),
      team1: team(option.team1),
      team2: team(option.team2),
      balanceScore: optionalNumber(option.balanceScore),
      balanceLabel: option.balanceLabel,
      team1WinProbability: optionalNumber(option.team1WinProbability),
      reason: option.reason,
    })),
  }
}
