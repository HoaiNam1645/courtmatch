/** 2.3.38 — thống kê hệ thống cho quản trị viên. */

import { request } from './client'

export const ROLE_LABEL = { player: 'Người chơi', owner: 'Chủ sân', admin: 'Quản trị viên', staff: 'Nhân viên' }
export const USER_STATUS_LABEL = { active: 'Đang hoạt động', inactive: 'Ngừng hoạt động', banned: 'Bị khóa', pending: 'Chưa xác minh' }
export const APPROVAL_LABEL = { pending: 'Chờ duyệt', approved: 'Đã duyệt', rejected: 'Bị từ chối' }
export const BOOKING_STATUS_LABEL = {
  pending_owner_confirmation: 'Chờ chủ sân xác nhận',
  awaiting_payment: 'Chờ thanh toán',
  confirmed: 'Đã xác nhận',
  completed: 'Đã hoàn thành',
  rejected: 'Bị từ chối',
  cancelled: 'Đã hủy',
  expired: 'Hết hạn',
  disputed: 'Tranh chấp',
  refunded: 'Đã hoàn tiền',
}
export const MATCH_STATUS_LABEL = { scheduled: 'Chờ kết quả', completed: 'Đã xác nhận', disputed: 'Tranh chấp', cancelled: 'Đã hủy' }
export const REPORT_TYPE_LABEL = { user: 'Người dùng', venue: 'Sân', venue_review: 'Đánh giá sân', partner_review: 'Đánh giá đối tác' }

/** Số liệu có thể vẽ trên biểu đồ xu hướng. */
export const TREND_METRICS = [
  { id: 'newUsers', label: 'Tài khoản mới' },
  { id: 'newVenues', label: 'Sân mới' },
  { id: 'newBookings', label: 'Booking mới' },
  { id: 'newMatches', label: 'Trận đấu mới' },
]

export async function fetchSystemStats({ from, to, granularity = 'day' } = {}) {
  const search = new URLSearchParams({ granularity })
  if (from) search.set('from', from)
  if (to) search.set('to', to)
  const data = await request(`/api/v1/admin/stats/system?${search.toString()}`)
  const point = (item) => ({
    period: item.period ?? null,
    newUsers: Number(item.newUsers),
    newVenues: Number(item.newVenues),
    newBookings: Number(item.newBookings),
    newMatches: Number(item.newMatches),
  })
  return {
    overview: data.overview,
    trend: { from: data.trend.from, to: data.trend.to, granularity: data.trend.granularity, points: data.trend.points.map(point), totals: point(data.trend.totals) },
  }
}
