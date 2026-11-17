import { AlertTriangle, Info } from 'lucide-react'
import { fetchMatchingPerformance, MODEL_STATUS_LABEL, MODEL_TYPE_LABEL } from '../../../../api/adminMatching'
import { useApiResource } from '../../../../hooks/useApiResource'
import { classNames } from '../../../../shared/utils'
import { OwnerCard, OwnerError, OwnerLoading } from '../../../owner/components/OwnerUi'
import { formatIsoDate } from '../../../owner/ownerDates'
import { formatRate, Kpi } from './matchingUi'

const ms = (value) => (value === null || value === undefined ? '—' : `${value.toLocaleString('vi-VN')} ms`)

function RunSummary({ title, summary, testId }) {
  return (
    <OwnerCard title={title}>
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-3" data-testid={testId}>
        <Kpi label="Số lượt" value={summary.runs.toLocaleString('vi-VN')} testId={`${testId}-runs`} />
        <Kpi label="Tỷ lệ lỗi" value={formatRate(summary.failureRate)} hint={`${summary.failed} lượt lỗi`} testId={`${testId}-failure`} />
        <Kpi label="Dùng baseline" value={formatRate(summary.baselineRate)} hint={`${summary.baselineRuns} lượt`} testId={`${testId}-baseline`} />
        <Kpi label="p50" value={ms(summary.p50Ms)} testId={`${testId}-p50`} />
        <Kpi label="p95" value={ms(summary.p95Ms)} testId={`${testId}-p95`} />
        <Kpi label="Chậm nhất" value={ms(summary.maxMs)} testId={`${testId}-max`} />
      </div>
    </OwnerCard>
  )
}

/** Cột theo ngày: cao = tổng lượt chạy trong ngày; phần đỏ phía dưới là lượt lỗi. */
function DailyChart({ daily }) {
  const max = Math.max(...daily.map((item) => item.partnerSearches + item.doubleTeams), 1)
  return (
    <div className="overflow-x-auto">
      <div className="flex h-36 items-end gap-[3px]" style={{ minWidth: `${daily.length * 9}px` }} role="img" aria-label="Số lượt ghép cặp mỗi ngày" data-testid="mp-chart">
        {daily.map((item) => {
          const total = item.partnerSearches + item.doubleTeams
          return (
            <div key={item.day} className="group relative flex h-full max-w-10 flex-1 items-end" data-testid={`mp-bar-${item.day}`} data-value={total} data-failed={item.failed}>
              <div className={classNames('flex w-full flex-col justify-end overflow-hidden rounded-t', total > 0 ? 'bg-slate-700' : 'bg-slate-200')} style={{ height: `${Math.max((total / max) * 100, total > 0 ? 3 : 1.5)}%` }}>
                {item.failed > 0 && <div className="bg-red-500" style={{ height: `${(item.failed / total) * 100}%` }} />}
              </div>
              <span className="pointer-events-none absolute bottom-full left-1/2 z-10 mb-1 hidden -translate-x-1/2 whitespace-nowrap rounded bg-slate-900 px-2 py-1 text-xs font-semibold text-white group-hover:block">{formatIsoDate(item.day)}: {item.partnerSearches} tìm đối tác · {item.doubleTeams} chia đội · {item.failed} lỗi</span>
            </div>
          )
        })}
      </div>
    </div>
  )
}

/** 2.3.73 - hiệu suất xử lý của hệ thống chấm điểm ghép cặp và chất lượng model. */
export function PerformanceSection({ range }) {
  const { status, data, error, reload } = useApiResource(
    () => fetchMatchingPerformance(range),
    [range.from, range.to],
  )
  if (status === 'loading') return <OwnerLoading rows={2} />
  if (status === 'error') return <OwnerError message={error?.message} onRetry={reload} />
  const totalRuns = data.partnerSearch.runs + data.doubleTeam.runs
  return (
    <section className="grid min-w-0 grid-cols-1 gap-5" aria-label="Hiệu suất và chất lượng model" data-testid="matching-performance">
      <div className="flex flex-wrap items-center gap-3">
        <h2 className="w-full text-lg font-semibold text-slate-900 sm:w-auto">Hiệu suất xử lý</h2>
        <span className="text-sm font-medium text-slate-500">{formatIsoDate(data.from)} – {formatIsoDate(data.to)}</span>
        <span className={classNames('rounded-full px-2.5 py-1 text-xs font-semibold ring-1', data.aiServiceActive ? 'bg-emerald-50 text-emerald-700 ring-emerald-200' : 'bg-slate-100 text-slate-700 ring-slate-200')} data-testid="mp-ai-status">
          {data.aiServiceActive ? 'AI service đang hoạt động' : 'Chưa có AI service — dùng baseline'}
        </span>
      </div>

      <ul className="grid gap-2" data-testid="mp-alerts" aria-label="Cảnh báo">
        {data.alerts.length === 0 && <li className="rounded-lg border border-emerald-100 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-800" data-testid="mp-no-alerts">Không có cảnh báo nào trong khoảng này.</li>}
        {data.alerts.map((alert) => (
          <li key={alert.code + alert.message} data-testid={`mp-alert-${alert.code}`} data-severity={alert.severity} className={classNames('flex items-start gap-2 rounded-lg border px-4 py-3 text-sm font-medium', alert.severity === 'warning' ? 'border-amber-200 bg-amber-50 text-amber-900' : 'border-slate-200 bg-white text-slate-700')}>
            {alert.severity === 'warning' ? <AlertTriangle className="mt-0.5 size-4 shrink-0" /> : <Info className="mt-0.5 size-4 shrink-0" />}
            {alert.message}
          </li>
        ))}
      </ul>

      <div className="grid gap-5 2xl:grid-cols-2">
        <RunSummary title="Tìm đối tác" summary={data.partnerSearch} testId="mp-partner" />
        <RunSummary title="Chia đội" summary={data.doubleTeam} testId="mp-team" />
      </div>

      <OwnerCard title="Lượt chạy mỗi ngày" description={`${totalRuns.toLocaleString('vi-VN')} lượt trong khoảng đã chọn; phần đỏ là lượt lỗi.`}>
        <DailyChart daily={data.daily} />
      </OwnerCard>

      <OwnerCard title="Chất lượng model" description={`Cảnh báo khi tỷ lệ chấp nhận dưới ${data.thresholds.minAcceptanceRate}%, lỗi trên ${data.thresholds.maxFailureRate}%, p95 trên ${data.thresholds.maxP95Ms} ms hoặc model quá ${data.thresholds.maxModelAgeDays} ngày chưa huấn luyện lại.`}>
        <ul className="grid gap-3" data-testid="mp-models">
          {data.models.map((model) => (
            <li key={model.id} className="rounded-lg border border-slate-100 px-4 py-3" data-testid={`mp-model-${model.version}`} data-status={model.status}>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="font-semibold text-slate-900">{model.version} <span className="font-medium text-slate-500">· {MODEL_TYPE_LABEL[model.type] ?? model.type}</span></p>
                <span className="text-xs font-semibold text-slate-600">{MODEL_STATUS_LABEL[model.status] ?? model.status}</span>
              </div>
              <p className="mt-1 text-xs font-medium text-slate-500">
                {model.trainedAt ? `Huấn luyện ${formatIsoDate(model.trainedAt.slice(0, 10))}` : 'Chưa huấn luyện'}
                {model.activatedAt ? ` · Kích hoạt ${formatIsoDate(model.activatedAt.slice(0, 10))}` : ''}
                {model.ageDays !== null ? ` · ${model.ageDays} ngày trước` : ''}
              </p>
              {model.metrics && (
                <dl className="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-xs font-medium text-slate-600" data-testid={`mp-metrics-${model.version}`}>
                  {Object.entries(model.metrics).map(([key, value]) => <div key={key} className="flex gap-1"><dt>{key}:</dt><dd className="font-semibold text-slate-900">{String(value)}</dd></div>)}
                </dl>
              )}
              {!model.metrics && <p className="mt-2 text-xs font-medium text-slate-500">Chưa có chỉ số huấn luyện (model công thức không cần huấn luyện).</p>}
            </li>
          ))}
        </ul>
      </OwnerCard>
    </section>
  )
}
