/** 2.3.31 — quản trị viên quản lý tài khoản người chơi. */

import { request } from './client'

export const SKILL_LABEL = { beginner: 'Mới chơi', intermediate: 'Trung bình', advanced: 'Khá', professional: 'Chuyên nghiệp' }
export const PLAYER_SKILLS = ['all', 'beginner', 'intermediate', 'advanced', 'professional']

export async function fetchAdminPlayers({ q = '', status = 'all', skill = 'all', from = '', to = '', page = 0, size = 20 } = {}) {
  const search = new URLSearchParams({ status, skill, page: String(page), size: String(size) })
  if (q.trim()) search.set('q', q.trim())
  if (from) search.set('from', from)
  if (to) search.set('to', to)
  const data = await request(`/api/v1/admin/players?${search.toString()}`)
  return {
    players: data.content.map((item) => ({
      id: item.id,
      fullName: item.fullName,
      email: item.email ?? '',
      phone: item.phone ?? '',
      status: item.status,
      skillLevel: item.skillLevel ?? null,
      bookingCount: Number(item.bookingCount ?? 0),
      lastLoginAt: item.lastLoginAt ?? null,
      createdAt: item.createdAt,
    })),
    page: data.page,
    totalPages: data.totalPages,
    totalElements: data.totalElements,
    statusCounts: data.statusCounts ?? {},
  }
}

function mapDetail(data) {
  const account = data.account
  return {
    id: account.id,
    fullName: account.fullName,
    email: account.email ?? '',
    phone: account.phone ?? '',
    status: account.status,
    emailVerified: Boolean(account.emailVerified),
    lastLoginAt: account.lastLoginAt ?? null,
    createdAt: account.createdAt,
    lastStatusChange: account.lastStatusChange
      ? { status: account.lastStatusChange.status, reason: account.lastStatusChange.reason ?? '', actor: account.lastStatusChange.actor ?? '', at: account.lastStatusChange.at }
      : null,
    gender: data.gender ?? null,
    dateOfBirth: data.dateOfBirth ?? null,
    skillLevel: data.skillLevel ?? null,
    skillScore: data.skillScore === null || data.skillScore === undefined ? null : Number(data.skillScore),
    dominantHand: data.dominantHand ?? null,
    bio: data.bio ?? '',
    bookingCount: Number(data.bookingCount ?? 0),
    bookingsByStatus: data.bookingsByStatus ?? {},
    matchCount: Number(data.matchCount ?? 0),
    reportCount: Number(data.reportCount ?? 0),
    recentBookings: data.recentBookings.map((item) => ({
      id: item.id,
      bookingCode: item.bookingCode,
      venueName: item.venueName,
      status: item.status,
      totalAmount: Number(item.totalAmount ?? 0),
      createdAt: item.createdAt,
    })),
  }
}

export async function fetchAdminPlayer(playerId) {
  return mapDetail(await request(`/api/v1/admin/players/${encodeURIComponent(playerId)}`))
}

export async function lockAdminPlayer(playerId, reason) {
  return mapDetail(await request(`/api/v1/admin/players/${encodeURIComponent(playerId)}/lock`, { method: 'POST', body: { reason: reason.trim() } }))
}

export async function unlockAdminPlayer(playerId) {
  return mapDetail(await request(`/api/v1/admin/players/${encodeURIComponent(playerId)}/unlock`, { method: 'POST' }))
}
