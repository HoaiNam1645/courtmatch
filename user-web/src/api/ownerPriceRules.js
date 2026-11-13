/** 2.3.5 — bảng giá của các sân con. */

import { request } from './client'

function toUiRule(rule) {
  return {
    id: rule.id,
    dayOfWeek: rule.dayOfWeek ?? null,
    startTime: rule.startTime,
    endTime: rule.endTime,
    pricePerHour: Number(rule.pricePerHour),
    effectiveFrom: rule.effectiveFrom,
    effectiveTo: rule.effectiveTo ?? null,
    status: rule.status,
  }
}

export async function fetchPriceRules(venueId) {
  const data = await request(`/api/v1/owner/venues/${encodeURIComponent(venueId)}/price-rules`)
  return data.courts.map((court) => ({
    courtId: court.courtId,
    courtName: court.courtName,
    courtCode: court.courtCode,
    courtStatus: court.courtStatus,
    rules: court.rules.map(toUiRule),
  }))
}

function toBody(values) {
  return {
    dayOfWeek: values.dayOfWeek === '' ? null : Number(values.dayOfWeek),
    startTime: values.startTime,
    endTime: values.endTime,
    pricePerHour: values.pricePerHour === '' ? null : Number(values.pricePerHour),
    effectiveFrom: values.effectiveFrom || null,
    effectiveTo: values.effectiveTo || null,
  }
}

function rulesPath(venueId, courtId) {
  return `/api/v1/owner/venues/${encodeURIComponent(venueId)}/courts/${encodeURIComponent(courtId)}/price-rules`
}

export function createPriceRule(venueId, courtId, values) {
  return request(rulesPath(venueId, courtId), { method: 'POST', body: toBody(values) })
}

export function updatePriceRule(venueId, courtId, ruleId, values) {
  return request(`${rulesPath(venueId, courtId)}/${encodeURIComponent(ruleId)}`, { method: 'PUT', body: toBody(values) })
}

/** Ngừng áp dụng; bản ghi vẫn được giữ làm lịch sử giá. */
export function deactivatePriceRule(venueId, courtId, ruleId) {
  return request(`${rulesPath(venueId, courtId)}/${encodeURIComponent(ruleId)}`, { method: 'DELETE' })
}
