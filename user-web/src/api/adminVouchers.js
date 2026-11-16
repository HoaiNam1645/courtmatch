/** B4 — quản trị viên quản lý voucher giảm giá. */

import { request } from './client'

export const VOUCHER_STATE_TABS = ['all', 'active', 'scheduled', 'exhausted', 'expired', 'locked']

export const VOUCHER_STATE_META = {
  active: { label: 'Đang phát hành', className: 'bg-emerald-100 text-emerald-700' },
  scheduled: { label: 'Sắp bắt đầu', className: 'bg-sky-100 text-sky-700' },
  exhausted: { label: 'Hết lượt', className: 'bg-amber-100 text-amber-800' },
  expired: { label: 'Hết hạn', className: 'bg-slate-200 text-slate-600' },
  locked: { label: 'Đã khóa', className: 'bg-red-100 text-red-700' },
}

export function voucherStateMeta(state) {
  return VOUCHER_STATE_META[state] ?? { label: state ?? 'Không rõ', className: 'bg-stone-200 text-stone-600' }
}

const money = (value) => Number(value ?? 0)
const nullable = (value) => (value == null ? null : Number(value))

function mapSummary(item) {
  return {
    id: item.id,
    code: item.code,
    name: item.name,
    discountPercent: Number(item.discountPercent),
    maxDiscountAmount: nullable(item.maxDiscountAmount),
    validFrom: item.validFrom,
    validUntil: item.validUntil,
    totalLimit: nullable(item.totalLimit),
    usedCount: Number(item.usedCount),
    remaining: nullable(item.remaining),
    totalDiscount: money(item.totalDiscount),
    minElo: nullable(item.minElo),
    minBookingMatches: nullable(item.minBookingMatches),
    minActiveWeeks: nullable(item.minActiveWeeks),
    status: item.status,
    state: item.state,
    createdAt: item.createdAt,
  }
}

function mapDetail(data) {
  return {
    ...mapSummary(data.summary),
    description: data.description ?? '',
    createdBy: data.createdBy ?? '',
    redemptions: data.redemptions.map((item) => ({
      id: item.id,
      userName: item.userName,
      bookingCode: item.bookingCode,
      originalAmount: money(item.originalAmount),
      discountAmount: money(item.discountAmount),
      status: item.status,
      createdAt: item.createdAt,
      releasedAt: item.releasedAt ?? null,
    })),
  }
}

/** Danh sách đầy đủ theo từ khóa; các tab trạng thái lọc phía giao diện từ cùng một lần tải. */
export async function fetchAdminVouchers({ q = '' } = {}) {
  const search = new URLSearchParams({ state: 'all' })
  if (q.trim()) search.set('q', q.trim())
  const data = await request(`/api/v1/admin/vouchers?${search.toString()}`)
  return {
    vouchers: data.content.map(mapSummary),
    totals: { voucherCount: Number(data.totals.voucherCount), redemptions: Number(data.totals.redemptions), totalDiscount: money(data.totals.totalDiscount) },
  }
}

export async function fetchAdminVoucher(voucherId) {
  return mapDetail(await request(`/api/v1/admin/vouchers/${encodeURIComponent(voucherId)}`))
}

function toBody(form) {
  const number = (value) => (String(value ?? '').trim() === '' ? null : Number(String(value).trim().replace(',', '.')))
  return {
    code: form.code.trim(),
    name: form.name.trim(),
    description: form.description.trim() || null,
    discountPercent: number(form.discountPercent),
    maxDiscountAmount: number(form.maxDiscountAmount),
    validFrom: form.validFrom ? new Date(form.validFrom).toISOString() : null,
    validUntil: form.validUntil ? new Date(form.validUntil).toISOString() : null,
    totalLimit: number(form.totalLimit),
    minElo: number(form.minElo),
    minBookingMatches: number(form.minBookingMatches),
    minActiveWeeks: number(form.minActiveWeeks),
  }
}

export async function createAdminVoucher(form) {
  return mapDetail(await request('/api/v1/admin/vouchers', { method: 'POST', body: toBody(form) }))
}

export async function updateAdminVoucher(voucherId, form) {
  return mapDetail(await request(`/api/v1/admin/vouchers/${encodeURIComponent(voucherId)}`, { method: 'PUT', body: toBody(form) }))
}

export async function lockAdminVoucher(voucherId) {
  return mapDetail(await request(`/api/v1/admin/vouchers/${encodeURIComponent(voucherId)}/lock`, { method: 'POST' }))
}

export async function unlockAdminVoucher(voucherId) {
  return mapDetail(await request(`/api/v1/admin/vouchers/${encodeURIComponent(voucherId)}/unlock`, { method: 'POST' }))
}

/** Giá trị ban đầu của form: voucher mới (hiệu lực 30 ngày từ hôm nay) hoặc voucher đang sửa. */
export function emptyVoucherForm() {
  const from = new Date()
  const until = new Date(Date.now() + 30 * 86400000)
  return { code: '', name: '', description: '', discountPercent: '', maxDiscountAmount: '', validFrom: toLocalInput(from.toISOString()), validUntil: toLocalInput(until.toISOString()), totalLimit: '', minElo: '', minBookingMatches: '', minActiveWeeks: '' }
}

export function formFromVoucher(voucher) {
  const text = (value) => (value == null ? '' : String(value))
  return {
    code: voucher.code,
    name: voucher.name,
    description: voucher.description ?? '',
    discountPercent: text(voucher.discountPercent),
    maxDiscountAmount: text(voucher.maxDiscountAmount),
    validFrom: toLocalInput(voucher.validFrom),
    validUntil: toLocalInput(voucher.validUntil),
    totalLimit: text(voucher.totalLimit),
    minElo: text(voucher.minElo),
    minBookingMatches: text(voucher.minBookingMatches),
    minActiveWeeks: text(voucher.minActiveWeeks),
  }
}

/** ISO -> giá trị cho ô datetime-local (giờ của trình duyệt). */
export function toLocalInput(iso) {
  const date = new Date(iso)
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000)
  return local.toISOString().slice(0, 16)
}

/** Kiểm tra form theo cùng quy tắc của backend; trả về map lỗi theo tên trường. */
export function voucherProblems(form, { editing }) {
  const errors = {}
  const num = (value) => (String(value ?? '').trim() === '' ? null : Number(String(value).trim().replace(',', '.')))
  if (!editing && !/^[A-Za-z0-9_-]{3,40}$/.test(form.code.trim())) errors.code = 'Mã từ 3 đến 40 ký tự gồm chữ, số, gạch nối hoặc gạch dưới'
  if (form.name.trim().length < 2 || form.name.trim().length > 150) errors.name = 'Tên voucher từ 2 đến 150 ký tự'
  const percent = num(form.discountPercent)
  if (percent === null || Number.isNaN(percent) || percent <= 0 || percent > 100) errors.discountPercent = 'Phần trăm giảm lớn hơn 0 và tối đa 100'
  else if (Math.round(percent * 100) / 100 !== percent) errors.discountPercent = 'Phần trăm giảm tối đa 2 chữ số thập phân'
  const cap = num(form.maxDiscountAmount)
  if (cap !== null && (Number.isNaN(cap) || cap <= 0 || cap > 100000000)) errors.maxDiscountAmount = 'Trần giảm lớn hơn 0 và tối đa 100.000.000đ'
  if (!form.validFrom) errors.validFrom = 'Chọn thời điểm bắt đầu'
  if (!form.validUntil) errors.validUntil = 'Chọn thời điểm hết hạn'
  else if (form.validFrom && new Date(form.validUntil) <= new Date(form.validFrom)) errors.validUntil = 'Hết hạn phải sau thời điểm bắt đầu'
  const intOr = (value, min, max) => { const n = num(value); return n === null ? '' : Number.isNaN(n) || !Number.isInteger(n) || n < min || n > max ? 'bad' : '' }
  if (intOr(form.totalLimit, 1, 100000)) errors.totalLimit = 'Tổng số lượt là số nguyên từ 1 đến 100.000'
  const elo = num(form.minElo)
  if (elo !== null && (Number.isNaN(elo) || elo < 0 || elo > 5000)) errors.minElo = 'Elo tối thiểu từ 0 đến 5000'
  if (intOr(form.minBookingMatches, 1, 100000)) errors.minBookingMatches = 'Số trận tối thiểu là số nguyên từ 1'
  if (intOr(form.minActiveWeeks, 1, 520)) errors.minActiveWeeks = 'Số tuần tối thiểu là số nguyên từ 1 đến 520'
  return errors
}
