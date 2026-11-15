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

/** Giá trị tối đa của phí cố định mỗi booking; khớp AdminFeePolicyService.MAX_FIXED_FEE. */
export const MAX_FIXED_FEE = 10_000_000

/** Kiểm tra giá trị phí theo đúng quy tắc của backend; trả về chuỗi lỗi hoặc rỗng. */
export function feeValueProblem(feeType, rawValue) {
  const text = String(rawValue ?? '').trim().replace(',', '.')
  if (text === '') return 'Vui lòng nhập giá trị phí'
  if (!/^\d+(\.\d+)?$/.test(text)) return 'Giá trị phí phải là số không âm'
  if (/\.\d{3,}/.test(text) && Number(text) !== Number(Number(text).toFixed(2))) return 'Giá trị phí tối đa 2 chữ số thập phân'
  const value = Number(text)
  if (feeType === 'percentage' && value > 100) return 'Phí phần trăm không được vượt quá 100'
  if (feeType === 'fixed' && value > MAX_FIXED_FEE) return 'Phí cố định tối đa 10.000.000đ mỗi booking'
  return ''
}

export async function createFeePolicy({ name, feeType, feeValue }) {
  return mapFeeConfig(await request('/api/v1/admin/fee-policies', {
    method: 'POST',
    body: { name: name.trim(), feeType, feeValue: Number(String(feeValue).trim().replace(',', '.')) },
  }))
}

export async function updateFeePolicy(policyId, { name, feeType, feeValue }) {
  return mapFeeConfig(await request(`/api/v1/admin/fee-policies/${encodeURIComponent(policyId)}`, {
    method: 'PUT',
    body: { name: name.trim(), feeType, feeValue: Number(String(feeValue).trim().replace(',', '.')) },
  }))
}

export async function activateFeePolicy(policyId) {
  return mapFeeConfig(await request(`/api/v1/admin/fee-policies/${encodeURIComponent(policyId)}/activate`, { method: 'POST' }))
}

/** Giá trị tối đa của ngưỡng; khớp AdminFeePolicyService ở backend. */
export const MAX_OVERDUE_AMOUNT = 100_000_000_000
export const MAX_OVERDUE_DAYS = 3650

/** Chuẩn hóa chuỗi số nhập tay ("1.000.000", "1000000", "7,5") thành số, hoặc null nếu để trống. */
export function parseThreshold(raw) {
  const text = String(raw ?? '').trim().replace(/\./g, '').replace(',', '.')
  return text === '' ? null : Number(text)
}

/**
 * Kiểm tra hai mức hạn chế theo cùng quy tắc của backend.
 *
 * @returns map lỗi theo khóa "level1.minOverdueAmount", "level2.enabled"...; rỗng nếu hợp lệ
 */
export function restrictionProblems(levels) {
  const errors = {}
  for (const level of levels) {
    const prefix = `level${level.level}.`
    const amount = parseThreshold(level.minOverdueAmount)
    const days = parseThreshold(level.minOverdueDays)
    if (amount !== null && (Number.isNaN(amount) || amount <= 0)) errors[`${prefix}minOverdueAmount`] = 'Ngưỡng số tiền phải lớn hơn 0'
    else if (amount !== null && amount > MAX_OVERDUE_AMOUNT) errors[`${prefix}minOverdueAmount`] = 'Ngưỡng số tiền tối đa 100.000.000.000đ'
    if (days !== null && (Number.isNaN(days) || !Number.isInteger(days) || days < 1 || days > MAX_OVERDUE_DAYS)) errors[`${prefix}minOverdueDays`] = `Ngưỡng số ngày là số nguyên từ 1 đến ${MAX_OVERDUE_DAYS}`
    if (level.enabled && amount === null && days === null) errors[`${prefix}enabled`] = 'Mức đang bật phải có ít nhất một ngưỡng (số tiền hoặc số ngày)'
  }
  const [first, second] = levels
  if (first.enabled && second.enabled && Object.keys(errors).length === 0) {
    const a1 = parseThreshold(first.minOverdueAmount)
    const a2 = parseThreshold(second.minOverdueAmount)
    const d1 = parseThreshold(first.minOverdueDays)
    const d2 = parseThreshold(second.minOverdueDays)
    if (a1 !== null && a2 !== null && a2 < a1) errors['level2.minOverdueAmount'] = 'Ngưỡng số tiền của mức 2 phải từ ngưỡng mức 1 trở lên'
    if (d1 !== null && d2 !== null && d2 < d1) errors['level2.minOverdueDays'] = 'Ngưỡng số ngày của mức 2 phải từ ngưỡng mức 1 trở lên'
  }
  return errors
}

export async function updateDebtRestrictions(levels) {
  return mapFeeConfig(await request('/api/v1/admin/fee-policies/debt-restrictions', {
    method: 'PUT',
    body: { levels: levels.map((level) => ({ level: level.level, enabled: level.enabled, minOverdueAmount: parseThreshold(level.minOverdueAmount), minOverdueDays: parseThreshold(level.minOverdueDays) })) },
  }))
}
