import { useState } from 'react'
import { fetchNetIncome } from '../../../api/ownerNetIncome'
import { fetchOwnerVenues } from '../../../api/ownerVenues'
import { useApiResource } from '../../../hooks/useApiResource'
import { formatCurrency } from '../../../shared/utils'
import { formatDateTime, rangeProblem } from '../ownerDates'
import { inputClass, secondaryButtonClass } from '../ownerStyles'
import { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading } from './OwnerUi'

function Total({ label, value, testId, strong }) {
  return (
    <div className="rounded-xl border border-emerald-100 bg-white p-4">
      <p className="text-xs font-semibold text-stone-500">{label}</p>
      <p className={strong ? 'mt-1 text-2xl font-semibold text-emerald-800' : 'mt-1 text-xl font-semibold text-emerald-950'} data-testid={testId}>{value}</p>
    </div>
  )
}

function Line({ label, value, sign, testId, note }) {
  return (
    <div className="flex items-baseline justify-between gap-3 text-sm">
      <dt className="font-medium text-stone-600">{sign} {label}{note && <span className="ml-1 text-xs font-semibold text-amber-700">({note})</span>}</dt>
      <dd className="font-semibold text-stone-800" data-testid={testId}>{formatCurrency(value)}</dd>
    </div>
  )
}

/** 2.3.19 — số thực nhận = doanh thu gộp − hoa hồng Courtly − tiền đã hoàn cho người chơi. */
function NetIncomeTab() {
  const [filters, setFilters] = useState({ venueId: '', from: '', to: '', page: 0 })
  const venues = useApiResource(() => fetchOwnerVenues(), [])
  const problem = rangeProblem(filters.from, filters.to)
  const invalidRange = Boolean(problem)
  const { status, data, error, reload } = useApiResource(
    () => (invalidRange ? Promise.resolve(null) : fetchNetIncome(filters)),
    [filters.venueId, filters.from, filters.to, filters.page, invalidRange],
    { isEmpty: (result) => result !== null && result.items.length === 0 },
  )

  function setFilter(patch) {
    setFilters((current) => ({ ...current, page: 0, ...patch }))
  }

  return (
    <div className="grid gap-5">
      <OwnerCard>
        <div className="grid gap-3 sm:grid-cols-3">
          <select className={inputClass} aria-label="Lọc thực nhận theo sân" value={filters.venueId} onChange={(event) => setFilter({ venueId: event.target.value })}>
            <option value="">Tất cả sân</option>
            {(venues.data?.venues ?? []).map((venue) => <option key={venue.id} value={venue.id}>{venue.name}</option>)}
          </select>
          <input type="date" className={inputClass} aria-label="Thu tiền từ ngày" value={filters.from} onChange={(event) => setFilter({ from: event.target.value })} />
          <input type="date" className={inputClass} aria-label="Thu tiền đến ngày" value={filters.to} onChange={(event) => setFilter({ to: event.target.value })} />
        </div>
        <p className="mt-3 text-xs font-medium text-stone-500">Mặc định 30 ngày gần nhất, tính theo ngày bạn xác nhận thu tiền. Số thực nhận = doanh thu gộp − hoa hồng Courtly − tiền bạn đã hoàn cho người chơi (đã được xác nhận).</p>
        {problem && <p className="mt-2 text-sm font-medium text-red-600" role="alert">{problem}</p>}
      </OwnerCard>

      {!invalidRange && status === 'loading' && <OwnerLoading rows={2} />}
      {!invalidRange && status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
      {!invalidRange && status === 'empty' && <OwnerEmpty title="Chưa có booking nào đã thu tiền trong kỳ này" description="Số thực nhận xuất hiện sau khi bạn xác nhận đã thu tiền của một booking." />}
      {!invalidRange && status === 'success' && data && (
        <>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Total label="Doanh thu gộp" value={formatCurrency(data.totals.grossAmount)} testId="net-total-gross" />
            <Total label="Hoa hồng Courtly" value={formatCurrency(data.totals.commissionAmount)} testId="net-total-commission" />
            <Total label="Đã hoàn cho người chơi" value={formatCurrency(data.totals.refundedAmount)} testId="net-total-refunded" />
            <Total label="Thực nhận" value={formatCurrency(data.totals.netAmount)} testId="net-total-net" strong />
          </div>
          {data.totals.pendingRefundAmount > 0 && (
            <p className="rounded-lg bg-amber-50 px-4 py-3 text-sm font-medium text-amber-800" data-testid="net-pending-refund">
              Còn {formatCurrency(data.totals.pendingRefundAmount)} hoàn tiền đã duyệt nhưng chưa được xác nhận; khoản này chưa được trừ vào số thực nhận.
            </p>
          )}

          <OwnerCard title="Từng booking" description={`${data.totals.bookings} booking đã thu tiền`}>
            <ul className="grid gap-3" data-testid="net-list">
              {data.items.map((item) => (
                <li key={item.paymentId} data-testid={`net-${item.bookingCode}`} className="rounded-xl border border-emerald-100 p-4">
                  <div className="flex flex-wrap items-baseline justify-between gap-2">
                    <p className="text-sm font-semibold text-emerald-950">{item.venueName} · <span className="text-xs font-bold text-stone-400">{item.bookingCode}</span></p>
                    <p className="text-lg font-semibold text-emerald-800" data-testid="net-amount">{formatCurrency(item.netAmount)}</p>
                  </div>
                  <p className="text-xs font-medium text-stone-500">{item.playerName} · thu lúc {formatDateTime(item.confirmedAt)}</p>
                  <dl className="mt-3 grid gap-1 border-t border-emerald-50 pt-3">
                    <Line label="Doanh thu gộp" value={item.grossAmount} sign="" testId="net-gross" />
                    <Line label="Hoa hồng Courtly" value={item.commissionAmount} sign="−" testId="net-commission" note={item.commissionEstimated ? 'ước tính' : null} />
                    <Line label="Đã hoàn cho người chơi" value={item.refundedAmount} sign="−" testId="net-refunded" />
                  </dl>
                </li>
              ))}
            </ul>
            {data.totalPages > 1 && (
              <div className="mt-5 flex items-center justify-between gap-3">
                <button type="button" className={secondaryButtonClass} disabled={filters.page === 0} onClick={() => setFilters((current) => ({ ...current, page: current.page - 1 }))}>Trang trước</button>
                <p className="text-sm font-semibold text-stone-600">Trang {data.page + 1}/{data.totalPages} · {data.totalElements} booking</p>
                <button type="button" className={secondaryButtonClass} disabled={filters.page + 1 >= data.totalPages} onClick={() => setFilters((current) => ({ ...current, page: current.page + 1 }))}>Trang sau</button>
              </div>
            )}
          </OwnerCard>
        </>
      )}
    </div>
  )
}

export { NetIncomeTab }
