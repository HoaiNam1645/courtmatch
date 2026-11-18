import { Banknote, Building2, ClipboardList, FileWarning, History, LayoutDashboard, MessageSquareText, UserRound, Wallet } from 'lucide-react'

/** Mục điều hướng của khu quản lý chủ sân. Thêm mục khi chức năng tương ứng hoàn thành. */
export const ownerNavigation = [
  { label: 'Tổng quan', to: '/owner', icon: LayoutDashboard, end: true },
  { label: 'Hồ sơ', to: '/owner/profile', icon: UserRound },
  { label: 'Sân của tôi', to: '/owner/venues', icon: Building2 },
  { label: 'Yêu cầu đặt sân', to: '/owner/booking-requests', icon: ClipboardList },
  { label: 'Lịch sử đặt sân', to: '/owner/bookings', icon: History },
  { label: 'Đánh giá của khách', to: '/owner/reviews', icon: MessageSquareText },
  { label: 'Báo cáo của khách', to: '/owner/disputes', icon: FileWarning, badge: 'disputes' },
  { label: 'Thanh toán', to: '/owner/payments', icon: Wallet },
  { label: 'Tài chính', to: '/owner/finance', icon: Banknote },
]
