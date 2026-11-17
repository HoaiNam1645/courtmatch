import { classNames } from '../../../../shared/utils'
import { inputClass } from '../../../owner/ownerStyles'
import { PRESETS } from './matchingFormat'

export function RangeBar({ preset, onPreset, custom, onCustom, problem, children }) {
  return (
    <div className="mb-5 rounded-xl border border-slate-200 bg-white p-4">
      <div className="flex flex-wrap items-end gap-3">
        <div className="flex max-w-full gap-1 overflow-x-auto rounded-lg border border-slate-200 p-1" role="group" aria-label="Khoảng thời gian">
          {PRESETS.map((item) => (
            <button key={item.id} type="button" aria-pressed={preset === item.id} data-testid={`mt-preset-${item.id}`} className={classNames('h-9 shrink-0 whitespace-nowrap rounded-md px-3 text-sm font-semibold', preset === item.id ? 'bg-slate-800 text-white' : 'text-slate-600 hover:bg-slate-100')} onClick={() => onPreset(item.id)}>{item.label}</button>
          ))}
        </div>
        {children}
      </div>
      {preset === 'custom' && (
        <div className="mt-3 grid gap-3 sm:grid-cols-2">
          <label className="block text-sm font-semibold text-slate-700">Từ ngày<input type="date" className={`${inputClass} mt-1`} aria-label="Từ ngày" value={custom.from} onChange={(event) => onCustom({ ...custom, from: event.target.value })} /></label>
          <label className="block text-sm font-semibold text-slate-700">Đến ngày<input type="date" className={`${inputClass} mt-1`} aria-label="Đến ngày" value={custom.to} onChange={(event) => onCustom({ ...custom, to: event.target.value })} /></label>
        </div>
      )}
      {problem && <p className="mt-3 text-sm font-medium text-red-600" role="alert" data-testid="mt-range-error">{problem}</p>}
    </div>
  )
}

export function Kpi({ label, value, hint, testId }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4">
      <p className="text-xs font-semibold text-slate-500">{label}</p>
      <p className="mt-2 text-2xl font-semibold text-slate-900" data-testid={testId}>{value}</p>
      {hint && <p className="mt-1 text-xs font-medium text-slate-500">{hint}</p>}
    </div>
  )
}

/** Nhãn cảnh báo mẫu nhỏ: số liệu vẫn hiện nhưng không được hiểu là kết luận. */
export function SampleBadge({ count, min, testId }) {
  return (
    <span data-testid={testId} className="inline-flex items-center rounded-full bg-amber-50 px-2.5 py-1 text-xs font-semibold text-amber-800 ring-1 ring-amber-200">
      Chưa đủ dữ liệu ({count}/{min} mẫu)
    </span>
  )
}

/** Trạng thái đối chiếu với trận thật (2.3.71): chưa có trận, hoặc đã có và dự đoán đúng / sai. */
export function ReconciliationBadge({ reconciliation }) {
  if (reconciliation.status !== 'played') {
    return <span className="inline-flex rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600" data-state="none">Chưa có trận</span>
  }
  const verdict = reconciliation.predictedCorrect === null ? 'Không dự đoán' : reconciliation.predictedCorrect ? 'Dự đoán đúng' : 'Dự đoán sai'
  const tone = reconciliation.predictedCorrect === null ? 'bg-slate-100 text-slate-700' : reconciliation.predictedCorrect ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'
  return (
    <span className={classNames('inline-flex rounded-full px-2.5 py-1 text-xs font-semibold', tone)} data-state="played">
      Đã chơi phương án {reconciliation.optionRank}{reconciliation.optionRecommended ? ' (khuyến nghị)' : ''} · {verdict}
    </span>
  )
}
