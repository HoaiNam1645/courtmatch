/** Ngày giờ hiển thị trong khu quản lý chủ sân luôn theo giờ Việt Nam. */

export const VN_TIME_ZONE = 'Asia/Ho_Chi_Minh'

/** Ngày hôm nay ở Việt Nam, dạng YYYY-MM-DD. */
export function todayInVietnam() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: VN_TIME_ZONE }).format(new Date())
}

/** "2026-11-20" -> "20/11/2026". */
export function formatIsoDate(value) {
  if (!value) return ''
  const [year, month, day] = value.split('-')
  return `${day}/${month}/${year}`
}

export function formatDateTime(value) {
  if (!value) return ''
  return new Intl.DateTimeFormat('vi-VN', {
    timeZone: VN_TIME_ZONE, day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(new Date(value))
}

export function formatTime(value) {
  if (!value) return ''
  return new Intl.DateTimeFormat('vi-VN', { timeZone: VN_TIME_ZONE, hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value))
}

/** Cộng/trừ ngày cho chuỗi YYYY-MM-DD, không phụ thuộc múi giờ máy. */
export function shiftIsoDate(isoDate, days) {
  const [year, month, day] = isoDate.split(/-/).map(Number)
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10)
}

/** Ngày đầu tháng của chuỗi YYYY-MM-DD. */
export function firstOfMonth(isoDate) {
  return `${isoDate.slice(0, 8)}01`
}

/** Giới hạn xem thống kê tài chính mỗi lần; khớp OwnerRevenueService.MAX_DAYS ở backend. */
export const MAX_RANGE_DAYS = 366

/**
 * Kiểm tra khoảng ngày trước khi gọi API thống kê (cùng điều kiện với backend).
 *
 * @returns chuỗi lỗi hiển thị cho người dùng, hoặc rỗng nếu hợp lệ
 */
export function rangeProblem(from, to) {
  const end = to || todayInVietnam()
  const start = from || shiftIsoDate(end, -29)
  if (end < start) return 'Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.'
  const days = Math.round((Date.parse(`${end}T00:00:00Z`) - Date.parse(`${start}T00:00:00Z`)) / 86400000) + 1
  if (days > MAX_RANGE_DAYS) return `Chỉ xem tối đa ${MAX_RANGE_DAYS} ngày mỗi lần. Hãy chọn khoảng ngắn hơn.`
  return ''
}
