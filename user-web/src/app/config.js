import { Home, List, Map, UserRound, Users } from 'lucide-react'

export const DEFAULT_USER_LOCATION = { lat: 16.0544, lng: 108.2022 }
export const MAX_PRICE_FILTER = 200000
export const HOME_PAGE_SIZE = 24
export const MAP_PAGE_SIZE = 100

export const DEFAULT_VENUE_FILTERS = {
  district: 'all',
  maxDistance: 15,
  maxPrice: MAX_PRICE_FILTER,
  availableOnly: false,
  highRatingOnly: false,
}

export const quickFilters = ['Gần tôi', 'Còn sân hôm nay', 'Đánh giá cao', 'Giá tốt']

export const fallbackSlides = [
  {
    id: 'fallback-slide',
    eyebrow: 'Gợi ý hôm nay',
    title: 'Giữ lịch 18:00 ở sân gần bạn',
    subtitle: '42 sân còn khung giờ đẹp, cập nhật theo khu vực bạn chọn.',
    cta: 'Xem lịch trống',
    image: 'https://images.unsplash.com/photo-1626224583764-f87db24ac4ea?auto=format&fit=crop&w=1600&q=85',
  },
]

export const navigationTabs = [
  { label: 'Trang chủ', icon: Home, href: '/' },
  { label: 'Bản đồ', icon: Map, href: '/map' },
  // Danh sách sân nằm ở trang chủ (mục #venues); /explore không có route nên trước đây bị đưa về trang chủ.
  { label: 'Khám phá', icon: List, href: '/#venues', raised: true },
  { label: 'Đối tác', icon: Users, href: '/partners' },
  { label: 'Tài khoản', icon: UserRound, href: '/login' },
]

/** Mục điều hướng đang mở theo đường dẫn, dùng cho thanh dưới (điện thoại) và thanh trên (máy tính). */
export function activeTabFor(pathname, hash = '') {
  if (pathname === '/') return hash === '#venues' ? 'Khám phá' : 'Trang chủ'
  if (pathname === '/map' || pathname === '/ban-do') return 'Bản đồ'
  if (/^\/(san|venue)\//.test(pathname) || pathname.startsWith('/booking/checkout')) return 'Khám phá'
  if (pathname.startsWith('/partners')) return 'Đối tác'
  return 'Tài khoản'
}
