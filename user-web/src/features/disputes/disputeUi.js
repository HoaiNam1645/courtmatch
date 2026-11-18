import { DISPUTE_STATUS_LABEL } from '../../api/bookingDisputes'

const STATUS_CLASS = {
  open: 'bg-sky-100 text-sky-700',
  awaiting_owner_response: 'bg-amber-100 text-amber-800',
  reviewing: 'bg-violet-100 text-violet-700',
  resolved: 'bg-emerald-100 text-emerald-700',
  rejected: 'bg-red-100 text-red-700',
  cancelled: 'bg-stone-200 text-stone-600',
}

export function disputeStatusMeta(status) {
  return { label: DISPUTE_STATUS_LABEL[status] ?? status, className: STATUS_CLASS[status] ?? 'bg-stone-200 text-stone-600' }
}

/** "còn 5 giờ 20 phút" / "đã quá hạn" theo hạn giải trình do backend trả về. */
export function remainingText(deadline, now = Date.now()) {
  if (!deadline) return ''
  const minutes = Math.floor((new Date(deadline).getTime() - now) / 60000)
  if (minutes <= 0) return 'đã quá hạn giải trình'
  if (minutes < 60) return `còn ${minutes} phút`
  const hours = Math.floor(minutes / 60)
  return `còn ${hours} giờ${minutes % 60 ? ` ${minutes % 60} phút` : ''}`
}

export const formatDateTime = (value) => (value ? new Date(value).toLocaleString('vi-VN') : '')

/** "đã chờ 5 giờ" / "đã chờ 2 ngày" kể từ một mốc thời gian (thời gian báo cáo chờ quản trị viên xử lý). */
export function elapsedText(since, now = Date.now()) {
  if (!since) return ''
  const minutes = Math.max(0, Math.floor((now - new Date(since).getTime()) / 60000))
  if (minutes < 60) return `đã chờ ${minutes} phút`
  const hours = Math.floor(minutes / 60)
  return hours < 48 ? `đã chờ ${hours} giờ` : `đã chờ ${Math.floor(hours / 24)} ngày`
}
