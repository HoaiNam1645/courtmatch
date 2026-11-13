/** 2.3.18 — phí nền tảng (hoa hồng Courtly) của từng booking. */

import { request } from './client'

/** Nhãn trạng thái hoa hồng; một chỗ duy nhất. */
export const COMMISSION_STATUS_META = {
  pending: { label: 'Chưa đến hạn', className: 'bg-stone-200 text-stone-700' },
  due: { label: 'Cần thanh toán', className: 'bg-orange-100 text-orange-700' },
  adjusted: { label: 'Đã điều chỉnh', className: 'bg-sky-100 text-sky-700' },
  suspended: { label: 'Tạm hoãn do tranh chấp', className: 'bg-amber-100 text-amber-800' },
  waived: { label: 'Được miễn', className: 'bg-violet-100 text-violet-700' },
  settled: { label: 'Đã đối soát', className: 'bg-emerald-100 text-emerald-700' },
}

export const COMMISSION_TABS = ['all', 'due', 'pending', 'adjusted', 'suspended', 'settled', 'waived']

export function commissionStatusMeta(status) {
  return COMMISSION_STATUS_META[status] ?? { label: status ?? 'Không rõ', className: 'bg-stone-200 text-stone-600' }
}

/** "12,5%" hoặc số tiền cố định theo loại phí của chính sách. */
export function describeFee(feeType, feeValue, formatMoney) {
  const value = Number(feeValue)
  return feeType === 'fixed' ? `${formatMoney(value)}/booking` : `${value.toLocaleString('vi-VN')}%`
}

export async function fetchCommissions({ venueId = '', status = 'all', from = '', to = '', page = 0, size = 20 } = {}) {
  const search = new URLSearchParams({ status, page: String(page), size: String(size) })
  if (venueId) search.set('venueId', venueId)
  if (from) search.set('from', from)
  if (to) search.set('to', to)
  const data = await request(`/api/v1/owner/commissions?${search.toString()}`)
  return {
    commissions: data.content.map((item) => ({
      bookingId: item.bookingId,
      bookingCode: item.bookingCode,
      venueName: item.venueName,
      playerName: item.playerName,
      grossAmount: Number(item.grossAmount ?? 0),
      feeType: item.feeType,
      feeValue: Number(item.feeValue ?? 0),
      commissionAmount: Number(item.commissionAmount ?? 0),
      policyName: item.policyName ?? '',
      status: item.status,
      overdue: Boolean(item.overdue),
      calculatedAt: item.calculatedAt,
      dueAt: item.dueAt ?? null,
      settledAt: item.settledAt ?? null,
    })),
    page: data.page,
    totalPages: data.totalPages,
    totalElements: data.totalElements,
    totalCommission: Number(data.totalCommission ?? 0),
    activePolicy: data.activePolicy ? { name: data.activePolicy.name, feeType: data.activePolicy.feeType, feeValue: Number(data.activePolicy.feeValue) } : null,
  }
}
