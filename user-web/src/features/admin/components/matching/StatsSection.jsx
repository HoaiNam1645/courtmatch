import { useState } from 'react'
import { BarChart3 } from 'lucide-react'
import { fetchMatchingStats, STATS_METRICS } from '../../../../api/adminMatching'
import { useApiResource } from '../../../../hooks/useApiResource'
import { classNames } from '../../../../shared/utils'
import { OwnerCard, OwnerError, OwnerLoading } from '../../../owner/components/OwnerUi'
import { formatIsoDate } from '../../../owner/ownerDates'
import { inputClass } from '../../../owner/ownerStyles'
import { formatMinutes } from './matchingFormat'
import { Kpi } from './matchingUi'

const GRANULARITIES = [['day', 'Theo ngày'], ['week', 'Theo tuần'], ['month', 'Theo tháng']]

function periodLabel(period, granularity) {
  if (granularity === 'month') return `${period.slice(5, 7)}/${period.slice(0, 4)}`
  if (granularity === 'week') return `Tuần ${formatIsoDate(period)}`
  return formatIsoDate(period)
}

function Chart({ points, metric, granularity }) {
  const max = Math.max(...points.map((point) => point[metric.id]), 1)
  return (
    <div className="overflow-x-auto">
      <div className="flex h-48 min-w-full items-end gap-[3px]" role="img" aria-label={`Biểu đồ ${metric.label.toLowerCase()}`} data-testid="ms-chart" style={{ minWidth: `${points.length * 9}px` }}>
        {points.map((point) => (
          <div key={point.period} className="group relative flex h-full max-w-10 flex-1 items-end" data-testid={`ms-bar-${point.period}`} data-value={point[metric.id]}>
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

/** 2.3.74 - thống kê hiệu suất ghép cặp theo ngày / tuần / tháng. */
export function StatsSection({ range }) {
  const [granularity, setGranularity] = useState('day')
  const [metricId, setMetricId] = useState('requests')
  const { status, data, error, reload } = useApiResource(
    () => fetchMatchingStats({ ...range, granularity }),
    [range.from, range.to, granularity],
  )
  const metric = STATS_METRICS.find((item) => item.id === metricId)
  return (
    <section className="grid min-w-0 grid-cols-1 gap-5" aria-label="Thống kê hiệu suất ghép cặp" data-testid="matching-stats">
      {status === 'loading' && <OwnerLoading rows={2} />}
      {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
      {data && (
        <>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            <Kpi label="Lượt tìm đối tác" value={data.totals.searches.toLocaleString('vi-VN')} hint={`${data.totals.suggestions.toLocaleString('vi-VN')} gợi ý đã hiển thị`} testId="ms-searches" />
            <Kpi label="Yêu cầu ghép cặp" value={data.totals.requests.toLocaleString('vi-VN')} hint={`${data.totals.accepted} chấp nhận · ${data.totals.rejected} từ chối · ${data.totals.expired} hết hạn · ${data.totals.cancelled} hủy`} testId="ms-requests" />
            <Kpi label="Kết nối mới" value={data.totals.connections.toLocaleString('vi-VN')} testId="ms-connections" />
            <Kpi label="Lượt chia đội" value={data.totals.teamRuns.toLocaleString('vi-VN')} hint={`${data.totals.teamOptions.toLocaleString('vi-VN')} phương án đã đề xuất`} testId="ms-team-runs" />
            <Kpi label="Phản hồi trung bình" value={formatMinutes(data.avgResponseMinutes)} hint={`Tính trên ${data.responseSamples} yêu cầu đã chấp nhận hoặc từ chối`} testId="ms-response" />
          </div>
          <p className="text-xs font-medium text-slate-500">Yêu cầu được tính theo ngày gửi và trạng thái hiện tại; mỗi kết nối chỉ đếm một lần cho cả hai chiều.</p>

          <OwnerCard title="Xu hướng">
            <div className="flex flex-wrap items-end gap-3">
              <div className="flex gap-1 rounded-lg border border-slate-200 p-1" role="group" aria-label="Đơn vị thời gian">
                {GRANULARITIES.map(([id, label]) => (
                  <button key={id} type="button" aria-pressed={granularity === id} data-testid={`ms-granularity-${id}`} className={classNames('h-9 shrink-0 whitespace-nowrap rounded-md px-3 text-sm font-semibold', granularity === id ? 'bg-slate-800 text-white' : 'text-slate-600 hover:bg-slate-100')} onClick={() => setGranularity(id)}>{label}</button>
                ))}
              </div>
              <select className={`${inputClass} sm:max-w-56`} aria-label="Chỉ số" value={metricId} onChange={(event) => setMetricId(event.target.value)}>
                {STATS_METRICS.map((item) => <option key={item.id} value={item.id}>{item.label}</option>)}
              </select>
            </div>
            <div className="mt-5">
              <p className="text-sm font-semibold text-slate-700" data-testid="ms-total">{metric.label}: <span className="text-lg text-slate-900">{data.totals[metric.id].toLocaleString('vi-VN')}</span> <span className="font-medium text-slate-500">từ {formatIsoDate(data.from)} đến {formatIsoDate(data.to)}</span></p>
              {data.totals[metric.id] === 0 && <p className="mt-2 flex items-center gap-2 text-sm font-medium text-slate-500" data-testid="ms-empty"><BarChart3 className="size-4" />Không có {metric.label.toLowerCase()} trong khoảng này.</p>}
              <div className="mt-3"><Chart points={data.points} metric={metric} granularity={data.granularity} /></div>
            </div>
          </OwnerCard>
        </>
      )}
    </section>
  )
}
