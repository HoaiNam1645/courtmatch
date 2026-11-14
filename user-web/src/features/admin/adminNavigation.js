import { Building2, KeyRound, LayoutDashboard, UserRound, Users } from 'lucide-react'

/** Mục điều hướng của khu quản trị. Thêm mục khi chức năng tương ứng hoàn thành. */
export const adminNavigation = [
  { label: 'Tổng quan', to: '/admin', icon: LayoutDashboard, end: true },
  { label: 'Tài khoản', to: '/admin/users', icon: Users },
  { label: 'Người chơi', to: '/admin/players', icon: UserRound },
  { label: 'Chủ sân', to: '/admin/owner', icon: Building2 },
  { label: 'Vai trò & quyền', to: '/admin/permissions', icon: KeyRound },
]
