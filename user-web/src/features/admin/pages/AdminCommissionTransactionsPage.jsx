import { useRef, useState } from 'react'
import { Search, X } from 'lucide-react'
import { fetchCommissionTransactions } from '../../../api/adminCommissionTransactions'
import { COMMISSION_TABS, commissionStatusMeta } from '../../../api/ownerCommissions'
import { settlementStatusMeta } from '../../../api/ownerSettlements'
import { useApiResource } from '../../../hooks/useApiResource'
import { useDebounced } from '../../../hooks/useDebounced'
import { classNames, formatCurrency } from '../../../shared/utils'
import { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading } from '../../owner/components/OwnerUi'
import { formatDateTime, formatIsoDate } from '../../owner/ownerDates'
import { inputClass, secondaryButtonClass } from '../../owner/ownerStyles'
import { AdminLayout } from '../components/AdminLayout'
import { CommissionDetailDrawer } from '../components/CommissionDetailDrawer'

const EMPTY_FILTERS = { status: 'all', venueId: '', settlementId: '', from: '', to: '', overdue: false, page: 0 }

/** 2.3.43 — giao dịch đối soát: hoa hồng của từng booking kèm trạng thái, hạn thanh toán và kỳ đối soát. */
function AdminCommissionTransactionsPage() {
  const [filters, setFilters] = useState(EMPTY_FILTERS)
  const [query, setQuery] = useState('')
  const [openId, setOpenId] = useState(null)
  const q = useDebounced(query)
  const invalidRange = Boolean(filters.from && filters.to && filters.to < filters.from)
  const { status, data, error, reload } = useApiResource(
    () => (invalidRange ? Promise.resolve(null) : fetchCommissionTransactions({ ...filters, q })),
    [filters.status, filters.venueId, filters.settlementId, filters.from, filters.to, filters.overdue, filters.page, q, invalidRange],
    { isEmpty: (result) => result !== null && result.rows.length === 0 },
  )
  // Giữ danh sách sân và kỳ đối soát của lần tải gần nhất để ô lọc không trống khi đang tải lại.
  const optionsRef = useRef({ venues: [], settlements: [] })
  if (data) optionsRef.current = { venues: data.venues, settlements: data.settlements }
  const { venues, settlements } = optionsRef.current
  const filtered = filters.status !== 'all' || filters.venueId || filters.settlementId || filters.from || filters.to || filters.overdue || q

  function setFilter(patch) {
    setFilters((current) => ({ ...current, page: 0, ...patch }))
  }

  return (
    <AdminLayout eyebrow="2.3.43 – 2.3.44 · 2.3.51 · Giao dịch đối soát" title="Giao dịch đối soát" description="Hoa hồng của từng booking: trạng thái, hạn thanh toán và kỳ đối soát đã gán.">
      <OwnerCard>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <label className="flex h-11 items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 focus-within:border-slate-700 sm:col-span-2 lg:col-span-1">
            <Search className="size-4 shrink-0 text-slate-400" />
            <input className="min-w-0 flex-1 text-sm outline-none placeholder:text-slate-400" aria-label="Tìm mã booking" placeholder="Mã booking" value={query} maxLength={100} onChange={(event) => { setQuery(event.target.value); setFilters((current) => ({ ...current, page: 0 })) }} />
            {query && <button type="button" aria-label="Xóa tìm kiếm" onClick={() => setQuery('')}><X className="size-4 text-slate-400" /></button>}
          </label>
          <select className={inputClass} aria-label="Lọc theo trạng thái" value={filters.status} onChange={(event) => setFilter({ status: event.target.value })}>
            {COMMISSION_TABS.map((tab) => <option key={tab} value={tab}>{tab === 'all' ? 'Mọi trạng thái' : commissionStatusMeta(tab).label}</option>)}
          </select>
          <select className={inputClass} aria-label="Lọc theo sân" value={filters.venueId} onChange={(event) => setFilter({ venueId: event.target.value })}>
            <option value="">Tất cả sân</option>
            {venues.map((venue) => <option key={venue.id} value={venue.id}>{venue.name}</option>)}
          </select>
          <select className={inputClass} aria-label="Lọc theo kỳ đối soát" value={filters.settlementId} onChange={(event) => setFilter({ settlementId: event.target.value })}>
            <option value="">Mọi kỳ đối soát</option>
            {settlements.map((item) => <option key={item.id} value={item.id}>{item.ownerName} · {formatIsoDate(item.periodFrom)} – {formatIsoDate(item.periodTo)}</option>)}
          </select>
          <label className="block text-xs font-semibold text-slate-600">Ghi nhận từ ngày<input type="date" className={`${inputClass} mt-1`} aria-label="Ghi nhận từ ngày" value={filters.from} onChange={(event) => setFilter({ from: event.target.value })} /></label>
          <label className="block text-xs font-semibold text-slate-600">Ghi nhận đến ngày<input type="date" className={`${inputClass} mt-1`} aria-label="Ghi nhận đến ngày" value={filters.to} onChange={(event) => setFilter({ to: event.target.value })} /></label>
          <label className="flex h-11 items-center gap-2 self-end rounded-lg border border-slate-300 bg-white px-3 text-sm font-semibold text-slate-700 sm:col-span-2 lg:col-span-1">
            <input type="checkbox" checked={filters.overdue} onChange={(event) => setFilter({ overdue: event.target.checked })} data-testid="tx-overdue-only" />Chỉ khoản quá hạn
          </label>
          {filtered && <button type="button" className={`${secondaryButtonClass} self-end`} data-testid="tx-clear" onClick={() => { setFilters(EMPTY_FILTERS); setQuery('') }}>Xóa bộ lọc</button>}
        </div>
        {invalidRange && <p className="mt-2 text-sm font-medium text-red-600" role="alert" data-testid="tx-range-error">Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.</p>}

        <div className="mt-5">
          {status === 'loading' && !data && <OwnerLoading rows={3} />}
          {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
          {status === 'empty' && <OwnerEmpty title="Không có giao dịch nào" description={filtered ? 'Thử bỏ bớt bộ lọc hoặc đổi từ khóa.' : 'Hoa hồng sẽ xuất hiện khi booking hoàn thành.'} />}
          {data && status !== 'empty' && (
            <>
              <div className="mb-4 grid gap-3 rounded-xl bg-slate-50 p-4 sm:grid-cols-3" data-testid="tx-totals">
                <div><p className="text-xs font-semibold text-slate-500">Số giao dịch</p><p className="text-lg font-semibold text-slate-900" data-testid="tx-total-count">{data.totals.count.toLocaleString('vi-VN')}</p></div>
                <div><p className="text-xs font-semibold text-slate-500">Doanh thu gộp</p><p className="text-lg font-semibold text-slate-900" data-testid="tx-total-gross">{formatCurrency(data.totals.grossAmount)}</p></div>
                <div><p className="text-xs font-semibold text-slate-500">Tổng hoa hồng</p><p className="text-lg font-semibold text-slate-900" data-testid="tx-total-commission">{formatCurrency(data.totals.commissionAmount)}</p></div>
              </div>
              <ul className="grid gap-3" data-testid="tx-list">
                {data.rows.map((row) => {
                  const meta = commissionStatusMeta(row.status)
                  return (
                    <li key={row.commissionId} data-testid={`tx-${row.bookingCode}`} className={classNames('rounded-xl border p-4', row.overdue ? 'border-red-200 bg-red-50/40' : 'border-slate-200')}>
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', meta.className)} data-testid="tx-status">{meta.label}</span>
                          {row.overdue && <span className="rounded-full bg-red-100 px-2.5 py-1 text-xs font-bold text-red-700" data-testid="tx-overdue">Quá hạn</span>}
                          <span className="text-xs font-bold text-slate-400">{row.bookingCode}</span>
                        </div>
                        <div className="flex items-center gap-3">
                          <p className="text-base font-semibold text-slate-900" data-testid="tx-commission">{formatCurrency(row.commissionAmount)}</p>
                          <button type="button" className="h-9 rounded-lg border border-slate-300 px-3 text-xs font-semibold text-slate-700 hover:bg-slate-50" aria-label={`Xem chi tiết ${row.bookingCode}`} data-testid="tx-detail" onClick={() => setOpenId(row.commissionId)}>Chi tiết</button>
                        </div>
                      </div>
                      <p className="mt-2 text-sm font-semibold text-slate-900">{row.venueName}</p>
                      <p className="text-xs font-medium text-slate-500">Chủ sân {row.ownerName} · doanh thu gộp {formatCurrency(row.grossAmount)} · ghi nhận {formatDateTime(row.calculatedAt)}</p>
                      <div className="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-xs font-medium text-slate-600">
                        <span data-testid="tx-due">Hạn thanh toán {formatDateTime(row.dueAt)}</span>
                        <span data-testid="tx-settlement">{row.settlement ? <>Kỳ {formatIsoDate(row.settlement.periodFrom)} – {formatIsoDate(row.settlement.periodTo)} ({settlementStatusMeta(row.settlement.status).label})</> : 'Chưa gán kỳ đối soát'}</span>
                        {row.settledAt && <span data-testid="tx-settled">Đối soát xong {formatDateTime(row.settledAt)}</span>}
                      </div>
                    </li>
                  )
                })}
              </ul>
              {data.totalPages > 1 && (
                <div className="mt-5 flex items-center justify-between gap-3">
                  <button type="button" className={secondaryButtonClass} disabled={filters.page === 0} onClick={() => setFilters((current) => ({ ...current, page: current.page - 1 }))}>Trang trước</button>
                  <p className="text-sm font-semibold text-slate-600">Trang {data.page + 1}/{data.totalPages} · {data.totalElements.toLocaleString('vi-VN')} giao dịch</p>
                  <button type="button" className={secondaryButtonClass} disabled={filters.page + 1 >= data.totalPages} onClick={() => setFilters((current) => ({ ...current, page: current.page + 1 }))}>Trang sau</button>
                </div>
              )}
            </>
          )}
        </div>
      </OwnerCard>
      {openId && <CommissionDetailDrawer commissionId={openId} onClose={() => setOpenId(null)} onChanged={reload} />}
    </AdminLayout>
  )
}

export default AdminCommissionTransactionsPage
