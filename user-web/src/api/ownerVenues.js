/** Sân của chủ sân: 2.3.3 thêm sân, 2.3.4 cập nhật thông tin sân. */

import { request } from './client'

/** Trạng thái hoạt động của sân (venues.status). */
export const VENUE_STATUS_META = {
  pending: { label: 'Chưa hoạt động', className: 'bg-stone-200 text-stone-700' },
  active: { label: 'Đang hoạt động', className: 'bg-emerald-100 text-emerald-700' },
  inactive: { label: 'Tạm ngưng', className: 'bg-stone-200 text-stone-700' },
  rejected: { label: 'Bị từ chối', className: 'bg-red-100 text-red-700' },
}

/** Trạng thái duyệt của quản trị viên (venues.approval_status). */
export const APPROVAL_STATUS_META = {
  pending: { label: 'Chờ duyệt', className: 'bg-amber-100 text-amber-800' },
  approved: { label: 'Đã duyệt', className: 'bg-emerald-100 text-emerald-700' },
  rejected: { label: 'Bị từ chối duyệt', className: 'bg-red-100 text-red-700' },
}

export const COURT_STATUS_META = {
  active: { label: 'Hoạt động', className: 'bg-emerald-100 text-emerald-700' },
  maintenance: { label: 'Bảo trì', className: 'bg-amber-100 text-amber-800' },
  inactive: { label: 'Ngừng', className: 'bg-stone-200 text-stone-700' },
}

export function statusMeta(table, value) {
  return table[value] ?? { label: value ?? 'Không rõ', className: 'bg-stone-200 text-stone-700' }
}

function toNumberOrNull(value) {
  return value === null || value === undefined || value === '' ? null : Number(value)
}

export async function fetchVenueOptions() {
  const data = await request('/api/v1/owner/venue-options')
  return {
    districts: data.districts ?? [],
    services: data.services ?? [],
    courtTypes: data.courtTypes ?? [],
    surfaceTypes: data.surfaceTypes ?? [],
    bounds: {
      minLatitude: Number(data.bounds.minLatitude),
      maxLatitude: Number(data.bounds.maxLatitude),
      minLongitude: Number(data.bounds.minLongitude),
      maxLongitude: Number(data.bounds.maxLongitude),
    },
    maxCourts: data.maxCourts,
  }
}

export async function fetchOwnerVenues({ page = 0, size = 50 } = {}) {
  const data = await request(`/api/v1/owner/venues?page=${page}&size=${size}`)
  return {
    venues: data.content.map((venue) => ({
      id: venue.id,
      name: venue.name,
      slug: venue.slug,
      district: venue.district ?? '',
      address: venue.address ?? '',
      status: venue.status,
      approvalStatus: venue.approvalStatus,
      courtCount: Number(venue.courtCount ?? 0),
      coverImageUrl: venue.coverImageUrl ?? null,
      createdAt: venue.createdAt,
    })),
    totalElements: data.totalElements,
  }
}

export function toUiVenueDetail(venue) {
  return {
    id: venue.id,
    name: venue.name ?? '',
    slug: venue.slug,
    description: venue.description ?? '',
    address: venue.address ?? '',
    ward: venue.ward ?? '',
    district: venue.district ?? '',
    latitude: toNumberOrNull(venue.latitude),
    longitude: toNumberOrNull(venue.longitude),
    phone: venue.phone ?? '',
    email: venue.email ?? '',
    status: venue.status,
    approvalStatus: venue.approvalStatus,
    services: (venue.services ?? []).map((service) => ({
      code: service.code,
      name: service.name,
      price: service.price == null ? '' : String(Number(service.price)),
      note: service.note ?? '',
    })),
    courts: (venue.courts ?? []).map((court) => ({
      id: court.id,
      name: court.name,
      courtCode: court.courtCode,
      courtType: court.courtType,
      surfaceType: court.surfaceType ?? '',
      indoor: Boolean(court.indoor),
      status: court.status,
    })),
    createdAt: venue.createdAt,
    updatedAt: venue.updatedAt,
  }
}

/** Phần thông tin chung + tiện ích, dùng chung cho thêm sân và cập nhật sân. */
export function toVenueInfoBody(values) {
  return {
    name: values.name,
    description: values.description || null,
    address: values.address,
    ward: values.ward || null,
    district: values.district,
    latitude: values.latitude,
    longitude: values.longitude,
    phone: values.phone,
    email: values.email || null,
    services: values.services.map((service) => ({
      code: service.code,
      price: service.price === '' ? null : Number(service.price),
      note: service.note || null,
    })),
  }
}

export function toCourtBody(court) {
  return {
    name: court.name,
    courtCode: court.courtCode,
    courtType: court.courtType,
    surfaceType: court.surfaceType || null,
    indoor: Boolean(court.indoor),
  }
}

/** 2.3.3 — thêm sân mới, sân luôn ở trạng thái chờ quản trị viên duyệt. */
export async function createOwnerVenue(values) {
  const data = await request('/api/v1/owner/venues', {
    method: 'POST',
    body: { ...toVenueInfoBody(values), courts: values.courts.map(toCourtBody) },
  })
  return toUiVenueDetail(data)
}

/** 2.3.4 — chi tiết sân của chủ sân, kể cả sân chưa duyệt. */
export async function fetchOwnerVenue(venueId) {
  return toUiVenueDetail(await request(`/api/v1/owner/venues/${encodeURIComponent(venueId)}`))
}

/** 2.3.4 — cập nhật thông tin chung và tiện ích. */
export async function updateOwnerVenue(venueId, values) {
  return toUiVenueDetail(await request(`/api/v1/owner/venues/${encodeURIComponent(venueId)}`, {
    method: 'PUT',
    body: toVenueInfoBody(values),
  }))
}

/** 2.3.4 — thêm sân con. */
export function addOwnerCourt(venueId, court) {
  return request(`/api/v1/owner/venues/${encodeURIComponent(venueId)}/courts`, {
    method: 'POST',
    body: toCourtBody(court),
  })
}

/** 2.3.4 — sửa sân con, kể cả đổi trạng thái hoạt động / bảo trì / ngừng. */
export function updateOwnerCourt(venueId, court) {
  return request(`/api/v1/owner/venues/${encodeURIComponent(venueId)}/courts/${encodeURIComponent(court.id)}`, {
    method: 'PUT',
    body: { ...toCourtBody(court), status: court.status },
  })
}
