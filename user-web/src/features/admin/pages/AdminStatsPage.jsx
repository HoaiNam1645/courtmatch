import { useMemo, useState } from 'react'
import { BarChart3, CalendarDays, FileWarning, MapPin, Trophy, UsersRound } from 'lucide-react'
import {
  APPROVAL_LABEL,
  BOOKING_STATUS_LABEL,
  fetchSystemStats,
  MATCH_STATUS_LABEL,
  REPORT_TYPE_LABEL,
  ROLE_LABEL,
  TREND_METRICS,
  USER_STATUS_LABEL,
} from '../../../api/adminStats'
import { useApiResource } from '../../../hooks/useApiResource'
import { classNames } from '../../../shared/utils'
import { OwnerCard, OwnerError, OwnerLoading } from '../../owner/components/OwnerUi'
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

function Kpi({ icon: Icon, label, value, testId, hint }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4">
      <div className="flex items-center gap-2 text-xs font-semibold text-slate-500"><Icon className="size-4 text-slate-400" />{label}</div>
      <p className="mt-2 text-2xl font-semibold text-slate-900" data-testid={testId}>{Number(value).toLocaleString('vi-VN')}</p>
      {hint && <p className="mt-1 text-xs font-medium text-slate-500">{hint}</p>}
    </div>
  )
}

/** Phân bố theo nhóm: thanh ngang tỉ lệ với nhóm lớn nhất, kèm số lượng. */
function Distribution({ title, data, labels, testId }) {
  const entries = Object.entries(data)
  const max = Math.max(...entries.map(([, value]) => value), 1)
  return (
    <OwnerCard title={title}>
      <ul className="grid gap-2" data-testid={testId}>
        {entries.map(([key, value]) => (
          <li key={key} data-testid={`${testId}-${key}`} data-value={value}>
            <div className="flex items-center justify-between text-sm font-medium text-slate-700"><span>{labels[key] ?? key}</span><span className="font-semibold text-slate-900">{value.toLocaleString('vi-VN')}</span></div>
            <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-slate-100"><div className="h-full rounded-full bg-slate-700" style={{ width: `${(value / max) * 100}%`, minWidth: value > 0 ? '4px' : 0 }} /></div>
          </li>
        ))}
      </ul>
    </OwnerCard>
  )
}

function periodLabel(period, granularity) {
  return granularity === 'month' ? `${period.slice(5, 7)}/${period.slice(0, 4)}` : formatIsoDate(period)
}

/** Cột theo ngày hoặc tháng; chiều cao tính theo điểm cao nhất của chỉ số đang chọn. */
function TrendChart({ points, metric, granularity }) {
  const max = Math.max(...points.map((point) => point[metric.id]), 1)
  return (
    <div className="overflow-x-auto">
      <div className="flex h-48 min-w-full items-end gap-[3px]" role="img" aria-label={`Biểu đồ ${metric.label.toLowerCase()}`} data-testid="stats-chart" style={{ minWidth: `${points.length * 9}px` }}>
        {points.map((point) => (
          <div key={point.period} className="group relative flex h-full flex-1 items-end" data-testid={`bar-${point.period}`} data-value={point[metric.id]}>
            <div className={classNames('w-full rounded-t', point[metric.id] > 0 ? 'bg-slate-700' : 'bg-slate-200')} style={{ height: `${Math.max((point[metric.id] / max) * 100, point[metric.id] > 0 ? 3 : 1.5)}%` }} />
            <span className="pointer-events-none absolute bottom-full left-1/2 z-10 mb-1 hidden -translate-x-1/2 whitespace-nowrap rounded bg-slate-900 px-2 py-1 text-xs font-semibold text-white group-hover:block">{periodLabel(point.period, granularity)}: {point[metric.id]}</span>
          </div>
        ))}
      </div>
      <div className="mt-1 flex justify-between text-xs font-medium text-slate-500">
        <span>{periodLabel(points[0].period, granularity)}</span>
        <span>{periodLabel(points[points.length - 1].period, granularity)}</span>
      </div>
    </div>
  )
}

/** 2.3.38 — thống kê hệ thống: tổng quan và xu hướng theo ngày hoặc tháng. */
function AdminStatsPage() {
  const [preset, setPreset] = useState('30d')
  const [granularity, setGranularity] = useState('day')
  const [metricId, setMetricId] = useState('newUsers')
  const [custom, setCustom] = useState({ from: shiftIsoDate(todayInVietnam(), -29), to: todayInVietnam() })
  const range = useMemo(() => rangeOf(preset, custom), [preset, custom])
  const problem = preset === 'custom' ? (!range.from || !range.to ? 'Chọn đủ ngày bắt đầu và ngày kết thúc.' : rangeProblem(range.from, range.to)) : ''
  const { status, data, error, reload } = useApiResource(
    () => (problem ? Promise.resolve(null) : fetchSystemStats({ ...range, granularity })),
    [range.from, range.to, granularity, problem],
  )
  const metric = TREND_METRICS.find((item) => item.id === metricId)
  const overview = data?.overview

  return (
    <AdminLayout eyebrow="2.3.38 · Thống kê" title="Thống kê hệ thống" description="Số liệu tổng quan và xu hướng của toàn hệ thống, tính từ dữ liệu gốc theo giờ Việt Nam.">
      {status === 'loading' && <OwnerLoading rows={3} />}
      {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
      {overview && (
        <div className="grid gap-5">
          <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4" aria-label="Số liệu tổng quan">
            <Kpi icon={UsersRound} label="Tổng tài khoản" value={overview.users.total} testId="kpi-users" hint={`Hôm nay +${overview.users.newToday} · 7 ngày +${overview.users.newLast7Days} · 30 ngày +${overview.users.newLast30Days}`} />
            <Kpi icon={MapPin} label="Sân cầu lông" value={overview.venues.total} testId="kpi-venues" hint={`${overview.venues.byApproval.pending} chờ duyệt`} />
            <Kpi icon={CalendarDays} label="Booking" value={overview.bookings.total} testId="kpi-bookings" />
            <Kpi icon={Trophy} label="Trận đấu" value={overview.matches.total} testId="kpi-matches" />
          </section>

          <div className="grid gap-5 lg:grid-cols-2">
            <Distribution title="Tài khoản theo vai trò" data={overview.users.byRole} labels={ROLE_LABEL} testId="dist-role" />
            <Distribution title="Tài khoản theo trạng thái" data={overview.users.byStatus} labels={USER_STATUS_LABEL} testId="dist-user-status" />
            <Distribution title="Sân theo trạng thái duyệt" data={overview.venues.byApproval} labels={APPROVAL_LABEL} testId="dist-venue" />
            <Distribution title="Booking theo trạng thái" data={overview.bookings.byStatus} labels={BOOKING_STATUS_LABEL} testId="dist-booking" />
            <Distribution title="Trận đấu theo trạng thái" data={overview.matches.byStatus} labels={MATCH_STATUS_LABEL} testId="dist-match" />
            <OwnerCard title="Báo cáo đang chờ xử lý">
              <div className="flex items-center gap-3">
                <FileWarning className="size-8 text-amber-500" />
                <div>
                  <p className="text-2xl font-semibold text-slate-900" data-testid="kpi-reports">{overview.reports.pending + overview.reports.reviewing}</p>
                  <p className="text-xs font-medium text-slate-500">{overview.reports.pending} chờ xử lý · {overview.reports.reviewing} đang xử lý</p>
                </div>
              </div>
              <ul className="mt-4 grid gap-1 text-sm font-medium text-slate-700" data-testid="dist-report">
                {Object.entries(overview.reports.openByType).map(([type, value]) => <li key={type} className="flex justify-between" data-testid={`dist-report-${type}`}><span>{REPORT_TYPE_LABEL[type] ?? type}</span><span className="font-semibold text-slate-900">{value}</span></li>)}
              </ul>
            </OwnerCard>
          </div>
        </div>
      )}

      <div className="mt-5">
        <OwnerCard title="Xu hướng">
          <div className="flex flex-wrap items-end gap-3">
            <div className="flex gap-1 rounded-lg border border-slate-200 p-1" role="group" aria-label="Khoảng thời gian">
              {PRESETS.map((item) => (
                <button key={item.id} type="button" aria-pressed={preset === item.id} data-testid={`stats-preset-${item.id}`} className={classNames('h-9 rounded-md px-3 text-sm font-semibold', preset === item.id ? 'bg-slate-800 text-white' : 'text-slate-600 hover:bg-slate-100')} onClick={() => { setPreset(item.id); if (item.id === 'year') setGranularity('month') }}>{item.label}</button>
              ))}
            </div>
            <div className="flex gap-1 rounded-lg border border-slate-200 p-1" role="group" aria-label="Đơn vị thời gian">
              {[['day', 'Theo ngày'], ['month', 'Theo tháng']].map(([id, label]) => (
                <button key={id} type="button" aria-pressed={granularity === id} data-testid={`stats-granularity-${id}`} className={classNames('h-9 rounded-md px-3 text-sm font-semibold', granularity === id ? 'bg-slate-800 text-white' : 'text-slate-600 hover:bg-slate-100')} onClick={() => setGranularity(id)}>{label}</button>
              ))}
            </div>
            <select className={`${inputClass} w-48`} aria-label="Chỉ số" value={metricId} onChange={(event) => setMetricId(event.target.value)}>
              {TREND_METRICS.map((item) => <option key={item.id} value={item.id}>{item.label}</option>)}
            </select>
          </div>
          {preset === 'custom' && (
            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              <label className="block text-sm font-semibold text-slate-700">Từ ngày<input type="date" className={`${inputClass} mt-1`} aria-label="Từ ngày" value={custom.from} onChange={(event) => setCustom((current) => ({ ...current, from: event.target.value }))} /></label>
              <label className="block text-sm font-semibold text-slate-700">Đến ngày<input type="date" className={`${inputClass} mt-1`} aria-label="Đến ngày" value={custom.to} onChange={(event) => setCustom((current) => ({ ...current, to: event.target.value }))} /></label>
            </div>
          )}
          {problem && <p className="mt-3 text-sm font-medium text-red-600" role="alert" data-testid="stats-range-error">{problem}</p>}
          {data && !problem && (
            <div className="mt-5">
              <p className="text-sm font-semibold text-slate-700" data-testid="stats-total">{metric.label}: <span className="text-lg text-slate-900">{data.trend.totals[metric.id].toLocaleString('vi-VN')}</span> <span className="font-medium text-slate-500">từ {formatIsoDate(data.trend.from)} đến {formatIsoDate(data.trend.to)}</span></p>
              {data.trend.totals[metric.id] === 0 && <p className="mt-2 flex items-center gap-2 text-sm font-medium text-slate-500" data-testid="stats-empty"><BarChart3 className="size-4" />Không có {metric.label.toLowerCase()} trong khoảng này.</p>}
              <div className="mt-3"><TrendChart points={data.trend.points} metric={metric} granularity={data.trend.granularity} /></div>
            </div>
          )}
        </OwnerCard>
      </div>
    </AdminLayout>
  )
}

export default AdminStatsPage
