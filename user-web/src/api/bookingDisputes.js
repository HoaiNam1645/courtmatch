/** 2.3.85 - 2.3.86: người chơi báo cáo vấn đề về đơn đặt sân, chọn loại trong danh mục. */

import { request, requestBlob } from './client'

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
export async function createBookingDispute(bookingId, { category, description, refundRequested = false, refundAmount = null }) {
  const body = { category, description, refundRequested }
  if (refundRequested && refundAmount !== null && refundAmount !== '') body.refundAmount = Number(refundAmount)
  const data = await request(`/api/v1/bookings/${bookingId}/disputes`, { method: 'POST', body })
  return {
    id: data.id,
    bookingId: data.bookingId,
    bookingCode: data.bookingCode,
    category: data.category,
    categoryLabel: data.categoryLabel,
    description: data.description,
    status: data.status,
    refundRequested: Boolean(data.refundRequested),
    refundAmount: data.refundAmount == null ? null : Number(data.refundAmount),
    ownerResponseDeadline: data.ownerResponseDeadline,
    createdAt: data.createdAt,
    evidences: (data.evidences ?? []).map(toEvidence),
  }
}

/** Giới hạn ảnh bằng chứng (khớp backend, 2.3.88). */
export const EVIDENCE_LIMITS = { maxInitial: 5, maxTotal: 10, maxBytes: 5 * 1024 * 1024, types: ['image/jpeg', 'image/png', 'image/webp'] }

/** Lý do một file bị từ chối ở phía trình duyệt (backend vẫn kiểm tra lại bằng nội dung thật); null nếu hợp lệ. */
export function evidenceProblem(file) {
  if (!EVIDENCE_LIMITS.types.includes(file.type)) return 'Chỉ nhận ảnh JPEG, PNG hoặc WebP'
  if (file.size === 0) return 'File ảnh rỗng'
  if (file.size > EVIDENCE_LIMITS.maxBytes) return 'Ảnh vượt quá 5MB'
  return null
}

function toEvidence(item) {
  return {
    id: item.id,
    originalName: item.originalName,
    size: Number(item.size),
    contentType: item.contentType,
    uploadedAt: item.uploadedAt,
    supplementId: item.supplementId ?? null,
    url: item.url,
  }
}

/** 2.3.88: tải lên một ảnh bằng chứng (mỗi lần một ảnh). */
export async function uploadDisputeEvidence(disputeId, file) {
  const body = new FormData()
  body.append('file', file)
  return toEvidence(await request(`/api/v1/booking-disputes/${disputeId}/evidences`, { method: 'POST', body, timeoutMs: 60000 }))
}

/** 2.3.88: xóa ảnh bằng chứng khi chủ sân chưa giải trình. */
export async function deleteDisputeEvidence(disputeId, evidenceId) {
  await request(`/api/v1/booking-disputes/${disputeId}/evidences/${evidenceId}`, { method: 'DELETE' })
}

/** 2.3.88: lấy ảnh riêng tư (cần đăng nhập) dưới dạng Blob. */
export function fetchDisputeEvidenceBlob(url) {
  return requestBlob(url)
}
