/** 2.3.108: theo dõi và xác nhận chủ sân đã hoàn tiền trực tiếp cho người chơi. */

import { request } from './client'

export const REFUND_STATUS = {
  awaiting_owner_refund: { label: 'Chờ chủ sân hoàn tiền', className: 'bg-amber-100 text-amber-800' },
  owner_reported_refunded: { label: 'Chủ sân báo đã hoàn', className: 'bg-violet-100 text-violet-700' },
  confirmed: { label: 'Đã hoàn tất', className: 'bg-emerald-100 text-emerald-700' },
}

function toRefund(data) {
  return {
    id: data.id,
    amount: Number(data.amount),
    status: data.status,
    createdAt: data.createdAt,
    ownerReportedAt: data.ownerReportedAt ?? null,
    ownerNote: data.ownerNote ?? '',
    proofUrl: data.proofUrl ?? null,
    playerComplaint: data.playerComplaint ?? '',
    complainedAt: data.complainedAt ?? null,
    confirmedAt: data.confirmedAt ?? null,
    autoConfirmed: Boolean(data.autoConfirmed),
    autoConfirmAt: data.autoConfirmAt ?? null,
    canReport: Boolean(data.canReport),
    canConfirm: Boolean(data.canConfirm),
    canAdminConfirm: Boolean(data.canAdminConfirm),
  }
}

/** Khoản hoàn của báo cáo; null khi báo cáo chưa có quyết định hoàn tiền. */
export async function fetchDisputeRefund(disputeId) {
  const data = await request(`/api/v1/booking-disputes/${disputeId}/refund`)
  return data.refund ? toRefund(data.refund) : null
}

export async function reportOwnerRefund(refundId, file, note) {
  const body = new FormData()
  body.append('file', file)
  if (note?.trim()) body.append('note', note.trim())
  return toRefund(await request(`/api/v1/owner/refunds/${refundId}/report-paid`, { method: 'POST', body, timeoutMs: 60000 }))
}

export async function confirmRefundAsPlayer(refundId) {
  return toRefund(await request(`/api/v1/refunds/${refundId}/confirm`, { method: 'POST' }))
}

export async function complainRefund(refundId, reason) {
  return toRefund(await request(`/api/v1/refunds/${refundId}/dispute`, { method: 'POST', body: { reason: reason.trim() } }))
}

export async function confirmRefundAsAdmin(refundId, note) {
  return toRefund(await request(`/api/v1/admin/refunds/${refundId}/confirm`, { method: 'POST', body: { note: note.trim() } }))
}
