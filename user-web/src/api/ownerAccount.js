/** 2.3.1 — tài khoản chủ sân do quản trị viên cấp. */

import { toUiUser } from './auth'
import { request } from './client'

/** Đổi mật khẩu tạm. Thành công thì backend gỡ cờ bắt buộc đổi mật khẩu. */
export async function changeOwnerPassword({ currentPassword, newPassword, confirmPassword }) {
  const data = await request('/api/v1/owner/account/password', {
    method: 'POST',
    body: { currentPassword, newPassword, confirmPassword },
  })
  return toUiUser(data)
}
