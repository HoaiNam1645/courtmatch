/** 2.3.37 — quản trị viên xử lý báo cáo về đánh giá đối tác. */

import { request } from './client'

export { REPORT_TABS, reportStatusMeta } from './adminReports'

export const REVIEW_STATUS_META = {
  visible: { label: 'Đang hiển thị', className: 'bg-emerald-100 text-emerald-700' },
  hidden: { label: 'Đã gỡ', className: 'bg-red-100 text-red-700' },
  reported: { label: 'Đang bị báo cáo', className: 'bg-amber-100 text-amber-800' },
}

export function reviewStatusMeta(status) {
  return REVIEW_STATUS_META[status] ?? { label: status ?? 'Không còn', className: 'bg-stone-200 text-stone-600' }
}

export const REVIEW_STATUS_FILTERS = ['all', 'visible', 'reported', 'hidden']

export const HANDLING_LABEL = { review_hidden: 'Đã gỡ đánh giá', review_kept: 'Giữ nguyên đánh giá' }

function mapReview(review) {
  return {
    id: review.id,
    exists: Boolean(review.exists),
    rating: review.rating == null ? null : Number(review.rating),
    comment: review.comment ?? '',
    reviewerName: review.reviewerName ?? '',
    reviewedName: review.reviewedName ?? '',
    matchId: review.matchId ?? null,
    status: review.status ?? null,
    createdAt: review.createdAt ?? null,
  }
}

export async function fetchReviewReports({ status = 'all', reason = 'all', reviewStatus = 'all', q = '', page = 0, size = 20 } = {}) {
  const search = new URLSearchParams({ status, reason, reviewStatus, page: String(page), size: String(size) })
  if (q.trim()) search.set('q', q.trim())
  const data = await request(`/api/v1/admin/review-reports?${search.toString()}`)
  return {
    reports: data.content.map((item) => ({
      id: item.id,
      status: item.status,
      reason: item.reason,
      reporterName: item.reporterName,
      review: mapReview(item.review),
      createdAt: item.createdAt,
      handledAt: item.handledAt ?? null,
      handledBy: item.handledBy ?? '',
    })),
    page: data.page,
    totalPages: data.totalPages,
    totalElements: data.totalElements,
    statusCounts: data.statusCounts ?? {},
    reasons: data.reasons ?? [],
  }
}

function mapDetail(data) {
  return {
    id: data.id,
    status: data.status,
    reason: data.reason,
    description: data.description ?? '',
    reporterName: data.reporterName,
    review: mapReview(data.review),
    related: data.related ?? [],
    totalReports: Number(data.totalReports ?? 0),
    createdAt: data.createdAt,
    handling: data.handling ?? null,
    canStart: Boolean(data.canStart),
    canConclude: Boolean(data.canConclude),
    canHide: Boolean(data.canHide),
    canRestore: Boolean(data.canRestore),
    involved: Boolean(data.involved),
  }
}

const base = (id) => `/api/v1/admin/review-reports/${encodeURIComponent(id)}`

export async function fetchReviewReport(reportId) {
  return mapDetail(await request(base(reportId)))
}

export async function startReviewReport(reportId) {
  return mapDetail(await request(`${base(reportId)}/start`, { method: 'POST' }))
}

/** action: hide (gỡ đánh giá), keep (giữ nguyên), restore (khôi phục đánh giá đã gỡ). */
export async function concludeReviewReport(reportId, action, note) {
  return mapDetail(await request(`${base(reportId)}/${action}`, { method: 'POST', body: { note: note.trim() } }))
}
