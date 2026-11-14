import { useState } from 'react'
import { commissionStatusMeta } from '../../../api/ownerCommissions'
import { fetchDebtBookings } from '../../../api/ownerCommissionDebt'
import { fetchOwnerVenues } from '../../../api/ownerVenues'
import { useApiResource } from '../../../hooks/useApiResource'
import { classNames, formatCurrency } from '../../../shared/utils'
import { formatDateTime } from '../ownerDates'
import { inputClass, secondaryButtonClass } from '../ownerStyles'
import { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading } from './OwnerUi'

const BUCKETS = [
  { id: 'all', label: 'Tất cả' },
  { id: 'overdue', label: 'Quá hạn' },
  { id: 'upcoming', label: 'Chưa đến hạn' },
]

/** 2.3.21 — các booking cấu thành công nợ hoa hồng, tổng hợp từ hoa hồng từng booking. */
function DebtBookingsCard() {
  const [filters, setFilters] = useState({ venueId: '', bucket: 'all', page: 0 })
  const venues = useApiResource(() => fetchOwnerVenues(), [])
  const { status, data, error, reload } = useApiResource(
    () => fetchDebtBookings(filters),
    [filters.venueId, filters.bucket, filters.page],
    { isEmpty: (result) => result.bookings.length === 0 },
  )

  function setFilter(patch) {
    setFilters((current) => ({ ...current, page: 0, ...patch }))
  }

  return (
    <OwnerCard title="Booking cấu thành công nợ" description="Mỗi dòng là hoa hồng của một booking chưa đối soát, hạn sớm nhất ở trên cùng.">
      <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_minmax(0,16rem)]">
        <div className="flex gap-2 overflow-x-auto" role="tablist" aria-label="Nhóm công nợ">
          {BUCKETS.map((bucket) => (
            <button key={bucket.id} type="button" role="tab" aria-selected={filters.bucket === bucket.id} data-testid={`debt-bucket-${bucket.id}`} className={classNames('h-10 shrink-0 rounded-lg px-4 text-sm font-semibold', filters.bucket === bucket.id ? 'bg-emerald-700 text-white' : 'bg-stone-50 text-stone-600')} onClick={() => setFilter({ bucket: bucket.id })}>{bucket.label}</button>
          ))}
        </div>
        <select className={inputClass} aria-label="Lọc công nợ theo sân" value={filters.venueId} onChange={(event) => setFilter({ venueId: event.target.value })}>
          <option value="">Tất cả sân</option>
          {(venues.data?.venues ?? []).map((venue) => <option key={venue.id} value={venue.id}>{venue.name}</option>)}
        </select>
      </div>

      <div className="mt-5">
        {status === 'loading' && <OwnerLoading rows={3} />}
        {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
        {status === 'empty' && <OwnerEmpty title="Không có booking nào cấu thành công nợ" description="Các booking có hoa hồng chưa đối soát sẽ hiện ở đây." />}
        {status === 'success' && (
          <>
            <p className="mb-3 text-sm font-semibold text-stone-600">Tổng hoa hồng của danh sách: <span className="text-lg font-semibold text-emerald-800" data-testid="debt-list-total">{formatCurrency(data.totalAmount)}</span></p>
            <ul className="grid gap-3" data-testid="debt-bookings">
              {data.bookings.map((item) => {
                const meta = commissionStatusMeta(item.status)
                return (
                  <li key={item.bookingId} data-testid={`debt-booking-${item.bookingCode}`} className="rounded-xl border border-emerald-100 p-4">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', meta.className)}>{meta.label}</span>
                        {item.overdue && <span className="rounded-full bg-red-100 px-2.5 py-1 text-xs font-bold text-red-700" data-testid="debt-booking-overdue">Quá hạn</span>}
                        <span className="text-xs font-bold text-stone-400">{item.bookingCode}</span>
                      </div>
                      <p className="text-base font-semibold text-emerald-800" data-testid="debt-booking-amount">{formatCurrency(item.commissionAmount)}</p>
                    </div>
                    <p className="mt-2 text-sm font-semibold text-emerald-950">{item.venueName}</p>
                    <p className="text-sm font-medium text-stone-600">{item.playerName} · doanh thu gộp {formatCurrency(item.grossAmount)}</p>
                    <p className="mt-1 text-xs font-medium text-stone-500">Ghi nhận {formatDateTime(item.calculatedAt)} · hạn thanh toán {formatDateTime(item.dueAt)}</p>
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
  )
}

export { DebtBookingsCard }
