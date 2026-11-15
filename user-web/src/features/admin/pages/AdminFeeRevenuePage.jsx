import { useMemo, useRef, useState } from 'react'
import { Info } from 'lucide-react'
import { fetchFeeRevenue } from '../../../api/adminFeeRevenue'
import { useApiResource } from '../../../hooks/useApiResource'
import { classNames, formatCurrency } from '../../../shared/utils'
import { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading } from '../../owner/components/OwnerUi'
import { firstOfMonth, formatIsoDate, rangeProblem, shiftIsoDate, todayInVietnam } from '../../owner/ownerDates'
import { inputClass } from '../../owner/ownerStyles'
import { AdminLayout } from '../components/AdminLayout'

const PRESETS = [
  { id: '7d', label: '7 ngày' },
  { id: '30d', label: '30 ngày' },
  { id: 'month', label: 'Tháng này' },
  { id: 'year', label: '12 tháng' },
  { id: 'custom', label: 'Tùy chọn' },
]

function rangeOf(preset, custom) {
  const today = todayInVietnam()
  if (preset === '7d') return { from: shiftIsoDate(today, -6), to: today }
  if (preset === 'month') return { from: firstOfMonth(today), to: today }
  if (preset === 'year') return { from: shiftIsoDate(today, -364), to: today }
  if (preset === 'custom') return custom
  return { from: shiftIsoDate(today, -29), to: today }
}

function Kpi({ label, value, hint, testId, tone = 'text-slate-900' }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4">
      <p className="text-xs font-semibold text-slate-500">{label}</p>
      <p className={classNames('mt-1 text-2xl font-semibold', tone)} data-testid={testId}>{value}</p>
      {hint && <p className="mt-1 text-xs font-medium text-slate-500">{hint}</p>}
    </div>
  )
}

function periodLabel(period, granularity) {
  return granularity === 'month' ? `${period.slice(5, 7)}/${period.slice(0, 4)}` : formatIsoDate(period)
}

/** Cột chồng: phần đã thu (đậm) và còn phải thu (nhạt); chiều cao theo điểm cao nhất của khoảng. */
function RevenueChart({ points, granularity }) {
  const max = Math.max(...points.map((point) => point.recorded), 1)
  return (
    <div className="overflow-x-auto">
      <div className="flex h-48 min-w-full items-end gap-[3px]" role="img" aria-label="Biểu đồ doanh thu phí" data-testid="fee-chart" style={{ minWidth: `${points.length * 9}px` }}>
        {points.map((point) => {
          const height = point.recorded > 0 ? Math.max((point.recorded / max) * 100, 3) : 1.5
          return (
            <div key={point.period} className="group relative flex h-full flex-1 items-end" data-testid={`bar-${point.period}`} data-value={point.recorded}>
              <div className="flex w-full flex-col justify-end overflow-hidden rounded-t" style={{ height: `${height}%` }}>
                {point.recorded === 0 ? <div className="h-full w-full bg-slate-200" /> : (
                  <>
                    <div className="w-full bg-amber-300" style={{ height: `${(point.outstanding / point.recorded) * 100}%` }} />
                    <div className="w-full bg-slate-700" style={{ height: `${(point.collected / point.recorded) * 100}%` }} />
                  </>
                )}
              </div>
              <span className="pointer-events-none absolute bottom-full left-1/2 z-10 mb-1 hidden -translate-x-1/2 whitespace-nowrap rounded bg-slate-900 px-2 py-1 text-xs font-semibold text-white group-hover:block">{periodLabel(point.period, granularity)}: {formatCurrency(point.recorded)}</span>
            </div>
          )
        })}
      </div>
      <div className="mt-1 flex justify-between text-xs font-medium text-slate-500">
        <span>{periodLabel(points[0].period, granularity)}</span>
        <span>{periodLabel(points[points.length - 1].period, granularity)}</span>
      </div>
      <div className="mt-3 flex flex-wrap gap-4 text-xs font-semibold text-slate-600">
        <span className="flex items-center gap-1.5"><span className="size-3 rounded-sm bg-slate-700" />Đã thu</span>
        <span className="flex items-center gap-1.5"><span className="size-3 rounded-sm bg-amber-300" />Còn phải thu</span>
      </div>
    </div>
  )
}

/** 2.3.42 — doanh thu phí nền tảng theo khoảng thời gian, theo ngày/tháng và theo sân. */
function AdminFeeRevenuePage() {
  const [preset, setPreset] = useState('30d')
  const [granularity, setGranularity] = useState('day')
  const [venueId, setVenueId] = useState('')
  const [custom, setCustom] = useState({ from: shiftIsoDate(todayInVietnam(), -29), to: todayInVietnam() })
  const range = useMemo(() => rangeOf(preset, custom), [preset, custom])
  const problem = preset === 'custom' ? (!range.from || !range.to ? 'Chọn đủ ngày bắt đầu và ngày kết thúc.' : rangeProblem(range.from, range.to)) : ''
  const { status, data, error, reload } = useApiResource(
    () => (problem ? Promise.resolve(null) : fetchFeeRevenue({ ...range, granularity, venueId })),
    [range.from, range.to, granularity, venueId, problem],
  )
  // Giữ danh sách sân của lần tải gần nhất để ô lọc không trống khi khoảng ngày đang không hợp lệ.
  const optionsRef = useRef([])
  if (data?.venueOptions) optionsRef.current = data.venueOptions
  const options = optionsRef.current
  const summary = data?.summary

  return (
    <AdminLayout eyebrow="2.3.42 · Doanh thu phí" title="Doanh thu phí nền tảng" description="Hoa hồng Courtly thu từ chủ sân, tính từ hoa hồng từng booking. Courtly không giữ tiền sân của người chơi nên đây không phải tổng doanh thu đặt sân.">
      <OwnerCard>
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex max-w-full gap-1 overflow-x-auto rounded-lg border border-slate-200 p-1" role="group" aria-label="Khoảng thời gian">
            {PRESETS.map((item) => (
              <button key={item.id} type="button" aria-pressed={preset === item.id} data-testid={`rev-preset-${item.id}`} className={classNames('h-9 shrink-0 whitespace-nowrap rounded-md px-3 text-sm font-semibold', preset === item.id ? 'bg-slate-800 text-white' : 'text-slate-600 hover:bg-slate-100')} onClick={() => { setPreset(item.id); if (item.id === 'year') setGranularity('month') }}>{item.label}</button>
            ))}
          </div>
          <div className="flex gap-1 rounded-lg border border-slate-200 p-1" role="group" aria-label="Đơn vị thời gian">
            {[['day', 'Theo ngày'], ['month', 'Theo tháng']].map(([id, label]) => (
              <button key={id} type="button" aria-pressed={granularity === id} data-testid={`rev-granularity-${id}`} className={classNames('h-9 shrink-0 whitespace-nowrap rounded-md px-3 text-sm font-semibold', granularity === id ? 'bg-slate-800 text-white' : 'text-slate-600 hover:bg-slate-100')} onClick={() => setGranularity(id)}>{label}</button>
            ))}
          </div>
          <select className={`${inputClass} sm:max-w-56`} aria-label="Lọc theo sân" value={venueId} onChange={(event) => setVenueId(event.target.value)}>
            <option value="">Tất cả sân</option>
            {options.map((venue) => <option key={venue.id} value={venue.id}>{venue.name}</option>)}
          </select>
        </div>
        {preset === 'custom' && (
          <div className="mt-3 grid gap-3 sm:grid-cols-2">
            <label className="block text-sm font-semibold text-slate-700">Từ ngày<input type="date" className={`${inputClass} mt-1`} aria-label="Từ ngày" value={custom.from} onChange={(event) => setCustom((current) => ({ ...current, from: event.target.value }))} /></label>
            <label className="block text-sm font-semibold text-slate-700">Đến ngày<input type="date" className={`${inputClass} mt-1`} aria-label="Đến ngày" value={custom.to} onChange={(event) => setCustom((current) => ({ ...current, to: event.target.value }))} /></label>
          </div>
        )}
        {problem && <p className="mt-3 text-sm font-medium text-red-600" role="alert" data-testid="rev-range-error">{problem}</p>}
      </OwnerCard>

      <div className="mt-5 grid gap-5">
        {status === 'loading' && <OwnerLoading rows={3} />}
        {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
        {data && !problem && (
          <>
            <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4" aria-label="Doanh thu phí">
              <Kpi label="Phí đã ghi nhận" value={formatCurrency(summary.recorded)} testId="rev-recorded" hint={`${summary.bookingCount.toLocaleString('vi-VN')} booking · doanh thu gộp ${formatCurrency(summary.grossAmount)}`} />
              <Kpi label="Đã thu" value={formatCurrency(summary.collected)} testId="rev-collected" tone="text-emerald-700" />
              <Kpi label="Còn phải thu" value={formatCurrency(summary.outstanding)} testId="rev-outstanding" tone="text-amber-700" />
              <Kpi label="Tỷ lệ phí thực tế" value={summary.effectiveRate == null ? '—' : `${summary.effectiveRate.toLocaleString('vi-VN')}%`} testId="rev-rate" hint="Phí ghi nhận / doanh thu gộp" />
            </section>

            {(summary.suspendedCount > 0 || summary.waivedCount > 0) && (
              <div className="flex items-start gap-3 rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm font-medium text-slate-600" data-testid="rev-excluded">
                <Info className="mt-0.5 size-4 shrink-0 text-slate-400" />
                <div>
                  <p className="font-semibold text-slate-800">Không tính vào doanh thu phí</p>
                  {summary.suspendedCount > 0 && <p data-testid="rev-suspended">Tạm hoãn do tranh chấp: {summary.suspendedCount} hoa hồng · {formatCurrency(summary.suspended)}</p>}
                  {summary.waivedCount > 0 && <p data-testid="rev-waived">Được miễn: {summary.waivedCount} hoa hồng · {formatCurrency(summary.waived)}</p>}
                </div>
              </div>
            )}

            <OwnerCard title="Xu hướng phí ghi nhận" description={`Từ ${formatIsoDate(data.trend.from)} đến ${formatIsoDate(data.trend.to)}, theo ${data.trend.granularity === 'month' ? 'tháng' : 'ngày'}.`}>
              {summary.recorded === 0 && <p className="mb-3 text-sm font-medium text-slate-500" data-testid="rev-empty">Chưa ghi nhận hoa hồng nào trong khoảng này.</p>}
              <RevenueChart points={data.trend.points} granularity={data.trend.granularity} />
            </OwnerCard>

            <OwnerCard title="Theo sân" description={data.venues.length === 0 ? undefined : `${data.venues.length} sân có hoa hồng ghi nhận, phí cao nhất trước.`}>
              {data.venues.length === 0 ? <OwnerEmpty title="Chưa có sân nào" description="Chưa có hoa hồng ghi nhận cho sân nào trong khoảng này." /> : (
                <ul className="grid gap-3" data-testid="rev-venues">
                  {data.venues.map((venue) => (
                    <li key={venue.venueId} data-testid={`rev-venue-${venue.venueName}`} className="rounded-xl border border-slate-200 p-4">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div>
                          <p className="text-sm font-semibold text-slate-900">{venue.venueName}</p>
                          <p className="text-xs font-medium text-slate-500">Chủ sân {venue.ownerName} · {venue.bookingCount.toLocaleString('vi-VN')} booking · doanh thu gộp {formatCurrency(venue.grossAmount)}</p>
                        </div>
                        <p className="text-base font-semibold text-slate-900" data-testid="rev-venue-recorded">{formatCurrency(venue.recorded)}</p>
                      </div>
                      <p className="mt-2 text-xs font-semibold text-slate-600">Đã thu <span className="text-emerald-700" data-testid="rev-venue-collected">{formatCurrency(venue.collected)}</span> · còn phải thu <span className="text-amber-700" data-testid="rev-venue-outstanding">{formatCurrency(venue.outstanding)}</span></p>
                    </li>
                  ))}
                </ul>
              )}
            </OwnerCard>
          </>
        )}
      </div>
    </AdminLayout>
  )
}

export default AdminFeeRevenuePage
