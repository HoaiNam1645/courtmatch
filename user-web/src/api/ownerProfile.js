/** 2.3.2 — hồ sơ chủ sân. */

import { request } from './client'

export const VERIFICATION_STATUS_META = {
  pending: { label: 'Chờ quản trị viên xác minh', className: 'bg-amber-100 text-amber-800' },
  verified: { label: 'Đã xác minh', className: 'bg-emerald-100 text-emerald-700' },
  rejected: { label: 'Xác minh bị từ chối', className: 'bg-red-100 text-red-700' },
}

function toUiProfile(profile) {
  return {
    fullName: profile.fullName ?? '',
    email: profile.email ?? '',
    phone: profile.phone ?? '',
    businessName: profile.businessName ?? '',
    taxCode: profile.taxCode ?? '',
    bankName: profile.bankName ?? '',
    bankAccountNo: profile.bankAccountNo ?? '',
    bankAccountName: profile.bankAccountName ?? '',
    verificationStatus: profile.verificationStatus,
    verifiedAt: profile.verifiedAt ?? null,
    updatedAt: profile.updatedAt ?? null,
  }
}

export async function fetchOwnerProfile() {
  return toUiProfile(await request('/api/v1/owner/profile'))
}

/** Email và trạng thái xác minh không gửi lên: backend không cho chủ sân tự sửa. */
export async function updateOwnerProfile(values) {
  return toUiProfile(await request('/api/v1/owner/profile', {
    method: 'PUT',
    body: {
      fullName: values.fullName,
      phone: values.phone,
      businessName: values.businessName,
      taxCode: values.taxCode,
      bankName: values.bankName,
      bankAccountNo: values.bankAccountNo,
      bankAccountName: values.bankAccountName,
    },
  }))
}
