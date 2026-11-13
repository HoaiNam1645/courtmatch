import { Banknote, Building2, ClipboardList, History, LayoutDashboard, UserRound, Wallet } from 'lucide-react'

/** Mục điều hướng của khu quản lý chủ sân. Thêm mục khi chức năng tương ứng hoàn thành. */
export const ownerNavigation = [
  { label: 'Tổng quan', to: '/owner', icon: LayoutDashboard, end: true },
  { label: 'Hồ sơ', to: '/owner/profile', icon: UserRound },
  { label: 'Sân của tôi', to: '/owner/venues', icon: Building2 },
  { label: 'Yêu cầu đặt sân', to: '/owner/booking-requests', icon: ClipboardList },
  { label: 'Lịch sử đặt sân', to: '/owner/bookings', icon: History },
  { label: 'Thanh toán', to: '/owner/payments', icon: Wallet },
  { label: 'Tài chính', to: '/owner/finance', icon: Banknote },
]
