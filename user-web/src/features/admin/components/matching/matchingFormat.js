import { firstOfMonth, rangeProblem, shiftIsoDate, todayInVietnam } from '../../../owner/ownerDates'

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

/** Thời gian phản hồi dễ đọc: dưới 1 giờ tính theo phút, còn lại theo giờ và phút. */
export function formatMinutes(minutes) {
  if (minutes === null || minutes === undefined) return '—'
  if (minutes < 60) return `${minutes.toLocaleString('vi-VN', { maximumFractionDigits: 1 })} phút`
  const hours = Math.floor(minutes / 60)
  const rest = Math.round(minutes - hours * 60)
  return rest === 0 ? `${hours} giờ` : `${hours} giờ ${rest} phút`
}
