import { useState } from 'react'
import { Link } from 'react-router-dom'
import { AlertTriangle } from 'lucide-react'
import { fetchVenueStats, reactivateVenue, VENUE_STAT_SORTS } from '../../../api/adminDisputes'
import { useApiResource } from '../../../hooks/useApiResource'
import { useDebounced } from '../../../hooks/useDebounced'
import { classNames, formatCurrency } from '../../../shared/utils'
import { ConfirmDialog } from '../../disputes/ConfirmDialog'
import { OwnerEmpty, OwnerError, OwnerLoading } from '../../owner/components/OwnerUi'
import { inputClass, secondaryButtonClass } from '../../owner/ownerStyles'

/** 2.3.103 - thống kê báo cáo theo sân; 2.3.104 - cờ sắp chạm ngưỡng, sân bị tạm ngưng và nút bật lại. */
function DisputeVenueStats() {
  const [filters, setFilters] = useState({ from: '', to: '', sort: 'lost', page: 0 })
  const [query, setQuery] = useState('')
  const [reactivating, setReactivating] = useState(null)
  const [note, setNote] = useState('')
  const [notice, setNotice] = useState('')
  const q = useDebounced(query)
  const invalidRange = filters.from && filters.to && filters.to < filters.from
  const { status, data, error, reload } = useApiResource(
    () => (invalidRange ? Promise.resolve({ items: [], page: 0, totalPages: 0, totalElements: 0, suspendThreshold: 3, suspendWindowDays: 90 }) : fetchVenueStats({ ...filters, q })),
    [filters.from, filters.to, filters.sort, filters.page, q],
    { isEmpty: (result) => result.items.length === 0 },
  )

  function setFilter(patch) {
    setFilters((current) => ({ ...current, page: 0, ...patch }))
  }

  async function confirmReactivate() {
    if (note.trim().length < 5) throw new Error('Ghi chú từ 5 đến 1000 ký tự.')
    await reactivateVenue(reactivating.venueId, note)
    setNotice(`Đã bật lại sân ${reactivating.venueName}. Chủ sân đã được thông báo.`)
    setReactivating(null)
    setNote('')
    reload()
  }

  return (
    <div data-testid="venue-stats">
      {notice && <p role="status" data-testid="venue-stats-notice" className="mb-4 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm font-semibold text-emerald-900">{notice}</p>}
      <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_10rem_10rem_12rem]">
        <input className={inputClass} aria-label="Tìm sân hoặc chủ sân" placeholder="Tên sân hoặc tên chủ sân" maxLength={100} value={query} onChange={(event) => { setQuery(event.target.value); setFilters((current) => ({ ...current, page: 0 })) }} />
        <input type="date" className={inputClass} aria-label="Từ ngày" data-testid="vs-from" value={filters.from} onChange={(event) => setFilter({ from: event.target.value })} />
        <input type="date" className={inputClass} aria-label="Đến ngày" data-testid="vs-to" value={filters.to} onChange={(event) => setFilter({ to: event.target.value })} />
        <select className={inputClass} aria-label="Sắp xếp" data-testid="vs-sort" value={filters.sort} onChange={(event) => setFilter({ sort: event.target.value })}>
          {VENUE_STAT_SORTS.map(([value, label]) => <option key={value} value={value}>Sắp xếp: {label}</option>)}
        </select>
      </div>
      {invalidRange && <p role="alert" className="mt-2 text-sm font-semibold text-red-600">Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.</p>}
      {data && <p className="mt-3 text-xs font-medium text-slate-500">Tự động tạm ngưng sân khi bị xử thua từ {data.suspendThreshold} lần trong {data.suspendWindowDays} ngày. Xử thua gồm hoàn tiền toàn phần, một nửa và chủ sân không phản hồi.</p>}

      <div className="mt-4">
        {status === 'loading' && <OwnerLoading rows={3} />}
        {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
        {status === 'empty' && <OwnerEmpty title="Chưa có báo cáo nào" description="Không có sân nào có báo cáo trong khoảng đã chọn." />}
        {status === 'success' && (
          <>
            <ul className="grid gap-3" data-testid="vs-list">
              {data.items.map((item) => {
                const near = item.venueStatus === 'active' && item.lostInWindow >= data.suspendThreshold - 1
                return (
                  <li key={item.venueId} data-testid={`vs-${item.venueId}`} className={classNames('rounded-xl border p-4', item.venueStatus === 'inactive' && item.suspensionReason ? 'border-red-300 bg-red-50/50' : near ? 'border-amber-300 bg-amber-50/50' : 'border-slate-200')}>
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <div className="min-w-0">
                        <p className="text-base font-semibold">{item.venueName}</p>
                        <p className="text-xs font-medium text-slate-500">Chủ sân {item.ownerName}</p>
                      </div>
                      <div className="flex flex-wrap items-center gap-2">
                        {item.venueStatus === 'inactive' && item.suspensionReason && <span className="rounded-full bg-red-100 px-2.5 py-1 text-xs font-bold text-red-700" data-testid="vs-suspended">Đang tạm ngưng</span>}
                        {near && <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2.5 py-1 text-xs font-bold text-amber-800" data-testid="vs-near"><AlertTriangle className="size-3.5" />Sắp chạm ngưỡng ({item.lostInWindow}/{data.suspendThreshold})</span>}
                      </div>
                    </div>
                    <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2 text-xs sm:grid-cols-4 lg:grid-cols-8">
                      {[['Tổng', item.total, 'total'], ['Chờ giải trình', item.awaiting, 'awaiting'], ['Chờ xử lý', item.pending, 'pending'], ['Xử thua', item.lost, 'lost'], ['Bác bỏ', item.rejected, 'rejected'], ['Huỷ', item.cancelled, 'cancelled'], ['Tự xử lý', item.autoResolved, 'auto'], ['Tỷ lệ xử thua', `${item.lostRate}%`, 'rate']].map(([label, value, key]) => (
                        <div key={key}><dt className="font-semibold text-slate-500">{label}</dt><dd className="mt-0.5 text-sm font-semibold" data-testid={`vs-${key}`}>{value}</dd></div>
                      ))}
                    </dl>
                    <p className="mt-2 text-xs font-medium text-slate-600">Tổng tiền quyết định hoàn: <span className="font-semibold" data-testid="vs-refund">{formatCurrency(item.refundAmount)}</span></p>
                    {item.venueStatus === 'inactive' && item.suspensionReason && <p className="mt-2 text-xs font-medium text-red-700" data-testid="vs-reason">{item.suspensionReason}</p>}
                    <div className="mt-3 flex flex-wrap gap-2">
                      <Link to={`/admin/disputes?venueId=${item.venueId}&venueName=${encodeURIComponent(item.venueName)}&status=all`} data-testid="vs-open" className={`${secondaryButtonClass} h-9 px-3 text-xs`}>Xem báo cáo của sân</Link>
                      {item.venueStatus === 'inactive' && item.suspensionReason && (
                        <button type="button" data-testid="vs-reactivate" className="inline-flex h-9 items-center rounded-lg bg-slate-800 px-3 text-xs font-semibold text-white" onClick={() => { setNote(''); setReactivating(item) }}>Bật lại sân</button>
                      )}
                    </div>
                  </li>
                )
              })}
            </ul>
            <div className="mt-5 flex items-center justify-between gap-3">
              <button type="button" className={secondaryButtonClass} disabled={filters.page === 0} onClick={() => setFilters((current) => ({ ...current, page: current.page - 1 }))}>Trang trước</button>
              <p className="text-sm font-semibold text-slate-600" data-testid="vs-pager">Trang {data.page + 1}/{data.totalPages} · {data.totalElements} sân</p>
              <button type="button" className={secondaryButtonClass} disabled={filters.page + 1 >= data.totalPages} onClick={() => setFilters((current) => ({ ...current, page: current.page + 1 }))}>Trang sau</button>
            </div>
          </>
        )}
      </div>
      {reactivating && (
        <ConfirmDialog title="Bật lại sân?" confirmLabel="Bật lại sân" onConfirm={confirmReactivate} onClose={() => setReactivating(null)} testId="vs-dialog">
          <p>Sân {reactivating.venueName} sẽ hoạt động trở lại. Các lần xử thua cũ vẫn được tính; sân chỉ bị tạm ngưng lại khi có thêm một lần xử thua mới.</p>
          <textarea data-testid="vs-note" aria-label="Ghi chú bật lại sân" placeholder="Ghi chú (bắt buộc)" className="mt-3 h-24 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" maxLength={1000} value={note} onChange={(event) => setNote(event.target.value)} />
        </ConfirmDialog>
      )}
    </div>
  )
}

export { DisputeVenueStats }
