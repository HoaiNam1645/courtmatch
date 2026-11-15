import { BarChart3, Building2, Coins, HandCoins, ClipboardCheck, Flag, KeyRound, LayoutDashboard, MessageSquareWarning, UserRound, Users } from 'lucide-react'

/**
 * Mục điều hướng của khu quản trị, chia nhóm để thanh bên không rối khi thêm chức năng.
 * Thêm mục vào đúng nhóm khi chức năng tương ứng hoàn thành.
 */
export const adminNavigationGroups = [
  {
    label: 'Tổng quan',
    items: [
      { label: 'Tổng quan', to: '/admin', icon: LayoutDashboard, end: true },
      { label: 'Thống kê', to: '/admin/stats', icon: BarChart3 },
    ],
  },
  {
    label: 'Người dùng',
    items: [
      { label: 'Tài khoản', to: '/admin/users', icon: Users },
      { label: 'Người chơi', to: '/admin/players', icon: UserRound },
      { label: 'Chủ sân', to: '/admin/owner', icon: Building2 },
      { label: 'Vai trò & quyền', to: '/admin/permissions', icon: KeyRound },
    ],
  },
  {
    label: 'Kiểm duyệt',
    items: [
      { label: 'Duyệt sân', to: '/admin/venues', icon: ClipboardCheck },
      { label: 'Báo cáo người dùng', to: '/admin/user-reports', icon: Flag },
      { label: 'Báo cáo đánh giá', to: '/admin/review-reports', icon: MessageSquareWarning },
    ],
  },
  {
    label: 'Tài chính',
    items: [
      { label: 'Phí nền tảng', to: '/admin/fees', icon: Coins },
      { label: 'Doanh thu phí', to: '/admin/fee-revenue', icon: HandCoins },
    ],
  },
]

export const adminNavigation = adminNavigationGroups.flatMap((group) => group.items)
