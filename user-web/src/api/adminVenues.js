/** 2.3.34 — quản trị viên duyệt sân cầu lông. */

import { request } from './client'

/** Quận / huyện Đà Nẵng, khớp VenueCatalog.DISTRICTS ở backend. */
export const DA_NANG_DISTRICTS = ['Hải Châu', 'Thanh Khê', 'Sơn Trà', 'Ngũ Hành Sơn', 'Liên Chiểu', 'Cẩm Lệ', 'Hòa Vang']

export const APPROVAL_TABS = ['pending', 'approved', 'rejected', 'all']

export const APPROVAL_META = {
  pending: { label: 'Chờ duyệt', className: 'bg-amber-100 text-amber-800' },
  approved: { label: 'Đã duyệt', className: 'bg-emerald-100 text-emerald-700' },
  rejected: { label: 'Đã từ chối', className: 'bg-red-100 text-red-700' },
}

export function approvalMeta(status) {
  return APPROVAL_META[status] ?? { label: status ?? 'Không rõ', className: 'bg-stone-200 text-stone-600' }
}

export async function fetchAdminVenues({ approval = 'pending', district = 'all', q = '', page = 0, size = 20 } = {}) {
  const search = new URLSearchParams({ approval, district, page: String(page), size: String(size) })
  if (q.trim()) search.set('q', q.trim())
  const data = await request(`/api/v1/admin/venues?${search.toString()}`)
  return {
    venues: data.content.map((item) => ({
      id: item.id,
      name: item.name,
      district: item.district ?? '',
      address: item.address ?? '',
      ownerName: item.ownerName,
      ownerEmail: item.ownerEmail ?? '',
      status: item.status,
      approvalStatus: item.approvalStatus,
      courtCount: Number(item.courtCount ?? 0),
      createdAt: item.createdAt,
    })),
    page: data.page,
    totalPages: data.totalPages,
    totalElements: data.totalElements,
    approvalCounts: data.approvalCounts ?? {},
  }
}

function mapDetail(data) {
  const venue = data.venue
  return {
    id: venue.id,
    name: venue.name,
    description: venue.description ?? '',
    address: venue.address ?? '',
    ward: venue.ward ?? '',
    district: venue.district ?? '',
    latitude: venue.latitude === null ? null : Number(venue.latitude),
    longitude: venue.longitude === null ? null : Number(venue.longitude),
    phone: venue.phone ?? '',
    status: venue.status,
    approvalStatus: venue.approvalStatus,
    services: venue.services.map((item) => item.name),
    courts: venue.courts,
    createdAt: venue.createdAt,
    owner: data.owner,
    images: data.images,
    hours: data.hours,
    courtPrices: data.courtPrices.map((item) => ({ ...item, minPrice: item.minPrice === null ? null : Number(item.minPrice), maxPrice: item.maxPrice === null ? null : Number(item.maxPrice) })),
    checks: data.checks,
    canApprove: Boolean(data.canApprove),
    approvedBy: data.approvedBy ?? '',
    approvedAt: data.approvedAt ?? null,
    rejection: data.rejection
      ? { code: data.rejection.code, reason: data.rejection.reason, rejectedBy: data.rejection.rejectedBy ?? '', rejectedAt: data.rejection.rejectedAt }
      : null,
  }
}

export async function fetchAdminVenue(venueId) {
  return mapDetail(await request(`/api/v1/admin/venues/${encodeURIComponent(venueId)}`))
}

export async function approveAdminVenue(venueId) {
  return mapDetail(await request(`/api/v1/admin/venues/${encodeURIComponent(venueId)}/approve`, { method: 'POST' }))
}

/** Danh mục lý do từ chối lấy từ backend; "other" bắt buộc kèm ghi chú. */
export async function fetchVenueRejectReasons() {
  const data = await request('/api/v1/admin/venues/reject-reasons')
  return data.reasons.map((reason) => ({ code: reason.code, label: reason.label, noteRequired: Boolean(reason.noteRequired) }))
}

export async function rejectAdminVenue(venueId, { reasonCode, note = '' }) {
  return mapDetail(await request(`/api/v1/admin/venues/${encodeURIComponent(venueId)}/reject`, {
    method: 'POST',
    body: { reasonCode, note: note.trim() || null },
  }))
}
