/** 2.3.24 — chủ sân gửi thông tin thanh toán hoa hồng cho Courtly. */

import { request } from './client'

export const COMMISSION_PAYMENT_STATUS_META = {
  pending_review: { label: 'Chờ Courtly xác nhận', className: 'bg-sky-100 text-sky-700' },
  confirmed: { label: 'Đã xác nhận', className: 'bg-emerald-100 text-emerald-700' },
  rejected: { label: 'Bị từ chối', className: 'bg-red-100 text-red-700' },
}

export function commissionPaymentStatusMeta(status) {
  return COMMISSION_PAYMENT_STATUS_META[status] ?? { label: status ?? 'Không rõ', className: 'bg-stone-200 text-stone-600' }
}

export function mapCommissionPayment(item) {
  return {
    id: item.id,
    settlementId: item.settlementId ?? null,
    periodFrom: item.periodFrom ?? null,
    periodTo: item.periodTo ?? null,
    amount: Number(item.amount ?? 0),
    paidOn: item.paidOn,
    referenceCode: item.referenceCode,
    proofUrl: item.proofUrl ?? '',
    note: item.note ?? '',
    status: item.status,
    reviewNote: item.reviewNote ?? '',
    reviewedAt: item.reviewedAt ?? null,
    createdAt: item.createdAt,
  }
}

export async function submitCommissionPayment({ settlementId = null, amount, paidOn, referenceCode, proofUrl = '', note = '' }) {
  return mapCommissionPayment(await request('/api/v1/owner/commission-payments', {
    method: 'POST',
    body: {
      settlementId,
      amount: Number(amount),
      paidOn,
      referenceCode: referenceCode.trim(),
      proofUrl: proofUrl.trim() || null,
      note: note.trim() || null,
    },
  }))
}
