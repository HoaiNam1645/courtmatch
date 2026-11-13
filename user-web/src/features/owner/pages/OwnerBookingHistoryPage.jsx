import { useEffect, useState } from 'react'
import { ChevronRight, Search, X } from 'lucide-react'
import { bookingStatusMeta } from '../../../api/ownerBookingRequests'
import { fetchBookingHistory, HISTORY_GROUPS, paymentStatusMeta } from '../../../api/ownerBookings'
import { fetchOwnerVenues } from '../../../api/ownerVenues'
import { useApiResource } from '../../../hooks/useApiResource'
import { classNames, formatCurrency } from '../../../shared/utils'
import { BookingDetailDrawer } from '../components/BookingDetailDrawer'
import { OwnerLayout } from '../components/OwnerLayout'
import { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading } from '../components/OwnerUi'
import { formatDateTime, formatTime } from '../ownerDates'
import { inputClass, secondaryButtonClass } from '../ownerStyles'

/** Chờ người dùng ngừng gõ rồi mới gọi API. */
function useDebounced(value, delay = 300) {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delay)
    return () => window.clearTimeout(timer)
  }, [value, delay])
  return debounced
}

/** 2.3.15 — lịch sử đặt sân của các sân do chủ sân quản lý. */
function OwnerBookingHistoryPage() {
  const [filters, setFilters] = useState({ venueId: '', group: 'all', from: '', to: '', page: 0 })
  const [query, setQuery] = useState('')
  const [selectedId, setSelectedId] = useState(null)
  const q = useDebounced(query)
  const venues = useApiResource(() => fetchOwnerVenues(), [])
  const { status, data, error, reload } = useApiResource(
    () => fetchBookingHistory({ ...filters, q }),
    [filters.venueId, filters.group, filters.from, filters.to, filters.page, q],
    { isEmpty: (result) => result.bookings.length === 0 },
  )

  function setFilter(patch) {
    setFilters((current) => ({ ...current, page: 0, ...patch }))
  }

  const invalidRange = filters.from && filters.to && filters.to < filters.from

  return (
    <OwnerLayout eyebrow="2.3.15 · Lịch sử đặt sân" title="Lịch sử đặt sân" description="Toàn bộ booking của các sân bạn quản lý, kèm trạng thái và tiến trình xử lý.">
      <OwnerCard>
        <div className="flex gap-2 overflow-x-auto pb-1" role="tablist" aria-label="Nhóm trạng thái">
          {HISTORY_GROUPS.map((group) => (
            <button key={group.id} type="button" role="tab" aria-selected={filters.group === group.id} className={classNames('h-10 shrink-0 rounded-lg px-4 text-sm font-semibold', filters.group === group.id ? 'bg-emerald-700 text-white' : 'bg-stone-50 text-stone-600')} onClick={() => setFilter({ group: group.id })}>{group.label}</button>
          ))}
        </div>

        <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <label className="flex h-11 items-center gap-2 rounded-lg border border-stone-300 px-3 sm:col-span-2 lg:col-span-1">
            <Search className="size-4 shrink-0 text-emerald-700" />
            <input className="min-w-0 flex-1 text-sm outline-none placeholder:text-stone-400" aria-label="Tìm booking" placeholder="Mã booking hoặc tên người đặt" value={query} maxLength={100} onChange={(event) => { setQuery(event.target.value); setFilters((current) => ({ ...current, page: 0 })) }} />
            {query && <button type="button" aria-label="Xóa tìm kiếm" onClick={() => setQuery('')}><X className="size-4 text-stone-400" /></button>}
          </label>
          <select className={inputClass} aria-label="Lọc theo sân" value={filters.venueId} onChange={(event) => setFilter({ venueId: event.target.value })}>
            <option value="">Tất cả sân</option>
            {(venues.data?.venues ?? []).map((venue) => <option key={venue.id} value={venue.id}>{venue.name}</option>)}
          </select>
          <input type="date" className={inputClass} aria-label="Chơi từ ngày" value={filters.from} onChange={(event) => setFilter({ from: event.target.value })} />
          <input type="date" className={inputClass} aria-label="Chơi đến ngày" value={filters.to} onChange={(event) => setFilter({ to: event.target.value })} />
        </div>
        {invalidRange && <p className="mt-2 text-sm font-medium text-red-600" role="alert">Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.</p>}

        <div className="mt-5">
          {status === 'loading' && <OwnerLoading rows={3} />}
          {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
          {status === 'empty' && <OwnerEmpty title="Không có booking phù hợp" description={q || filters.from || filters.to || filters.venueId ? 'Thử bỏ bớt bộ lọc hoặc đổi từ khóa tìm kiếm.' : 'Booking mới sẽ xuất hiện tại đây.'} />}
          {status === 'success' && (
            <>
              <ul className="grid gap-3" data-testid="history-list">
                {data.bookings.map((item) => {
                  const meta = bookingStatusMeta(item.status)
                  return (
                    <li key={item.bookingId} data-testid={`history-${item.bookingCode}`} className="rounded-xl border border-emerald-100 p-4">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', meta.className)} data-testid="history-status">{meta.label}</span>
                          {item.paymentStatus && <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', paymentStatusMeta(item.paymentStatus).className)} data-testid="history-payment">{paymentStatusMeta(item.paymentStatus).label}</span>}
                          <span className="text-xs font-bold text-stone-400">{item.bookingCode}</span>
                        </div>
                        <p className="text-base font-semibold text-emerald-700">{formatCurrency(item.totalAmount)}</p>
                      </div>
                      <p className="mt-2 text-sm font-semibold text-emerald-950">{item.venueName} · {item.slots.map((slot) => slot.courtName).join(', ')}</p>
                      <p className="text-sm font-medium text-stone-600">{item.slots[0] ? `${formatDateTime(item.slots[0].startTime)}–${formatTime(item.slots[0].endTime)}` : ''}</p>
                      <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
                        <p className="text-sm font-medium text-stone-500">{item.playerName}{item.playerPhone ? ` · ${item.playerPhone}` : ''}</p>
                        <button type="button" className="inline-flex h-9 items-center gap-1 rounded-lg bg-emerald-700 px-3 text-xs font-semibold text-white" aria-label={`Xem chi tiết ${item.bookingCode}`} onClick={() => setSelectedId(item.bookingId)}>Xem chi tiết<ChevronRight className="size-4" /></button>
                      </div>
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
      {selectedId && <BookingDetailDrawer bookingId={selectedId} onClose={() => setSelectedId(null)} />}
    </OwnerLayout>
  )
}

export default OwnerBookingHistoryPage
