/** 2.3.39 - 2.3.41 — cấu hình phí nền tảng và ngưỡng hạn chế công nợ quá hạn. */

import { formatCurrency } from '../shared/utils'
import { request } from './client'

export const FEE_TYPE_LABEL = { percentage: 'Phần trăm', fixed: 'Số tiền cố định' }

export const RESTRICTION_LEVEL_META = {
  1: { title: 'Mức 1 — Chặn nhận booking mới', effect: 'Chủ sân vẫn xử lý được booking đã nhận nhưng không nhận thêm booking mới.' },
  2: { title: 'Mức 2 — Tạm ẩn sân', effect: 'Sân của chủ sân tạm thời không hiển thị cho người chơi cho tới khi thanh toán công nợ.' },
}

/** "10%" hoặc "15.000đ / booking". */
export function feeLabel(feeType, feeValue) {
  return feeType === 'percentage' ? `${Number(feeValue).toLocaleString('vi-VN')}%` : `${formatCurrency(Number(feeValue))} / booking`
}

/** Mô tả điều kiện áp dụng của một mức hạn chế. */
export function thresholdLabel(restriction) {
  const parts = []
  if (restriction.minOverdueAmount != null) parts.push(`công nợ quá hạn từ ${formatCurrency(restriction.minOverdueAmount)}`)
  if (restriction.minOverdueDays != null) parts.push(`quá hạn từ ${restriction.minOverdueDays} ngày`)
  return parts.length === 0 ? 'Chưa đặt ngưỡng' : parts.join(' hoặc ')
}

function mapPolicy(policy) {
  return {
    id: policy.id,
    name: policy.name,
    feeType: policy.feeType,
    feeValue: Number(policy.feeValue),
    status: policy.status,
    effectiveFrom: policy.effectiveFrom,
    effectiveTo: policy.effectiveTo ?? null,
    createdBy: policy.createdBy ?? '',
    createdAt: policy.createdAt,
    commissionCount: Number(policy.commissionCount ?? 0),
    commissionTotal: Number(policy.commissionTotal ?? 0),
  }
}

function mapRestriction(item) {
  return {
    level: Number(item.level),
    enabled: Boolean(item.enabled),
    minOverdueAmount: item.minOverdueAmount == null ? null : Number(item.minOverdueAmount),
    minOverdueDays: item.minOverdueDays == null ? null : Number(item.minOverdueDays),
    updatedBy: item.updatedBy ?? '',
    updatedAt: item.updatedAt ?? null,
  }
}

export function mapFeeConfig(data) {
  return {
    active: data.active ? mapPolicy(data.active) : null,
    policies: data.policies.map(mapPolicy),
    restrictions: data.restrictions.map(mapRestriction),
  }
}

export async function fetchFeePolicies() {
  return mapFeeConfig(await request('/api/v1/admin/fee-policies'))
}
