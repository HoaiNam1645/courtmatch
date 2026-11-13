import { Building2, ClipboardList, LayoutDashboard, UserRound } from 'lucide-react'

/** Mục điều hướng của khu quản lý chủ sân. Thêm mục khi chức năng tương ứng hoàn thành. */
export const ownerNavigation = [
  { label: 'Tổng quan', to: '/owner', icon: LayoutDashboard, end: true },
  { label: 'Hồ sơ', to: '/owner/profile', icon: UserRound },
  { label: 'Sân của tôi', to: '/owner/venues', icon: Building2 },
  { label: 'Yêu cầu đặt sân', to: '/owner/booking-requests', icon: ClipboardList },
]
