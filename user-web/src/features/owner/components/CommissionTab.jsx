import { useState } from 'react'
import { Info } from 'lucide-react'
import { COMMISSION_TABS, commissionStatusMeta, describeFee, fetchCommissions } from '../../../api/ownerCommissions'
import { fetchOwnerVenues } from '../../../api/ownerVenues'
import { useApiResource } from '../../../hooks/useApiResource'
import { classNames, formatCurrency } from '../../../shared/utils'
import { formatDateTime } from '../ownerDates'
import { inputClass, secondaryButtonClass } from '../ownerStyles'
import { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading } from './OwnerUi'

/** 2.3.18 — phí nền tảng của từng booking, chụp theo chính sách lúc ghi nhận. */
function CommissionTab() {
  const [filters, setFilters] = useState({ venueId: '', status: 'all', from: '', to: '', page: 0 })
  const venues = useApiResource(() => fetchOwnerVenues(), [])
  const { status, data, error, reload } = useApiResource(
    () => fetchCommissions(filters),
    [filters.venueId, filters.status, filters.from, filters.to, filters.page],
    { isEmpty: (result) => result.commissions.length === 0 },
  )
  const invalidRange = filters.from && filters.to && filters.to < filters.from

  function setFilter(patch) {
    setFilters((current) => ({ ...current, page: 0, ...patch }))
  }

  return (
    <div className="grid gap-5">
      <div className="flex items-start gap-3 rounded-xl border border-emerald-100 bg-white p-4 text-sm font-medium text-stone-600" data-testid="commission-note">
        <Info className="mt-0.5 size-4 shrink-0 text-emerald-700" />
        <p>
          Hoa hồng được ghi nhận khi booking hoàn thành, theo chính sách đang hiệu lực lúc đó và không đổi khi chính sách thay đổi sau này.
          {data?.activePolicy && <> Chính sách hiện hành: <span className="font-semibold text-emerald-900" data-testid="active-policy">{data.activePolicy.name} · {describeFee(data.activePolicy.feeType, data.activePolicy.feeValue, formatCurrency)}</span>.</>}
        </p>
      </div>

      <OwnerCard>
        <div className="flex gap-2 overflow-x-auto pb-1" role="tablist" aria-label="Trạng thái hoa hồng">
          {COMMISSION_TABS.map((tab) => (
            <button key={tab} type="button" role="tab" aria-selected={filters.status === tab} data-testid={`commission-tab-${tab}`} className={classNames('h-10 shrink-0 rounded-lg px-4 text-sm font-semibold', filters.status === tab ? 'bg-emerald-700 text-white' : 'bg-stone-50 text-stone-600')} onClick={() => setFilter({ status: tab })}>
              {tab === 'all' ? 'Tất cả' : commissionStatusMeta(tab).label}
            </button>
          ))}
        </div>
        <div className="mt-4 grid gap-3 sm:grid-cols-3">
          <select className={inputClass} aria-label="Lọc hoa hồng theo sân" value={filters.venueId} onChange={(event) => setFilter({ venueId: event.target.value })}>
            <option value="">Tất cả sân</option>
            {(venues.data?.venues ?? []).map((venue) => <option key={venue.id} value={venue.id}>{venue.name}</option>)}
          </select>
          <input type="date" className={inputClass} aria-label="Ghi nhận từ ngày" value={filters.from} onChange={(event) => setFilter({ from: event.target.value })} />
          <input type="date" className={inputClass} aria-label="Ghi nhận đến ngày" value={filters.to} onChange={(event) => setFilter({ to: event.target.value })} />
        </div>
        {invalidRange && <p className="mt-2 text-sm font-medium text-red-600" role="alert">Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.</p>}

        <div className="mt-5">
          {status === 'loading' && <OwnerLoading rows={3} />}
          {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
          {status === 'empty' && <OwnerEmpty title="Chưa có hoa hồng nào" description="Hoa hồng phát sinh khi một booking đã xác nhận được chơi xong." />}
          {status === 'success' && (
            <>
              <p className="mb-3 text-sm font-semibold text-stone-600">Tổng hoa hồng của danh sách: <span className="text-lg font-semibold text-emerald-800" data-testid="commission-total">{formatCurrency(data.totalCommission)}</span></p>
              <ul className="grid gap-3" data-testid="commission-list">
                {data.commissions.map((item) => {
                  const meta = commissionStatusMeta(item.status)
                  return (
                    <li key={item.bookingId} data-testid={`commission-${item.bookingCode}`} className="rounded-xl border border-emerald-100 p-4">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', meta.className)} data-testid="commission-status">{meta.label}</span>
                          {item.overdue && <span className="rounded-full bg-red-100 px-2.5 py-1 text-xs font-bold text-red-700" data-testid="commission-overdue">Quá hạn</span>}
                          <span className="text-xs font-bold text-stone-400">{item.bookingCode}</span>
                        </div>
                        <p className="text-base font-semibold text-emerald-800" data-testid="commission-amount">{formatCurrency(item.commissionAmount)}</p>
                      </div>
                      <p className="mt-2 text-sm font-semibold text-emerald-950">{item.venueName}</p>
                      <p className="text-sm font-medium text-stone-600">{item.playerName} · doanh thu gộp {formatCurrency(item.grossAmount)}</p>
                      <p className="mt-1 text-xs font-medium text-stone-500">
                        Phí {describeFee(item.feeType, item.feeValue, formatCurrency)}{item.policyName ? ` (${item.policyName})` : ''} · ghi nhận {formatDateTime(item.calculatedAt)}{item.dueAt ? ` · hạn ${formatDateTime(item.dueAt)}` : ''}
                      </p>
                    </li>
                  )
                })}
              </ul>
              {data.totalPages > 1 && (
                <div className="mt-5 flex items-center justify-between gap-3">
                  <button type="button" className={secondaryButtonClass} disabled={filters.page === 0} onClick={() => setFilters((current) => ({ ...current, page: current.page - 1 }))}>Trang trước</button>
                  <p className="text-sm font-semibold text-stone-600">Trang {data.page + 1}/{data.totalPages} · {data.totalElements} booking</p>
                  <button type="button" className={secondaryButtonClass} disabled={filters.page + 1 >= data.totalPages} onClick={() => setFilters((current) => ({ ...current, page: current.page + 1 }))}>Trang sau</button>
                </div>
              )}
            </>
          )}
        </div>
      </OwnerCard>
    </div>
  )
}

export { CommissionTab }
