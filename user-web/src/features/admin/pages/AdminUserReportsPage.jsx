import { useState } from 'react'
import { CheckCircle2, ChevronRight, Search, X } from 'lucide-react'
import { fetchUserReports, REPORT_TABS, reportStatusMeta } from '../../../api/adminReports'
import { useApiResource } from '../../../hooks/useApiResource'
import { useDebounced } from '../../../hooks/useDebounced'
import { classNames } from '../../../shared/utils'
import { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading } from '../../owner/components/OwnerUi'
import { formatDateTime } from '../../owner/ownerDates'
import { inputClass, secondaryButtonClass } from '../../owner/ownerStyles'
import { AdminLayout } from '../components/AdminLayout'
import { UserReportDrawer } from '../components/UserReportDrawer'

/** 2.3.36 — quản lý người dùng bị báo cáo. */
function AdminUserReportsPage() {
  const [filters, setFilters] = useState({ status: 'pending', reason: 'all', page: 0 })
  const [query, setQuery] = useState('')
  const [selectedId, setSelectedId] = useState(null)
  const [notice, setNotice] = useState('')
  const q = useDebounced(query)
  const { status, data, error, reload } = useApiResource(
    () => fetchUserReports({ ...filters, q }),
    [filters.status, filters.reason, filters.page, q],
    { isEmpty: (result) => result.reports.length === 0 },
  )
  const counts = data?.statusCounts ?? {}
  const total = Object.values(counts).reduce((sum, value) => sum + value, 0)

  function setFilter(patch) {
    setFilters((current) => ({ ...current, page: 0, ...patch }))
  }

  return (
    <AdminLayout eyebrow="2.3.36 · Báo cáo người dùng" title="Người dùng bị báo cáo" description="Xem xét báo cáo vi phạm, kết luận và xử lý tài khoản bị báo cáo.">
      {notice && <p role="status" className="mb-5 flex items-start gap-2 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-semibold text-emerald-800" data-testid="admin-notice"><CheckCircle2 className="mt-0.5 size-4 shrink-0" />{notice}</p>}
      <OwnerCard>
        <div className="flex gap-2 overflow-x-auto pb-1" role="tablist" aria-label="Trạng thái báo cáo">
          {REPORT_TABS.map((tab) => (
            <button key={tab} type="button" role="tab" aria-selected={filters.status === tab} data-testid={`report-tab-${tab}`} className={classNames('flex h-10 shrink-0 items-center gap-2 rounded-lg px-4 text-sm font-semibold', filters.status === tab ? 'bg-slate-800 text-white' : 'bg-slate-100 text-slate-600')} onClick={() => setFilter({ status: tab })}>
              {tab === 'all' ? 'Tất cả' : reportStatusMeta(tab).label}
              <span className={classNames('rounded-full px-2 text-xs font-bold', filters.status === tab ? 'bg-white/20' : 'bg-slate-200')}>{tab === 'all' ? total : counts[tab] ?? 0}</span>
            </button>
          ))}
        </div>
        <div className="mt-4 grid gap-3 sm:grid-cols-[minmax(0,1fr)_14rem]">
          <label className="flex h-11 items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 focus-within:border-slate-700">
            <Search className="size-4 shrink-0 text-slate-400" />
            <input className="min-w-0 flex-1 text-sm outline-none placeholder:text-slate-400" aria-label="Tìm báo cáo" placeholder="Tên hoặc email người báo cáo / bị báo cáo" value={query} maxLength={100} onChange={(event) => { setQuery(event.target.value); setFilters((current) => ({ ...current, page: 0 })) }} />
            {query && <button type="button" aria-label="Xóa tìm kiếm" onClick={() => setQuery('')}><X className="size-4 text-slate-400" /></button>}
          </label>
          <select className={inputClass} aria-label="Lọc theo lý do" value={filters.reason} onChange={(event) => setFilter({ reason: event.target.value })}>
            <option value="all">Tất cả lý do</option>
            {(data?.reasons ?? []).map((reason) => <option key={reason} value={reason}>{reason}</option>)}
            {filters.reason !== 'all' && !(data?.reasons ?? []).includes(filters.reason) && <option value={filters.reason}>{filters.reason}</option>}
          </select>
        </div>

        <div className="mt-5">
          {status === 'loading' && <OwnerLoading rows={3} />}
          {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
          {status === 'empty' && <OwnerEmpty title="Không có báo cáo nào" description={filters.status === 'pending' ? 'Không có báo cáo nào đang chờ xử lý.' : 'Thử đổi bộ lọc hoặc từ khóa.'} />}
          {status === 'success' && (
            <>
              <ul className="grid gap-3" data-testid="report-list">
                {data.reports.map((item) => {
                  const meta = reportStatusMeta(item.status)
                  return (
                    <li key={item.id} data-testid={`report-${item.id}`} className="rounded-xl border border-slate-200 p-4">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', meta.className)} data-testid="report-status">{meta.label}</span>
                          <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-700" data-testid="report-reason">{item.reason}</span>
                        </div>
                        <button type="button" className="inline-flex h-9 items-center gap-1 rounded-lg bg-slate-800 px-3 text-xs font-semibold text-white" aria-label={`Xem báo cáo về ${item.target.fullName || 'tài khoản đã xóa'}`} onClick={() => setSelectedId(item.id)}>{item.status === 'pending' ? 'Xem và xử lý' : 'Chi tiết'}<ChevronRight className="size-4" /></button>
                      </div>
                      <p className="mt-2 text-sm font-semibold text-slate-900" data-testid="report-target-name">{item.target.exists ? item.target.fullName : 'Tài khoản không còn tồn tại'}{item.target.exists && item.target.status !== 'active' && <span className="ml-2 rounded-full bg-red-100 px-2 py-0.5 text-[11px] font-bold text-red-700">{item.target.status === 'banned' ? 'Đã khóa' : 'Ngừng hoạt động'}</span>}</p>
                      {item.descriptionPreview && <p className="mt-1 text-sm font-medium text-slate-600">{item.descriptionPreview}</p>}
                      <p className="mt-1 text-xs font-medium text-slate-500">Người báo cáo {item.reporter.fullName} · {formatDateTime(item.createdAt)}{item.handledAt ? ` · xử lý bởi ${item.handledBy}` : ''}</p>
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
      {selectedId && <UserReportDrawer reportId={selectedId} onClose={() => setSelectedId(null)} onChanged={(message) => { setNotice(message); reload() }} />}
    </AdminLayout>
  )
}

export default AdminUserReportsPage
