import { useState } from 'react'
import { CheckCircle2, ChevronRight, Search, X } from 'lucide-react'
import { fetchReviewReports, REPORT_TABS, REVIEW_STATUS_FILTERS, reportStatusMeta, reviewStatusMeta } from '../../../api/adminReviewReports'
import { useApiResource } from '../../../hooks/useApiResource'
import { useDebounced } from '../../../hooks/useDebounced'
import { classNames } from '../../../shared/utils'
import { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading } from '../../owner/components/OwnerUi'
import { formatDateTime } from '../../owner/ownerDates'
import { inputClass, secondaryButtonClass } from '../../owner/ownerStyles'
import { AdminLayout } from '../components/AdminLayout'
import { ReviewReportDrawer, Stars } from '../components/ReviewReportDrawer'

/** 2.3.37 — quản lý báo cáo về đánh giá đối tác. */
function AdminReviewReportsPage() {
  const [filters, setFilters] = useState({ status: 'pending', reason: 'all', reviewStatus: 'all', page: 0 })
  const [query, setQuery] = useState('')
  const [selectedId, setSelectedId] = useState(null)
  const [notice, setNotice] = useState('')
  const q = useDebounced(query)
  const { status, data, error, reload } = useApiResource(
    () => fetchReviewReports({ ...filters, q }),
    [filters.status, filters.reason, filters.reviewStatus, filters.page, q],
    { isEmpty: (result) => result.reports.length === 0 },
  )
  const counts = data?.statusCounts ?? {}
  const total = Object.values(counts).reduce((sum, value) => sum + value, 0)

  function setFilter(patch) {
    setFilters((current) => ({ ...current, page: 0, ...patch }))
  }

  return (
    <AdminLayout eyebrow="2.3.37 · Báo cáo đánh giá" title="Báo cáo đánh giá đối tác" description="Xem xét đánh giá bị báo cáo: gỡ khỏi hồ sơ hoặc giữ nguyên.">
      {notice && <p role="status" className="mb-5 flex items-start gap-2 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-semibold text-emerald-800" data-testid="admin-notice"><CheckCircle2 className="mt-0.5 size-4 shrink-0" />{notice}</p>}
      <OwnerCard>
        <div className="flex gap-2 overflow-x-auto pb-1" role="tablist" aria-label="Trạng thái báo cáo">
          {REPORT_TABS.map((tab) => (
            <button key={tab} type="button" role="tab" aria-selected={filters.status === tab} data-testid={`review-tab-${tab}`} className={classNames('flex h-10 shrink-0 items-center gap-2 rounded-lg px-4 text-sm font-semibold', filters.status === tab ? 'bg-slate-800 text-white' : 'bg-slate-100 text-slate-600')} onClick={() => setFilter({ status: tab })}>
              {tab === 'all' ? 'Tất cả' : reportStatusMeta(tab).label}
              <span className={classNames('rounded-full px-2 text-xs font-bold', filters.status === tab ? 'bg-white/20' : 'bg-slate-200')}>{tab === 'all' ? total : counts[tab] ?? 0}</span>
            </button>
          ))}
        </div>
        <div className="mt-4 grid gap-3 sm:grid-cols-[minmax(0,1fr)_12rem_12rem]">
          <label className="flex h-11 items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 focus-within:border-slate-700">
            <Search className="size-4 shrink-0 text-slate-400" />
            <input className="min-w-0 flex-1 text-sm outline-none placeholder:text-slate-400" aria-label="Tìm báo cáo đánh giá" placeholder="Tên người liên quan hoặc nội dung đánh giá" value={query} maxLength={100} onChange={(event) => { setQuery(event.target.value); setFilters((current) => ({ ...current, page: 0 })) }} />
            {query && <button type="button" aria-label="Xóa tìm kiếm" onClick={() => setQuery('')}><X className="size-4 text-slate-400" /></button>}
          </label>
          <select className={inputClass} aria-label="Lọc theo lý do" value={filters.reason} onChange={(event) => setFilter({ reason: event.target.value })}>
            <option value="all">Tất cả lý do</option>
            {(data?.reasons ?? []).map((reason) => <option key={reason} value={reason}>{reason}</option>)}
            {filters.reason !== 'all' && !(data?.reasons ?? []).includes(filters.reason) && <option value={filters.reason}>{filters.reason}</option>}
          </select>
          <select className={inputClass} aria-label="Lọc theo trạng thái đánh giá" value={filters.reviewStatus} onChange={(event) => setFilter({ reviewStatus: event.target.value })}>
            {REVIEW_STATUS_FILTERS.map((value) => <option key={value} value={value}>{value === 'all' ? 'Mọi trạng thái đánh giá' : `Đánh giá ${reviewStatusMeta(value).label.toLowerCase()}`}</option>)}
          </select>
        </div>

        <div className="mt-5">
          {status === 'loading' && <OwnerLoading rows={3} />}
          {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
          {status === 'empty' && <OwnerEmpty title="Không có báo cáo nào" description={filters.status === 'pending' ? 'Không có báo cáo đánh giá nào đang chờ xử lý.' : 'Thử đổi bộ lọc hoặc từ khóa.'} />}
          {status === 'success' && (
            <>
              <ul className="grid gap-3" data-testid="review-report-list">
                {data.reports.map((item) => {
                  const meta = reportStatusMeta(item.status)
                  const reviewMeta = reviewStatusMeta(item.review.status)
                  return (
                    <li key={item.id} data-testid={`review-report-${item.id}`} className="rounded-xl border border-slate-200 p-4">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', meta.className)} data-testid="review-report-status">{meta.label}</span>
                          <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-700">{item.reason}</span>
                          <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', reviewMeta.className)} data-testid="review-report-review-status">{item.review.exists ? reviewMeta.label : 'Đánh giá không còn'}</span>
                        </div>
                        <button type="button" className="inline-flex h-9 items-center gap-1 rounded-lg bg-slate-800 px-3 text-xs font-semibold text-white" aria-label={`Xem báo cáo đánh giá của ${item.review.reviewerName || 'đánh giá đã xóa'}`} onClick={() => setSelectedId(item.id)}>{item.status === 'pending' ? 'Xem và xử lý' : 'Chi tiết'}<ChevronRight className="size-4" /></button>
                      </div>
                      {item.review.exists ? (
                        <div className="mt-2">
                          <Stars rating={item.review.rating} />
                          <p className="mt-1 text-sm font-medium text-slate-700" data-testid="review-report-comment">{item.review.comment || 'Không có nhận xét bằng chữ.'}</p>
                          <p className="mt-1 text-xs font-medium text-slate-500">{item.review.reviewerName} đánh giá {item.review.reviewedName}</p>
                        </div>
                      ) : <p className="mt-2 text-sm font-medium text-slate-500">Đánh giá không còn tồn tại.</p>}
                      <p className="mt-1 text-xs font-medium text-slate-500">{item.reporterName} báo cáo · {formatDateTime(item.createdAt)}{item.handledAt ? ` · xử lý bởi ${item.handledBy}` : ''}</p>
                    </li>
                  )
                })}
              </ul>
              {data.totalPages > 1 && (
                <div className="mt-5 flex items-center justify-between gap-3">
                  <button type="button" className={secondaryButtonClass} disabled={filters.page === 0} onClick={() => setFilters((current) => ({ ...current, page: current.page - 1 }))}>Trang trước</button>
                  <p className="text-sm font-semibold text-slate-600">Trang {data.page + 1}/{data.totalPages} · {data.totalElements} báo cáo</p>
                  <button type="button" className={secondaryButtonClass} disabled={filters.page + 1 >= data.totalPages} onClick={() => setFilters((current) => ({ ...current, page: current.page + 1 }))}>Trang sau</button>
                </div>
              )}
            </>
          )}
        </div>
      </OwnerCard>
      {selectedId && <ReviewReportDrawer reportId={selectedId} onClose={() => setSelectedId(null)} onChanged={(message) => { setNotice(message); reload() }} />}
    </AdminLayout>
  )
}

export default AdminReviewReportsPage
