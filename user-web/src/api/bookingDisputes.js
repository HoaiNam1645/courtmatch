/** 2.3.85 - 2.3.86: người chơi báo cáo vấn đề về đơn đặt sân, chọn loại trong danh mục. */

import { request } from './client'

/** Trạng thái báo cáo dành cho người chơi. */
export const DISPUTE_STATUS_LABEL = {
  open: 'Mới tạo',
  awaiting_owner_response: 'Chờ chủ sân giải trình',
  reviewing: 'Quản trị viên đang xem xét',
  resolved: 'Đã giải quyết',
  rejected: 'Đã bác bỏ',
  cancelled: 'Đã hủy',
}

/** 2.3.86: danh mục loại báo cáo (mã, tên, gợi ý nội dung, số ký tự mô tả tối thiểu). */
export async function fetchDisputeCategories() {
  const data = await request('/api/v1/booking-disputes/categories')
  return data.map((item) => ({ code: item.code, label: item.label, hint: item.hint, minDescriptionLength: Number(item.minDescriptionLength) }))
}

/** 2.3.85: tạo báo cáo về một đơn đã xác nhận hoặc hoàn thành. */
export async function createBookingDispute(bookingId, { category, description }) {
  const data = await request(`/api/v1/bookings/${bookingId}/disputes`, { method: 'POST', body: { category, description } })
  return {
    id: data.id,
    bookingId: data.bookingId,
    bookingCode: data.bookingCode,
    category: data.category,
    categoryLabel: data.categoryLabel,
    description: data.description,
    status: data.status,
    ownerResponseDeadline: data.ownerResponseDeadline,
    createdAt: data.createdAt,
  }
}
