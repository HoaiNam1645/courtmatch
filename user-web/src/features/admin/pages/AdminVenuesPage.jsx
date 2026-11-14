import { useState } from 'react'
import { CheckCircle2, ChevronRight, Search, X } from 'lucide-react'
import { APPROVAL_TABS, approvalMeta, DA_NANG_DISTRICTS, fetchAdminVenues } from '../../../api/adminVenues'
import { useApiResource } from '../../../hooks/useApiResource'
import { useDebounced } from '../../../hooks/useDebounced'
import { classNames } from '../../../shared/utils'
import { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading } from '../../owner/components/OwnerUi'
import { formatDateTime } from '../../owner/ownerDates'
import { inputClass, secondaryButtonClass } from '../../owner/ownerStyles'
import { AdminLayout } from '../components/AdminLayout'
import { VenueReviewDrawer } from '../components/VenueReviewDrawer'

/** 2.3.34 — duyệt sân cầu lông. */
function AdminVenuesPage() {
  const [filters, setFilters] = useState({ approval: 'pending', district: 'all', page: 0 })
  const [query, setQuery] = useState('')
  const [selectedId, setSelectedId] = useState(null)
  const [notice, setNotice] = useState('')
  const q = useDebounced(query)
  const { status, data, error, reload } = useApiResource(
    () => fetchAdminVenues({ ...filters, q }),
    [filters.approval, filters.district, filters.page, q],
    { isEmpty: (result) => result.venues.length === 0 },
  )
  const counts = data?.approvalCounts ?? {}
  const total = Object.values(counts).reduce((sum, value) => sum + value, 0)

  function setFilter(patch) {
    setFilters((current) => ({ ...current, page: 0, ...patch }))
  }

  return (
    <AdminLayout eyebrow="2.3.34 · Duyệt sân" title="Duyệt sân cầu lông" description="Xem sân chủ sân gửi lên và duyệt để hiển thị cho người chơi.">
      {notice && <p role="status" className="mb-5 flex items-start gap-2 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-semibold text-emerald-800" data-testid="admin-notice"><CheckCircle2 className="mt-0.5 size-4 shrink-0" />{notice}</p>}
      <OwnerCard>
        <div className="flex gap-2 overflow-x-auto pb-1" role="tablist" aria-label="Trạng thái duyệt">
          {APPROVAL_TABS.map((tab) => (
            <button key={tab} type="button" role="tab" aria-selected={filters.approval === tab} data-testid={`venue-tab-${tab}`} className={classNames('flex h-10 shrink-0 items-center gap-2 rounded-lg px-4 text-sm font-semibold', filters.approval === tab ? 'bg-slate-800 text-white' : 'bg-slate-100 text-slate-600')} onClick={() => setFilter({ approval: tab })}>
              {tab === 'all' ? 'Tất cả' : approvalMeta(tab).label}
              <span className={classNames('rounded-full px-2 text-xs font-bold', filters.approval === tab ? 'bg-white/20' : 'bg-slate-200')}>{tab === 'all' ? total : counts[tab] ?? 0}</span>
            </button>
          ))}
        </div>
        <div className="mt-4 grid gap-3 sm:grid-cols-[minmax(0,1fr)_14rem]">
          <label className="flex h-11 items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 focus-within:border-slate-700">
            <Search className="size-4 shrink-0 text-slate-400" />
            <input className="min-w-0 flex-1 text-sm outline-none placeholder:text-slate-400" aria-label="Tìm sân" placeholder="Tên hoặc địa chỉ sân" value={query} maxLength={100} onChange={(event) => { setQuery(event.target.value); setFilters((current) => ({ ...current, page: 0 })) }} />
            {query && <button type="button" aria-label="Xóa tìm kiếm" onClick={() => setQuery('')}><X className="size-4 text-slate-400" /></button>}
          </label>
          <select className={inputClass} aria-label="Lọc theo quận" value={filters.district} onChange={(event) => setFilter({ district: event.target.value })}>
            <option value="all">Tất cả quận / huyện</option>
            {DA_NANG_DISTRICTS.map((district) => <option key={district} value={district}>{district}</option>)}
          </select>
        </div>

        <div className="mt-5">
          {status === 'loading' && <OwnerLoading rows={3} />}
          {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
          {status === 'empty' && <OwnerEmpty title="Không có sân nào" description={filters.approval === 'pending' ? 'Không có sân nào đang chờ duyệt.' : 'Thử đổi bộ lọc.'} />}
          {status === 'success' && (
            <>
              <ul className="grid gap-3" data-testid="venue-list">
                {data.venues.map((item) => {
                  const meta = approvalMeta(item.approvalStatus)
                  return (
                    <li key={item.id} data-testid={`venue-${item.name}`} className="rounded-xl border border-slate-200 p-4">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', meta.className)} data-testid="venue-approval">{meta.label}</span>
                        <button type="button" className="inline-flex h-9 items-center gap-1 rounded-lg bg-slate-800 px-3 text-xs font-semibold text-white" aria-label={`Xem chi tiết sân ${item.name}`} onClick={() => setSelectedId(item.id)}>{item.approvalStatus === 'pending' ? 'Xem và duyệt' : 'Chi tiết'}<ChevronRight className="size-4" /></button>
                      </div>
                      <p className="mt-2 text-sm font-semibold text-slate-900">{item.name}</p>
                      <p className="text-sm font-medium text-slate-600">{[item.address, item.district].filter(Boolean).join(', ')}</p>
                      <p className="mt-1 text-xs font-medium text-slate-500">Chủ sân {item.ownerName} · {item.courtCount} sân con · gửi lúc {formatDateTime(item.createdAt)}</p>
                    </li>
                  )
                })}
              </ul>
              {data.totalPages > 1 && (
                <div className="mt-5 flex items-center justify-between gap-3">
                  <button type="button" className={secondaryButtonClass} disabled={filters.page === 0} onClick={() => setFilters((current) => ({ ...current, page: current.page - 1 }))}>Trang trước</button>
                  <p className="text-sm font-semibold text-slate-600">Trang {data.page + 1}/{data.totalPages} · {data.totalElements} sân</p>
                  <button type="button" className={secondaryButtonClass} disabled={filters.page + 1 >= data.totalPages} onClick={() => setFilters((current) => ({ ...current, page: current.page + 1 }))}>Trang sau</button>
                </div>
              )}
            </>
          )}
        </div>
      </OwnerCard>
      {selectedId && <VenueReviewDrawer venueId={selectedId} onClose={() => setSelectedId(null)} onChanged={(message) => { setNotice(message); reload() }} />}
    </AdminLayout>
  )
}

export default AdminVenuesPage
