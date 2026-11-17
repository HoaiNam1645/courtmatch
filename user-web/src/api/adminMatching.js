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
