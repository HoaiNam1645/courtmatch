/** Khóa / mở khóa khung giờ sân: 2.3.10, 2.3.11. */

import { request } from './client'

function path(venueId) {
  return `/api/v1/owner/venues/${encodeURIComponent(venueId)}/blocks`
}

function toUiBlock(block) {
  return {
    id: block.id,
    courtId: block.courtId,
    courtName: block.courtName,
    courtCode: block.courtCode,
    startTime: block.startTime,
    endTime: block.endTime,
    reason: block.reason ?? '',
    status: block.status,
    createdByName: block.createdByName ?? '',
    createdAt: block.createdAt,
  }
}

/** Ngày YYYY-MM-DD và giờ HH:mm theo giờ Việt Nam -> ISO-8601 UTC để gửi backend. */
export function vietnamToIso(date, time) {
  return new Date(`${date}T${time}:00+07:00`).toISOString()
}

export async function fetchBlocks(venueId, { page = 0, size = 50 } = {}) {
  const data = await request(`${path(venueId)}?page=${page}&size=${size}`)
  return { blocks: data.content.map(toUiBlock), totalElements: data.totalElements }
}

/** Khóa một khoảng giờ trên một hoặc nhiều sân con. */
export async function createBlocks(venueId, { courtIds, startTime, endTime, reason }) {
  const data = await request(path(venueId), { method: 'POST', body: { courtIds, startTime, endTime, reason } })
  return data.map(toUiBlock)
}

/** 2.3.11 — mở khóa một khoảng khóa; khung giờ đặt được lại ngay. */
export async function unlockBlock(venueId, blockId) {
  return toUiBlock(await request(`${path(venueId)}/${encodeURIComponent(blockId)}/unlock`, { method: 'POST' }))
}
