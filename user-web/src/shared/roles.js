/** Vai trò lấy từ `user.roles` do backend trả về; không tự suy ra từ dữ liệu khác. */

export function isOwner(user) {
  return Boolean(user?.roles?.includes('owner'))
}

/**
 * Trang đích sau khi đăng nhập (2.3.1).
 *
 * Chủ sân vào khu quản lý; nếu đang dùng mật khẩu tạm thì phải đổi mật khẩu trước.
 */
export function homePathFor(user) {
  if (isOwner(user)) {
    return user.passwordChangeRequired ? '/owner/change-password' : '/owner'
  }
  return '/'
}
