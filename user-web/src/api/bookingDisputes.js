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

/** Kết luận của quản trị viên (2.3.100 - 2.3.102) hiển thị cho người chơi. */
export const RESOLUTION_LABEL = {
  refund_full: 'Hoàn tiền toàn phần',
  refund_half: 'Hoàn tiền một nửa',
  rejected: 'Bác bỏ yêu cầu hoàn tiền',
  dismissed: 'Bác bỏ báo cáo',
}

export const DISPUTE_ACTOR_LABEL = { you: 'Bạn', owner: 'Chủ sân', admin: 'Quản trị viên' }
export const DISPUTE_TAB_FILTERS = [['all', 'Tất cả'], ['active', 'Đang xử lý'], ['closed', 'Đã đóng']]

function toSummary(item) {
  return {
    id: item.id,
    bookingId: item.bookingId,
    bookingCode: item.bookingCode,
    venueName: item.venueName,
    category: item.category,
    categoryLabel: item.categoryLabel,
    status: item.status,
    closed: Boolean(item.closed),
    refundRequested: Boolean(item.refundRequested),
    refundAmount: item.refundAmount == null ? null : Number(item.refundAmount),
    evidenceCount: Number(item.evidenceCount ?? 0),
    createdAt: item.createdAt,
    ownerResponseDeadline: item.ownerResponseDeadline ?? null,
    resolution: item.resolution ?? null,
  }
}

/** 2.3.89: danh sách báo cáo đã gửi của chính mình (all / active / closed). */
export async function fetchMyDisputes({ status = 'all', page = 0, size = 20 } = {}) {
  const data = await request(`/api/v1/booking-disputes?status=${status}&page=${page}&size=${size}`)
  return {
    items: data.content.map(toSummary),
    page: Number(data.page),
    totalPages: Number(data.totalPages),
    totalElements: Number(data.totalElements),
  }
}

/** 2.3.90: chi tiết một báo cáo kèm dòng thời gian trao đổi. */
export async function fetchMyDispute(disputeId) {
  const data = await request(`/api/v1/booking-disputes/${disputeId}`)
  return {
    id: data.id,
    category: data.category,
    categoryLabel: data.categoryLabel,
    status: data.status,
    closed: Boolean(data.closed),
    refundRequested: Boolean(data.refundRequested),
    refundAmount: data.refundAmount == null ? null : Number(data.refundAmount),
    createdAt: data.createdAt,
    ownerResponseDeadline: data.ownerResponseDeadline ?? null,
    booking: {
      id: data.booking.id,
      bookingCode: data.booking.bookingCode,
      venueName: data.booking.venueName,
      venueSlug: data.booking.venueSlug,
      courtName: data.booking.courtName ?? '',
      startTime: data.booking.startTime ?? null,
      endTime: data.booking.endTime ?? null,
      payableAmount: Number(data.booking.payableAmount ?? 0),
    },
    timeline: data.timeline.map((entry) => ({
      type: entry.type,
      actor: entry.actor,
      at: entry.at,
      text: entry.text ?? '',
      label: entry.label ?? null,
      evidences: (entry.evidences ?? []).map(toEvidence),
    })),
    canSupplement: Boolean(data.canSupplement),
    canCancel: Boolean(data.canCancel),
    supplementsRemaining: Number(data.supplementsRemaining ?? 0),
    evidenceRemaining: Number(data.evidenceRemaining ?? 0),
  }
}
