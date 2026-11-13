import { useMemo, useState } from 'react'
import { ArrowDownRight, ArrowUpRight } from 'lucide-react'
import { fetchRevenueStats } from '../../../api/ownerRevenue'
import { fetchOwnerVenues } from '../../../api/ownerVenues'
import { useApiResource } from '../../../hooks/useApiResource'
import { classNames, formatCurrency } from '../../../shared/utils'
import { firstOfMonth, formatIsoDate, shiftIsoDate, todayInVietnam } from '../ownerDates'
import { inputClass } from '../ownerStyles'
import { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading } from './OwnerUi'

const PRESETS = [
  { id: '7d', label: '7 ngày' },
  { id: '30d', label: '30 ngày' },
  { id: 'month', label: 'Tháng này' },
  { id: 'custom', label: 'Tùy chọn' },
]

function rangeOf(preset, custom) {
  const today = todayInVietnam()
  if (preset === '7d') return { from: shiftIsoDate(today, -6), to: today }
  if (preset === 'month') return { from: firstOfMonth(today), to: today }
  if (preset === 'custom') return custom
  return { from: shiftIsoDate(today, -29), to: today }
}

function Kpi({ label, value, testId, children }) {
  return (
    <div className="rounded-xl border border-emerald-100 bg-white p-4">
      <p className="text-xs font-semibold text-stone-500">{label}</p>
      <p className="mt-1 text-2xl font-semibold text-emerald-900" data-testid={testId}>{value}</p>
      {children}
    </div>
  )
}

/** Cột doanh thu theo ngày; chiều cao tính theo ngày cao nhất trong kỳ. */
function RevenueChart({ daily }) {
  const max = Math.max(...daily.map((point) => point.revenue), 1)
  return (
    <div className="overflow-x-auto">
      <div className="flex h-48 min-w-full items-end gap-[3px]" role="img" aria-label="Biểu đồ doanh thu theo ngày" data-testid="revenue-chart" style={{ minWidth: `${daily.length * 9}px` }}>
        {daily.map((point) => (
          <div key={point.date} className="group relative flex h-full flex-1 items-end" data-testid={`bar-${point.date}`} data-value={point.revenue}>
            <div className={classNames('w-full rounded-t', point.revenue > 0 ? 'bg-emerald-600' : 'bg-emerald-100')} style={{ height: `${Math.max((point.revenue / max) * 100, point.revenue > 0 ? 3 : 1.5)}%` }} />
            <span className="pointer-events-none absolute bottom-full left-1/2 z-10 mb-1 hidden -translate-x-1/2 whitespace-nowrap rounded bg-emerald-950 px-2 py-1 text-xs font-semibold text-white group-hover:block">
              {formatIsoDate(point.date)}: {formatCurrency(point.revenue)}
            </span>
          </div>
        ))}
      </div>
      <div className="mt-1 flex justify-between text-xs font-medium text-stone-500">
        <span>{formatIsoDate(daily[0].date)}</span>
        <span>{formatIsoDate(daily[daily.length - 1].date)}</span>
      </div>
    </div>
  )
}

/** 2.3.17 — doanh thu gộp theo kỳ, theo ngày và theo sân. */
function RevenueTab() {
  const [preset, setPreset] = useState('30d')
  const [venueId, setVenueId] = useState('')
  const [custom, setCustom] = useState({ from: shiftIsoDate(todayInVietnam(), -29), to: todayInVietnam() })
  const range = useMemo(() => rangeOf(preset, custom), [preset, custom])
  const invalidRange = preset === 'custom' && (!range.from || !range.to || range.to < range.from)
  const venues = useApiResource(() => fetchOwnerVenues(), [])
  const { status, data, error, reload } = useApiResource(
    () => (invalidRange ? Promise.resolve(null) : fetchRevenueStats({ venueId, ...range })),
    [venueId, range.from, range.to, invalidRange],
    { isEmpty: (result) => result !== null && result.bookingCount === 0 && result.previous.bookingCount === 0 },
  )

  return (
    <div className="grid gap-5">
      <OwnerCard>
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex gap-1 rounded-lg border border-emerald-100 p-1" role="group" aria-label="Khoảng thời gian">
            {PRESETS.map((item) => (
              <button key={item.id} type="button" aria-pressed={preset === item.id} className={classNames('h-9 rounded-md px-3 text-sm font-semibold', preset === item.id ? 'bg-emerald-700 text-white' : 'text-stone-600 hover:bg-emerald-50')} onClick={() => setPreset(item.id)}>{item.label}</button>
            ))}
          </div>
          {preset === 'custom' && (
            <>
              <input type="date" className={`${inputClass} h-10 w-40`} aria-label="Doanh thu từ ngày" value={custom.from} onChange={(event) => setCustom((current) => ({ ...current, from: event.target.value }))} />
              <input type="date" className={`${inputClass} h-10 w-40`} aria-label="Doanh thu đến ngày" value={custom.to} onChange={(event) => setCustom((current) => ({ ...current, to: event.target.value }))} />
            </>
          )}
          <select className={`${inputClass} h-10 w-56`} aria-label="Lọc doanh thu theo sân" value={venueId} onChange={(event) => setVenueId(event.target.value)}>
            <option value="">Tất cả sân</option>
            {(venues.data?.venues ?? []).map((venue) => <option key={venue.id} value={venue.id}>{venue.name}</option>)}
          </select>
        </div>
        {invalidRange && <p className="mt-3 text-sm font-medium text-red-600" role="alert">Chọn khoảng ngày hợp lệ: ngày kết thúc phải sau hoặc bằng ngày bắt đầu.</p>}
        <p className="mt-3 text-xs font-medium text-stone-500">Doanh thu gộp là số tiền bạn đã xác nhận thu trực tiếp từ người chơi, tính theo ngày xác nhận. Chưa trừ hoa hồng và tiền hoàn.</p>
      </OwnerCard>

      {!invalidRange && status === 'loading' && <OwnerLoading rows={2} />}
      {!invalidRange && status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
      {!invalidRange && status === 'empty' && <OwnerEmpty title="Chưa có doanh thu trong kỳ này" description="Doanh thu được ghi nhận khi bạn xác nhận đã thu tiền của một booking." />}
      {!invalidRange && status === 'success' && data && (
        <>
          <div className="grid gap-3 sm:grid-cols-3">
            <Kpi label="Doanh thu gộp" value={formatCurrency(data.totalRevenue)} testId="revenue-total">
              {data.changePercent !== null && (
                <p data-testid="revenue-change" className={classNames('mt-1 inline-flex items-center gap-1 text-xs font-bold', data.changePercent >= 0 ? 'text-emerald-700' : 'text-red-700')}>
                  {data.changePercent >= 0 ? <ArrowUpRight className="size-3.5" /> : <ArrowDownRight className="size-3.5" />}
                  {data.changePercent >= 0 ? 'Tăng' : 'Giảm'} {Math.abs(data.changePercent).toLocaleString('vi-VN')}% so với kỳ trước
                </p>
              )}
              {data.changePercent === null && <p className="mt-1 text-xs font-medium text-stone-500" data-testid="revenue-change">Kỳ trước chưa có doanh thu</p>}
            </Kpi>
            <Kpi label="Số booking đã thu tiền" value={data.bookingCount} testId="revenue-bookings" />
            <Kpi label="Trung bình mỗi booking" value={formatCurrency(data.averagePerBooking)} testId="revenue-average" />
          </div>

          <OwnerCard title="Doanh thu theo ngày" description={`${formatIsoDate(data.from)} – ${formatIsoDate(data.to)}`}>
            <RevenueChart daily={data.daily} />
          </OwnerCard>

          <OwnerCard title="Theo sân">
            <ul className="divide-y divide-emerald-50" data-testid="revenue-by-venue">
              {data.byVenue.map((venue) => (
                <li key={venue.venueId} className="flex items-center justify-between gap-3 py-3 text-sm" data-testid={`revenue-venue-${venue.venueName}`}>
                  <span className="min-w-0 truncate font-semibold text-emerald-950">{venue.venueName}</span>
                  <span className="shrink-0 text-right"><span className="font-semibold text-emerald-700">{formatCurrency(venue.revenue)}</span><span className="ml-2 text-xs font-medium text-stone-500">{venue.bookings} booking</span></span>
                </li>
              ))}
            </ul>
          </OwnerCard>
        </>
      )}
    </div>
  )
}

export { RevenueTab }
