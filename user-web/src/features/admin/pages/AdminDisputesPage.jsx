import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ChevronRight, ImageIcon, Search, X } from 'lucide-react'
import { ADMIN_DISPUTE_STATUS, ADMIN_DISPUTE_TABS, DISPUTE_CATEGORY_OPTIONS, fetchAdminDisputes } from '../../../api/adminDisputes'
import { RESOLUTION_LABEL } from '../../../api/bookingDisputes'
import { useApiResource } from '../../../hooks/useApiResource'
import { useDebounced } from '../../../hooks/useDebounced'
import { classNames, formatCurrency } from '../../../shared/utils'
import { elapsedText, formatDateTime as formatDisputeTime } from '../../disputes/disputeUi'
import { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading } from '../../owner/components/OwnerUi'
import { inputClass, secondaryButtonClass } from '../../owner/ownerStyles'
import { AdminLayout } from '../components/AdminLayout'

/** 2.3.98 - danh sách báo cáo về đơn đặt sân; mặc định là các báo cáo chủ sân đã giải trình, cũ nhất lên đầu. */
function AdminDisputesPage() {
  const [filters, setFilters] = useState({ status: 'pending', category: 'all', refund: 'all', from: '', to: '', page: 0 })
  const [query, setQuery] = useState('')
  const q = useDebounced(query)
  const invalidRange = filters.from && filters.to && filters.to < filters.from
  const { status, data, error, reload } = useApiResource(
    () => (invalidRange ? Promise.resolve({ items: [], page: 0, totalPages: 0, totalElements: 0, counts: {} }) : fetchAdminDisputes({ ...filters, q })),
    [filters.status, filters.category, filters.refund, filters.from, filters.to, filters.page, q],
    { isEmpty: (result) => result.items.length === 0 },
  )
  const counts = data?.counts ?? {}

  function setFilter(patch) {
    setFilters((current) => ({ ...current, page: 0, ...patch }))
  }

  return (
    <AdminLayout eyebrow="2.3.98 · Báo cáo đơn đặt sân" title="Báo cáo đơn đặt sân" description="Báo cáo của người chơi về đơn đặt sân. Xem giải trình của chủ sân và quyết định hoàn tiền.">
      <OwnerCard>
        <div className="flex gap-2 overflow-x-auto pb-1" role="tablist" aria-label="Trạng thái báo cáo">
          {ADMIN_DISPUTE_TABS.map(([id, label]) => (
            <button key={id} type="button" role="tab" aria-selected={filters.status === id} data-testid={`ad-tab-${id}`} onClick={() => setFilter({ status: id })}
              className={classNames('flex h-10 shrink-0 items-center gap-2 whitespace-nowrap rounded-lg px-4 text-sm font-semibold', filters.status === id ? 'bg-slate-800 text-white' : 'bg-slate-100 text-slate-600')}>
              {label}
              <span className={classNames('rounded-full px-2 text-xs font-bold', filters.status === id ? 'bg-white/20' : 'bg-slate-200')} data-testid={`ad-count-${id}`}>{counts[id] ?? 0}</span>
            </button>
          ))}
        </div>
        <div className="mt-4 grid gap-3 lg:grid-cols-[minmax(0,1fr)_11rem_11rem]">
          <label className="flex h-11 items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 focus-within:border-slate-700">
            <Search className="size-4 shrink-0 text-slate-400" />
            <input className="min-w-0 flex-1 text-sm outline-none placeholder:text-slate-400" aria-label="Tìm báo cáo" placeholder="Mã đơn, tên người chơi hoặc tên sân" value={query} maxLength={100} onChange={(event) => { setQuery(event.target.value); setFilters((current) => ({ ...current, page: 0 })) }} />
            {query && <button type="button" aria-label="Xóa tìm kiếm" onClick={() => setQuery('')}><X className="size-4 text-slate-400" /></button>}
          </label>
          <select className={inputClass} aria-label="Lọc theo loại" data-testid="ad-category" value={filters.category} onChange={(event) => setFilter({ category: event.target.value })}>
            <option value="all">Mọi loại báo cáo</option>
            {DISPUTE_CATEGORY_OPTIONS.map(([code, label]) => <option key={code} value={code}>{label}</option>)}
          </select>
          <select className={inputClass} aria-label="Lọc theo yêu cầu hoàn tiền" data-testid="ad-refund" value={filters.refund} onChange={(event) => setFilter({ refund: event.target.value })}>
            <option value="all">Có hoặc không hoàn tiền</option>
            <option value="yes">Có yêu cầu hoàn tiền</option>
            <option value="no">Không yêu cầu hoàn tiền</option>
          </select>
        </div>
        <div className="mt-3 grid grid-cols-2 gap-3 sm:max-w-md">
          <label className="grid gap-1 text-xs font-semibold text-slate-600">Từ ngày<input type="date" className={inputClass} data-testid="ad-from" value={filters.from} onChange={(event) => setFilter({ from: event.target.value })} /></label>
          <label className="grid gap-1 text-xs font-semibold text-slate-600">Đến ngày<input type="date" className={inputClass} data-testid="ad-to" value={filters.to} onChange={(event) => setFilter({ to: event.target.value })} /></label>
        </div>
        {invalidRange && <p role="alert" data-testid="ad-range-error" className="mt-2 text-sm font-semibold text-red-600">Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.</p>}

        <div className="mt-5">
          {status === 'loading' && <OwnerLoading rows={3} />}
          {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
          {status === 'empty' && <OwnerEmpty title="Không có báo cáo nào" description={filters.status === 'pending' ? 'Không có báo cáo nào đang chờ xử lý.' : 'Thử đổi bộ lọc hoặc từ khóa.'} />}
          {status === 'success' && (
            <>
              <ul className="grid gap-3" data-testid="ad-list">
                {data.items.map((item) => {
                  const meta = ADMIN_DISPUTE_STATUS[item.status] ?? { label: item.status, className: 'bg-slate-100 text-slate-600' }
                  return (
                    <li key={item.id}>
                      <Link to={`/admin/disputes/${item.id}`} data-testid={`ad-${item.id}`} data-status={item.status} className="block rounded-xl border border-slate-200 p-4 hover:border-slate-400">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div className="flex flex-wrap items-center gap-2">
                            <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', meta.className)}>{meta.label}</span>
                            <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-700">{DISPUTE_CATEGORY_OPTIONS.find(([code]) => code === item.category)?.[1] ?? item.categoryLabel}</span>
                            {item.refundRequested && <span className="rounded-full bg-emerald-100 px-2.5 py-1 text-xs font-bold text-emerald-800" data-testid="ad-refund-badge">Yêu cầu hoàn {formatCurrency(item.refundAmount)}</span>}
                          </div>
                          <ChevronRight className="size-5 text-slate-400" />
                        </div>
                        <p className="mt-3 text-base font-semibold">{item.venueName} <span className="font-medium text-slate-500">· đơn {item.bookingCode}</span></p>
                        <p className="mt-1 text-xs font-medium text-slate-500">Người chơi {item.playerName} · chủ sân {item.ownerName}</p>
                        <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs font-semibold text-slate-600">
                          <span className="inline-flex items-center gap-1" data-testid="ad-images"><ImageIcon className="size-3.5" />{item.playerImages} ảnh người chơi · {item.ownerImages} ảnh chủ sân</span>
                          <span data-testid="ad-waiting">{item.status === 'reviewing' ? `Chờ xử lý từ ${formatDisputeTime(item.waitingSince)} (${elapsedText(item.waitingSince)})` : `Gửi lúc ${formatDisputeTime(item.createdAt)}`}</span>
                          <span data-testid="ad-prior">Sân có {item.priorVenueDisputes} báo cáo trước đó</span>
                          {item.resolution && <span className="text-emerald-800">{RESOLUTION_LABEL[item.resolution] ?? item.resolution}</span>}
                        </div>
                      </Link>
                    </li>
                  )
                })}
              </ul>
              <div className="mt-5 flex items-center justify-between gap-3">
                <button type="button" className={secondaryButtonClass} disabled={filters.page === 0} onClick={() => setFilters((current) => ({ ...current, page: current.page - 1 }))}>Trang trước</button>
                <p className="text-sm font-semibold text-slate-600" data-testid="ad-pager">Trang {data.page + 1}/{data.totalPages} · {data.totalElements} báo cáo</p>
                <button type="button" className={secondaryButtonClass} disabled={filters.page + 1 >= data.totalPages} onClick={() => setFilters((current) => ({ ...current, page: current.page + 1 }))}>Trang sau</button>
              </div>
            </>
          )}
        </div>
      </OwnerCard>
    </AdminLayout>
  )
}

export default AdminDisputesPage
