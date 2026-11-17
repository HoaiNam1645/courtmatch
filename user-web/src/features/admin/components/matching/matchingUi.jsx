import { classNames } from '../../../../shared/utils'
import { inputClass } from '../../../owner/ownerStyles'
import { rangeProblem, shiftIsoDate, todayInVietnam, firstOfMonth } from '../../../owner/ownerDates'

export const PRESETS = [
  { id: '7d', label: '7 ngày' },
  { id: '30d', label: '30 ngày' },
  { id: 'month', label: 'Tháng này' },
  { id: '90d', label: '90 ngày' },
  { id: 'custom', label: 'Tùy chọn' },
]

export function rangeOf(preset, custom) {
  const today = todayInVietnam()
  if (preset === '7d') return { from: shiftIsoDate(today, -6), to: today }
  if (preset === 'month') return { from: firstOfMonth(today), to: today }
  if (preset === '90d') return { from: shiftIsoDate(today, -89), to: today }
  if (preset === 'custom') return custom
  return { from: shiftIsoDate(today, -29), to: today }
}

export function customProblem(preset, range) {
  if (preset !== 'custom') return ''
  return !range.from || !range.to ? 'Chọn đủ ngày bắt đầu và ngày kết thúc.' : rangeProblem(range.from, range.to)
}

/** Phần trăm có thể vắng mặt: null (mẫu số bằng 0) hiện "—", không hiện 0% gây hiểu lầm. */
export function formatRate(value) {
  return value === null || value === undefined ? '—' : `${value.toLocaleString('vi-VN', { maximumFractionDigits: 1 })}%`
}

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
