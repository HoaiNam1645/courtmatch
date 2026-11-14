/** 2.3.32 — quản trị viên xem chủ sân duy nhất và quyết định xác minh hồ sơ. */

import { request } from './client'

export const VERIFICATION_META = {
  pending: { label: 'Chờ xác minh', className: 'bg-amber-100 text-amber-800' },
  verified: { label: 'Đã xác minh', className: 'bg-emerald-100 text-emerald-700' },
  rejected: { label: 'Bị từ chối', className: 'bg-red-100 text-red-700' },
}

export function verificationMeta(status) {
  return VERIFICATION_META[status] ?? { label: status ?? 'Không rõ', className: 'bg-stone-200 text-stone-600' }
}

function mapDetail(data) {
  if (!data.owner) return { exists: false }
  const owner = data.owner
  const profile = data.profile
  return {
    exists: true,
    owner: {
      id: owner.id,
      fullName: owner.fullName,
      email: owner.email ?? '',
      phone: owner.phone ?? '',
      status: owner.status,
      passwordChangeRequired: Boolean(owner.passwordChangeRequired),
      lastLoginAt: owner.lastLoginAt ?? null,
      createdAt: owner.createdAt,
      lastStatusChange: owner.lastStatusChange
        ? { status: owner.lastStatusChange.status, reason: owner.lastStatusChange.reason ?? '', actor: owner.lastStatusChange.actor ?? '', at: owner.lastStatusChange.at }
        : null,
    },
    profile: profile
      ? {
        businessName: profile.businessName ?? '',
        taxCode: profile.taxCode ?? '',
        identityNumber: profile.identityNumber ?? '',
        bankName: profile.bankName ?? '',
        bankAccountNo: profile.bankAccountNo ?? '',
        bankAccountName: profile.bankAccountName ?? '',
        verificationStatus: profile.verificationStatus,
        verifiedAt: profile.verifiedAt ?? null,
        verificationNote: profile.verificationNote ?? '',
        verifiedBy: profile.verifiedBy ?? '',
      }
      : null,
    overview: {
      venuesByApproval: data.overview.venuesByApproval ?? {},
      outstandingCommission: Number(data.overview.outstandingCommission ?? 0),
      activeBookings: Number(data.overview.activeBookings ?? 0),
    },
  }
}

export async function fetchAdminOwner() {
  return mapDetail(await request('/api/v1/admin/owner'))
}

/** status: verified | rejected | pending; reason bắt buộc khi từ chối hoặc đặt lại. */
export async function reviewAdminOwner({ status, reason = '' }) {
  return mapDetail(await request('/api/v1/admin/owner/verification', { method: 'POST', body: { status, reason: reason.trim() || null } }))
}
