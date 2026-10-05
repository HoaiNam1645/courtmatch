import { useEffect, useMemo, useRef, useState } from 'react'
import 'leaflet/dist/leaflet.css'
import {
  Activity,
  ArrowLeft,
  Award,
  Bell,
  CalendarDays,
  Camera,
  Check,
  CheckCircle2,
  ClipboardCheck,
  ChevronRight,
  ChevronDown,
  Clock3,
  Compass,
  Copy,
  CircleX,
  CircleAlert,
  CreditCard,
  Eye,
  EyeOff,
  Edit3,
  Heart,
  History,
  Home,
  Layers,
  KeyRound,
  LockKeyhole,
  Flame,
  FileText,
  List,
  LogOut,
  LocateFixed,
  Landmark,
  Map,
  MapPinned,
  Mail,
  MailCheck,
  MapPin,
  Navigation,
  Phone,
  Plus,
  QrCode,
  ReceiptText,
  RefreshCw,
  Search,
  Save,
  Share2,
  ShieldCheck,
  Settings2,
  SlidersHorizontal,
  Star,
  Tag,
  Target,
  Trash2,
  Umbrella,
  Users,
  Zap,
  X,
  UserRound,
} from 'lucide-react'
import { ApiError, UNAUTHORIZED_EVENT, readToken } from './api/client'
import {
  confirmPasswordReset as confirmPasswordResetApi,
  fetchCurrentUser,
  resendVerification as resendVerificationApi,
  verifyEmail as verifyEmailApi,
  login as loginApi,
  logout as logoutApi,
  register as registerApi,
  requestPasswordReset as requestPasswordResetApi,
  verifyPasswordResetCode as verifyPasswordResetCodeApi,
} from './api/auth'
import { updatePreferences as updatePreferencesApi, updateProfile as updateProfileApi } from './api/profile'
import { useProfile } from './hooks/useProfile'
import { useVenueSearch } from './hooks/useVenueSearch'
import { useVenueDetail } from './hooks/useVenueDetail'
import { useAvailability } from './hooks/useAvailability'
import { useVenueReviews } from './hooks/useVenueReviews'
import { fetchQuote } from './api/venues'
import { cancelBooking as cancelBookingApi, createBooking as createBookingApi } from './api/bookings'
import { useBookings } from './hooks/useBookings'
import { useBookingDetail } from './hooks/useBookingDetail'
import { usePayment } from './hooks/usePayment'
import { retryPayment as retryPaymentApi } from './api/payments'
import { fetchDistricts } from './api/venues'
import { LocationPicker } from './components/LocationPicker'
import { VenueMap } from './components/VenueMap'

const DEFAULT_USER_LOCATION = { lat: 16.0544, lng: 108.2022 }
const CACHED_USER_KEY = 'courtly_cached_user'
const RECOVERY_STORAGE_KEY = 'courtly_password_recovery'
const PENDING_VERIFICATION_KEY = 'courtly_pending_verification'
const PENDING_BOOKING_STORAGE_KEY = 'courtly_pending_booking'
const LEGACY_STORAGE_KEYS = {
  auth: 'badcourt_fake_user',
  recovery: 'badcourt_password_recovery',
  profile: 'badcourt_fake_player_profile',
  pendingBooking: 'badcourt_pending_booking',
}

const tabs = [
  { label: 'Trang chủ', icon: Home, href: '/' },
  { label: 'Bản đồ', icon: Map, href: '/map' },
  { label: 'Khám phá', icon: List, href: '/explore', raised: true },
  { label: 'Nổi bật', icon: Compass, href: '/featured' },
  { label: 'Tài khoản', icon: UserRound, href: '/login' },
]

const quickFilters = ['Gần tôi', 'Còn sân hôm nay', 'Đánh giá cao', 'Giá tốt']

/** Gia toi da cua thanh truot; bang gia tri nay nghia la khong loc theo gia. */
const MAX_PRICE_FILTER = 200000

/** So san tai ve moi lan o trang chu. */
const HOME_PAGE_SIZE = 24

const DEFAULT_VENUE_FILTERS = {
  district: 'all',
  maxDistance: 15,
  maxPrice: 200000,
  availableOnly: false,
  highRatingOnly: false,
}



function searchMatches(item, value) {
  const keyword = value.trim().toLowerCase()
  if (!keyword) return true
  return [item.name, item.address].some((text) => text.toLowerCase().includes(keyword))
}

function classNames(...items) {
  return items.filter(Boolean).join(' ')
}

/**
 * Bản sao thông tin tài khoản để header hiển thị ngay khi mới mở trang.
 * Nguồn sự thật vẫn là GET /api/v1/auth/me, gọi ngay sau đó để ghi đè.
 */
function readCachedUser() {
  try {
    const savedUser = window.localStorage.getItem(CACHED_USER_KEY)
    return savedUser ? JSON.parse(savedUser) : null
  } catch {
    return null
  }
}

function saveCachedUser(user) {
  try {
    window.localStorage.setItem(CACHED_USER_KEY, JSON.stringify(user))
  } catch {
    // Chế độ riêng tư có thể chặn localStorage; header vẫn chạy được nhờ state.
  }
  window.dispatchEvent(new Event('courtly-auth-change'))
}

function clearCachedUser() {
  try {
    window.localStorage.removeItem(CACHED_USER_KEY)
    window.localStorage.removeItem(LEGACY_STORAGE_KEYS.auth)
    // Dọn nốt khoá của giai đoạn prototype.
    window.localStorage.removeItem('courtly_fake_user')
  } catch {
    // Bỏ qua, xem chú thích ở saveCachedUser.
  }
  window.dispatchEvent(new Event('courtly-auth-change'))
}

/** 2.1.4 - đăng xuất: gọi API rồi xoá token và bản sao ở client. */
async function signOut() {
  await logoutApi()
  clearCachedUser()
}



/**
 * Dữ liệu booking giả của giai đoạn prototype.
 *
 * Từ 2.1.28–2.1.32 các màn hình đặt sân đã dùng API thật; phần này **chỉ còn**
 * màn hình /payments dùng, sẽ bỏ khi làm 2.1.34.
 */
function readPendingBooking() {
  try {
    const value = window.sessionStorage.getItem(PENDING_BOOKING_STORAGE_KEY) ?? window.sessionStorage.getItem(LEGACY_STORAGE_KEYS.pendingBooking)
    return value ? JSON.parse(value) : null
  } catch {
    return null
  }
}

function saveRecoveryRequest(request) {
  window.sessionStorage.setItem(RECOVERY_STORAGE_KEY, JSON.stringify(request))
}

function readRecoveryRequest() {
  try {
    const savedRequest = window.sessionStorage.getItem(RECOVERY_STORAGE_KEY) ?? window.sessionStorage.getItem(LEGACY_STORAGE_KEYS.recovery)
    return savedRequest ? JSON.parse(savedRequest) : null
  } catch {
    return null
  }
}

/**
 * Trạng thái "đang chờ xác minh email" giữa trang /register và /register/verify.
 *
 * Dùng sessionStorage chứ không localStorage: đây là trạng thái của một lần đăng ký,
 * không nên sống qua phiên trình duyệt.
 */
function savePendingVerification(state) {
  try {
    window.sessionStorage.setItem(PENDING_VERIFICATION_KEY, JSON.stringify(state))
  } catch {
    // Chế độ riêng tư có thể chặn; trang xác minh sẽ hỏi lại email.
  }
}

function readPendingVerification() {
  try {
    const value = window.sessionStorage.getItem(PENDING_VERIFICATION_KEY)
    return value ? JSON.parse(value) : null
  } catch {
    return null
  }
}

function clearPendingVerification() {
  try {
    window.sessionStorage.removeItem(PENDING_VERIFICATION_KEY)
  } catch {
    // Không xoá được thì cũng không chặn người dùng đi tiếp.
  }
}

function clearRecoveryRequest() {
  try {
    window.sessionStorage.removeItem(RECOVERY_STORAGE_KEY)
    window.sessionStorage.removeItem(LEGACY_STORAGE_KEYS.recovery)
  } catch {
    // Không xoá được thì cũng không chặn người dùng đi tiếp.
  }
}

function maskRecoveryDestination(method, destination) {
  if (!destination) return method === 'phone' ? '09•• ••• ••88' : 'n•••@email.com'
  if (method === 'phone') {
    const digits = destination.replace(/\D/g, '')
    return digits.length > 4 ? `${digits.slice(0, 2)}•• ••• ••${digits.slice(-2)}` : destination
  }

  const [name, domain] = destination.split('@')
  if (!domain) return destination
  return `${name.slice(0, 1)}${'•'.repeat(Math.max(3, name.length - 1))}@${domain}`
}

function SplashScreen() {
  return (
    <div className="splash-screen">
      <div className="splash-orbit" aria-hidden="true">
        <span className="splash-float splash-shuttle splash-float-1">
          <span />
        </span>
        <span className="splash-float splash-racket splash-float-2" />
        <span className="splash-float splash-shuttle splash-float-3">
          <span />
        </span>
        <span className="splash-float splash-court splash-float-4" />
        <span className="splash-float splash-racket splash-float-5" />
        <span className="splash-float splash-shuttle splash-float-6">
          <span />
        </span>
      </div>
      <div className="splash-logo" aria-hidden="true">
        <img src="/logo.png" alt="" />
      </div>
      <p className="text-[32px] font-bold tracking-[0.2em] text-white max-sm:text-[24px]">
        COURTLY
      </p>
      <p className="text-sm font-semibold uppercase tracking-[0.18em] text-emerald-100/75">
        Đặt lịch sân cầu lông
      </p>
      <div className="flex gap-2 pt-3" aria-hidden="true">
        <span className="splash-dot" />
        <span className="splash-dot animation-delay-150" />
        <span className="splash-dot animation-delay-300" />
      </div>
    </div>
  )
}

function BrandMark({ compact = false, mini = false }) {
  return (
    <div
      className={classNames(
        'grid shrink-0 place-items-center overflow-hidden rounded-lg shadow-[0_10px_24px_rgba(8,112,64,0.18)]',
        mini ? 'size-9' : compact ? 'size-11' : 'size-16',
      )}
    >
      <img className="size-full scale-[1.34] object-contain" src="/logo.png" alt="Courtly" />
    </div>
  )
}

const fallbackSlides = [
  {
    id: 'fallback-slide',
    eyebrow: 'Gợi ý hôm nay',
    title: 'Giữ lịch 18:00 ở sân gần bạn',
    subtitle: '42 sân còn khung giờ đẹp, cập nhật theo khu vực bạn chọn.',
    cta: 'Xem lịch trống',
    image: 'https://images.unsplash.com/photo-1626224583764-f87db24ac4ea?auto=format&fit=crop&w=1600&q=85',
  },
]

function HeroSlides({ slides = fallbackSlides }) {
  const [activeIndex, setActiveIndex] = useState(0)
  const safeIndex = slides.length ? activeIndex % slides.length : 0
  const activeSlide = slides[safeIndex] ?? slides[0] ?? fallbackSlides[0]

  useEffect(() => {
    if (slides.length <= 1) return undefined
    const timer = window.setInterval(() => {
      setActiveIndex((current) => (current + 1) % slides.length)
    }, 4200)
    return () => window.clearInterval(timer)
  }, [slides.length])

  return (
    <section className="relative min-h-[470px] overflow-hidden px-4 pb-24 pt-14 md:px-6 lg:px-8 lg:pt-16">
      <img
        className="absolute inset-0 h-full w-full object-cover"
        src={activeSlide.image}
        alt={activeSlide.title}
      />
      <div className="absolute inset-0 bg-[linear-gradient(90deg,rgba(0,75,48,0.92)_0%,rgba(0,97,63,0.62)_38%,rgba(0,50,41,0.48)_100%)]" />
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_75%_28%,rgba(158,255,111,0.10),transparent_26%),linear-gradient(130deg,rgba(16,185,129,0.20)_0_18%,transparent_18%_50%,rgba(16,185,129,0.13)_50%_66%,transparent_66%)]" />
      <div className="absolute bottom-0 right-0 h-32 w-[42vw] bg-emerald-400/28 [clip-path:polygon(100%_0,0_100%,100%_100%)]" />

      <div className="relative z-10 mx-auto max-w-[1680px]">
        <div className="max-w-[620px] pt-6">
          <span className="inline-flex items-center gap-2 rounded-lg bg-white/16 px-4 py-2 text-xs font-semibold uppercase tracking-[0.08em] text-lime-100 ring-1 ring-white/14 backdrop-blur">
            <Zap className="size-4 fill-lime-300 text-lime-300" />
            Đặt sân nhanh
          </span>
          <h1 className="mt-5 text-balance text-[36px] font-semibold leading-[1.07] tracking-normal text-white md:text-[50px] lg:text-[58px]">
            So giá và chọn sân
            <span className="block text-lime-300">trong một màn</span>
          </h1>
          <p className="mt-5 text-lg font-medium leading-8 text-emerald-50/82 md:text-2xl">
            Lọc theo khoảng cách, giá và sân còn trống.
          </p>

          <div className="mt-8 flex flex-wrap items-center gap-5">
            <button className="inline-flex h-16 items-center gap-3 rounded-lg bg-lime-300 px-8 text-base font-semibold text-emerald-950 shadow-[0_18px_32px_rgba(163,230,53,0.26)]">
              <CalendarDays className="size-5" />
              Tìm sân phù hợp
            </button>
            <div className="flex items-center gap-2">
              {slides.map((slide, index) => (
                <button
                  key={slide.id}
                  className={classNames(
                    'size-3 rounded-full transition-all',
                    index === safeIndex ? 'w-7 bg-lime-300' : 'bg-white/35',
                  )}
                  onClick={() => setActiveIndex(index)}
                  aria-label={`Chuyển sang slide ${index + 1}`}
                />
              ))}
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}

function Header({ slides, user, onLogout }) {
  return (
    <div className="relative overflow-hidden bg-emerald-950 text-white">
      <AppHeader user={user} onLogout={onLogout} />
      <HeroSlides slides={slides} />
    </div>
  )
}

function AppHeader({ user, onLogout }) {
  return (
    <header className="sticky top-0 z-40 border-b border-white/10 bg-emerald-950 px-4 py-2.5 text-white shadow-[0_8px_22px_rgba(4,47,32,0.14)] md:px-6 lg:px-8">
      <div className="mx-auto flex max-w-[1680px] items-center justify-between gap-5">
        <a className="flex items-center gap-3 text-white" href="/">
          <BrandMark mini />
          <span className="text-[23px] font-semibold tracking-normal">Courtly</span>
        </a>

        {user ? (
          <div className="flex items-center gap-2">
            <button className="hidden size-9 place-items-center rounded-lg text-white/90 hover:bg-white/8 sm:grid">
              <Bell className="size-[18px]" />
            </button>
            <a
              href="/profile"
              className="flex h-10 items-center gap-2.5 rounded-lg border border-white/22 bg-white/8 px-3 text-left text-white shadow-sm backdrop-blur"
            >
              <span className="grid size-7 place-items-center rounded-md bg-lime-300 text-xs font-semibold text-emerald-950">
                {user.name.slice(0, 1)}
              </span>
              <span className="hidden leading-tight sm:grid">
                <span className="text-[13px] font-semibold">{user.name}</span>
                <span className="text-[11px] font-medium text-lime-200">Người chơi</span>
              </span>
            </a>
            <button
              className="grid size-10 place-items-center rounded-lg border border-white/22 text-white hover:bg-white/8"
              onClick={onLogout}
              aria-label="Đăng xuất"
              title="Đăng xuất"
            >
              <LogOut className="size-[18px]" />
            </button>
          </div>
        ) : (
          <div className="flex items-center gap-3">
            <button className="hidden size-9 place-items-center rounded-lg text-white/90 hover:bg-white/8 sm:grid">
              <Bell className="size-[18px]" />
            </button>
            <a className="grid h-10 place-items-center rounded-lg border border-white/22 bg-white/7 px-5 text-sm font-semibold text-white backdrop-blur" href="/login">
              Đăng nhập
            </a>
            <a className="hidden h-10 place-items-center rounded-lg bg-lime-300 px-5 text-sm font-semibold text-emerald-950 shadow-[0_10px_22px_rgba(163,230,53,0.2)] sm:grid" href="/register">
              Đăng kí
            </a>
          </div>
        )}
      </div>
    </header>
  )
}

function GoogleMark() {
  return (
    <span className="relative grid size-5 place-items-center rounded-full bg-white text-[15px] font-bold">
      <span className="text-[#4285f4]">G</span>
    </span>
  )
}

function FieldError({ message }) {
  if (!message) return null
  return <span className="mt-2 block text-sm font-medium text-red-600">{message}</span>
}

/** Bọc FormAlert với khoảng cách sẵn, dùng trong các khối dọc. */
function formatError(message) {
  if (!message) return null
  return <div className="mt-5"><FormAlert message={message} /></div>
}

function FormAlert({ message }) {
  if (!message) return null
  return (
    <div role="alert" className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
      {message}
    </div>
  )
}

function LoginPage() {
  const [method, setMethod] = useState('phone')
  const [showPassword, setShowPassword] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})

  async function handleLogin(event) {
    event.preventDefault()
    if (submitting) return

    const formData = new FormData(event.currentTarget)
    const emailOrPhone = (method === 'phone'
      ? formData.get('courtly_login_phone')
      : formData.get('courtly_login_email'))?.toString().trim() ?? ''
    const password = formData.get('courtly_login_password')?.toString() ?? ''

    // Kiểm tra phía client chỉ để báo sớm; backend vẫn kiểm tra lại toàn bộ.
    const nextFieldErrors = {}
    if (!emailOrPhone) {
      nextFieldErrors.emailOrPhone = method === 'phone' ? 'Vui lòng nhập số điện thoại' : 'Vui lòng nhập email'
    }
    if (!password) nextFieldErrors.password = 'Vui lòng nhập mật khẩu'
    if (Object.keys(nextFieldErrors).length > 0) {
      setFieldErrors(nextFieldErrors)
      setFormError('')
      return
    }

    setSubmitting(true)
    setFormError('')
    setFieldErrors({})

    try {
      const user = await loginApi({ emailOrPhone, password })
      saveCachedUser(user)
      window.location.href = '/'
    } catch (error) {
      if (error instanceof ApiError) {
        // Tài khoản đã đăng ký nhưng chưa xác minh email: đưa thẳng sang màn hình
        // nhập mã thay vì báo lỗi rồi bỏ mặc người dùng không biết làm gì tiếp.
        if (error.code === 'EMAIL_NOT_VERIFIED') {
          savePendingVerification({ email: emailOrPhone, sentAt: 0, resendAfterSeconds: 0 })
          window.location.href = '/register/verify'
          return
        }
        setFieldErrors(error.fieldErrors ?? {})
        setFormError(error.message)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
      setSubmitting(false)
    }
  }

  return (
    <main className="login-bg relative min-h-screen overflow-hidden px-4 pb-8 pt-6 text-emerald-950 sm:px-6">
      <header className="relative z-10 flex items-center justify-center">
        <a
          href="/"
          className="absolute left-0 top-0 grid size-8 place-items-center text-white sm:left-5"
          aria-label="Quay lại trang chủ"
        >
          <ArrowLeft className="size-6 stroke-[3]" />
        </a>
        <h1 className="text-[20px] font-semibold leading-8 text-white">Đăng nhập</h1>
      </header>

      <section className="relative z-10 mx-auto flex min-h-[calc(100svh-64px)] max-w-[600px] flex-col items-center justify-start pt-[96px]">
        <div className="w-full">
          <div className="overflow-hidden rounded-md bg-white shadow-[0_18px_44px_rgba(4,72,42,0.24)]">
            <div className="login-tabs grid h-[58px] grid-cols-2 bg-[#f4f4f4] text-[18px] font-semibold">
              {[
                { id: 'phone', label: 'Số điện thoại' },
                { id: 'email', label: 'Email' },
              ].map((item) => {
                const active = method === item.id
                return (
                  <button
                    key={item.id}
                    className={classNames(
                      'login-tab relative flex items-center justify-center transition',
                      active && 'login-tab-active z-10 bg-white text-[#0c5130]',
                      !active && 'login-tab-muted text-stone-400',
                      !active && item.id === 'phone' && 'login-tab-muted-phone',
                      !active && item.id === 'email' && 'login-tab-muted-email',
                    )}
                    onClick={() => setMethod(item.id)}
                  >
                    <span className="relative z-10">{item.label}</span>
                  </button>
                )
              })}
            </div>

            <form className="grid gap-6 px-6 pb-8 pt-9 sm:px-7" autoComplete="off" onSubmit={handleLogin}>
              {method === 'phone' ? (
                <label className="block">
                  <span className="text-[18px] font-semibold text-[#0c5130]">Số điện thoại của bạn?</span>
                  <div className="mt-3 flex h-[48px] overflow-hidden rounded-md border border-stone-300 bg-white focus-within:border-emerald-700">
                    <span className="flex w-[104px] items-center justify-center gap-2.5 text-sm font-medium text-[#0c5130]">
                      <span className="grid size-5 place-items-center rounded-full bg-[#e21a2c] text-[11px] text-yellow-300">
                        ★
                      </span>
                      <span>+ 84</span>
                      <ChevronDown className="size-4 text-stone-400" />
                    </span>
                    <input
                      className="min-w-0 flex-1 px-3 text-base font-normal text-stone-800 outline-none placeholder:text-stone-400"
                      placeholder="Nhập số điện thoại"
                      inputMode="tel"
                      name="courtly_login_phone"
                      type="tel"
                      autoComplete="off"
                    />
                  </div>
                  <FieldError message={fieldErrors.emailOrPhone} />
                </label>
              ) : (
                <label className="block">
                  <span className="text-[18px] font-semibold text-[#0c5130]">Email của bạn? (*)</span>
                  <div className="mt-3 flex h-[48px] items-center gap-3 rounded-md border border-stone-300 bg-white px-4 focus-within:border-emerald-700">
                    <Mail className="size-4 text-[#0c5130]" />
                    <input
                      className="min-w-0 flex-1 text-base font-normal text-stone-800 outline-none placeholder:text-stone-400"
                      placeholder="name@email.com"
                      name="courtly_login_email"
                      type="email"
                      autoComplete="off"
                    />
                  </div>
                  <FieldError message={fieldErrors.emailOrPhone} />
                </label>
              )}

              <label className="block">
                <span className="text-[18px] font-semibold text-[#0c5130]">Mật khẩu (*)</span>
                <div className="mt-3 flex h-[50px] items-center gap-3 rounded-md border border-stone-300 bg-white px-4 focus-within:border-emerald-700">
                  <input
                    className="min-w-0 flex-1 text-base font-normal text-stone-800 outline-none placeholder:text-stone-400"
                    placeholder="Nhập mật khẩu (*)"
                    name="courtly_login_password"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="new-password"
                  />
                  <button
                    type="button"
                    className="grid size-9 place-items-center text-[#0b7b47]"
                    onClick={() => setShowPassword((current) => !current)}
                    aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                  >
                    {showPassword ? <EyeOff className="size-5" /> : <Eye className="size-5" />}
                  </button>
                </div>
                <FieldError message={fieldErrors.password} />
              </label>

              <FormAlert message={formError} />

              <button
                type="submit"
                disabled={submitting}
                className="mt-1 h-[50px] rounded-md bg-[#04793f] text-base font-semibold uppercase tracking-normal text-white hover:bg-[#046d39] disabled:cursor-not-allowed disabled:opacity-60"
              >
                {submitting ? 'Đang đăng nhập...' : 'Đăng nhập'}
              </button>

              <div className="text-center text-sm font-normal text-[#0c5130]">
                <span>Bạn quên mật khẩu? </span>
                <a className="font-semibold underline underline-offset-2" href="/forgot-password">
                  Quên mật khẩu
                </a>
              </div>
            </form>
          </div>

          <p className="mt-7 text-center text-base font-normal text-white">
            Bạn chưa có tài khoản?{' '}
            <a className="font-semibold" href="/register">
              Đăng ký
            </a>
          </p>

          <div className="mt-6 grid gap-6">
            {/* 2.1.3 chưa triển khai. Vô hiệu hoá thay vì đăng nhập giả. */}
            <button
              type="button"
              disabled
              title="Chức năng đang được phát triển"
              className="flex h-[44px] cursor-not-allowed items-center justify-center gap-3 rounded-md bg-white text-[17px] font-normal text-[#23543a] opacity-60 shadow-[0_8px_22px_rgba(4,72,42,0.12)]"
            >
              <GoogleMark />
              Đăng nhập với Google
            </button>

            <a
              className="login-owner-banner flex min-h-[76px] items-center justify-center rounded-md border border-[#e4b02d] bg-white px-5 text-center text-base leading-7 text-[#e3a528] underline underline-offset-4"
              href="/owner-app"
            >
              Nếu bạn là CHỦ SÂN hoặc NHÂN VIÊN, Bấm vào đây để tải ứng dụng
              <br />
              Courtly - Quản lý sân cầu lông!
            </a>
          </div>
        </div>
      </section>
    </main>
  )
}

function RegisterPage() {
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})

  async function handleRegister(event) {
    event.preventDefault()
    if (submitting) return

    const formData = new FormData(event.currentTarget)
    const payload = {
      fullName: formData.get('courtly_register_name')?.toString().trim() ?? '',
      email: formData.get('courtly_register_email')?.toString().trim() ?? '',
      phone: formData.get('courtly_register_phone')?.toString().trim() ?? '',
      password: formData.get('courtly_register_password')?.toString() ?? '',
      confirmPassword: formData.get('courtly_register_confirm_password')?.toString() ?? '',
    }

    // Các điều kiện dưới đây phải khớp đúng với Bean Validation ở RegisterRequest.
    const nextFieldErrors = {}
    if (payload.fullName.length < 2) {
      nextFieldErrors.fullName = 'Họ và tên từ 2 đến 150 ký tự'
    }
    // Email bắt buộc từ khi có xác minh email: không có email thì không gửi được mã.
    if (!payload.email) {
      nextFieldErrors.email = 'Vui lòng nhập email'
    } else if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(payload.email)) {
      nextFieldErrors.email = 'Email không đúng định dạng'
    }
    if (payload.password.length < 8) {
      nextFieldErrors.password = 'Mật khẩu từ 8 đến 72 ký tự'
    } else if (!/^(?=.*[A-Za-z])(?=.*\d).+$/.test(payload.password)) {
      nextFieldErrors.password = 'Mật khẩu phải có ít nhất một chữ cái và một chữ số'
    }
    if (payload.password !== payload.confirmPassword) {
      nextFieldErrors.passwordConfirmed = 'Mật khẩu nhập lại không khớp'
    }
    if (Object.keys(nextFieldErrors).length > 0) {
      setFieldErrors(nextFieldErrors)
      setFormError('')
      return
    }

    setSubmitting(true)
    setFormError('')
    setFieldErrors({})

    try {
      const result = await registerApi(payload)
      // Đăng ký chưa cấp token: tài khoản chờ xác minh email.
      savePendingVerification({
        email: result.email,
        sentAt: Date.now(),
        resendAfterSeconds: result.resendAfterSeconds,
        codeExpiresAt: Date.now() + result.expiresInSeconds * 1000,
      })
      window.location.href = '/register/verify'
    } catch (error) {
      if (error instanceof ApiError) {
        setFieldErrors(error.fieldErrors ?? {})
        setFormError(error.message)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
      setSubmitting(false)
    }
  }

  return (
    <main className="login-bg relative min-h-screen overflow-hidden px-4 pb-8 pt-6 text-emerald-950 sm:px-6">
      <header className="relative z-10 flex items-center justify-center">
        <a
          href="/login"
          className="absolute left-0 top-0 grid size-8 place-items-center text-white sm:left-5"
          aria-label="Quay lại đăng nhập"
        >
          <ArrowLeft className="size-6 stroke-[3]" />
        </a>
        <h1 className="text-[20px] font-semibold leading-8 text-white">Đăng ký</h1>
      </header>

      <section className="relative z-10 mx-auto flex min-h-[calc(100svh-64px)] max-w-[560px] flex-col items-center justify-start pt-[88px]">
        <div className="w-full rounded-md bg-white px-6 pb-9 pt-8 shadow-[0_18px_44px_rgba(4,72,42,0.24)] sm:px-7">
          <form className="grid gap-5" autoComplete="off" onSubmit={handleRegister}>
            <label className="block">
              <span className="text-[18px] font-semibold text-[#0c5130]">Số điện thoại của bạn?</span>
              <div className="mt-3 flex h-[48px] overflow-hidden rounded-md border border-stone-300 bg-white focus-within:border-emerald-700">
                <span className="flex w-[104px] items-center justify-center gap-2.5 text-sm font-medium text-[#0c5130]">
                  <span className="grid size-5 place-items-center rounded-full bg-[#e21a2c] text-[11px] text-yellow-300">
                    ★
                  </span>
                  <span>+ 84</span>
                  <ChevronDown className="size-4 text-stone-400" />
                </span>
                <input
                  className="min-w-0 flex-1 px-3 text-base font-normal text-stone-800 outline-none placeholder:text-stone-400"
                  placeholder="Nhập số điện thoại"
                  inputMode="tel"
                  name="courtly_register_phone"
                  type="tel"
                  autoComplete="off"
                />
              </div>
            <FieldError message={fieldErrors.phone} />
            </label>

            <label className="block">
              <span className="text-[18px] font-semibold text-[#0c5130]">Email của bạn?</span>
              <div className="mt-3 flex h-[48px] items-center gap-3 rounded-md border border-stone-300 bg-white px-3 focus-within:border-emerald-700">
                <input
                  className="min-w-0 flex-1 text-base font-normal text-stone-800 outline-none placeholder:text-stone-400"
                  placeholder="Nhập email của bạn"
                  name="courtly_register_email"
                  type="email"
                  autoComplete="off"
                />
                <CircleX className="size-5 shrink-0 fill-[#08793f] text-white" />
              </div>
            <FieldError message={fieldErrors.email} />
            </label>

            <label className="block">
              <span className="text-[18px] font-semibold text-[#0c5130]">Tên đầy đủ (*)</span>
              <div className="mt-3 flex h-[48px] items-center gap-3 rounded-md border border-stone-300 bg-white px-3 focus-within:border-emerald-700">
                <input
                  className="min-w-0 flex-1 text-base font-normal text-stone-800 outline-none placeholder:text-stone-400"
                  placeholder="Nhập họ và tên"
                  name="courtly_register_name"
                  type="text"
                  autoComplete="off"
                />
                <CircleX className="size-5 shrink-0 fill-[#08793f] text-white" />
              </div>
            <FieldError message={fieldErrors.fullName} />
            </label>

            <label className="block">
              <span className="text-[18px] font-semibold text-[#0c5130]">Mật khẩu (*)</span>
              <div className="mt-3 flex h-[48px] items-center gap-3 rounded-md border border-stone-300 bg-white px-3 focus-within:border-emerald-700">
                <input
                  className="min-w-0 flex-1 text-base font-normal text-stone-800 outline-none placeholder:text-stone-400"
                  placeholder="Nhập mật khẩu (*)"
                  name="courtly_register_password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="new-password"
                />
                <button
                  type="button"
                  className="grid size-9 shrink-0 place-items-center text-[#0b7b47]"
                  onClick={() => setShowPassword((current) => !current)}
                  aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                >
                  {showPassword ? <EyeOff className="size-5" /> : <Eye className="size-5" />}
                </button>
              </div>
            <FieldError message={fieldErrors.password} />
            </label>

            <label className="block">
              <span className="text-[18px] font-semibold text-[#0c5130]">Nhập mật khẩu</span>
              <div className="mt-3 flex h-[48px] items-center gap-3 rounded-md border border-stone-300 bg-white px-3 focus-within:border-emerald-700">
                <input
                  className="min-w-0 flex-1 text-base font-normal text-stone-800 outline-none placeholder:text-stone-400"
                  placeholder="Nhập lại mật khẩu"
                  name="courtly_register_confirm_password"
                  type={showConfirmPassword ? 'text' : 'password'}
                  autoComplete="new-password"
                />
                <button
                  type="button"
                  className="grid size-9 shrink-0 place-items-center text-[#0b7b47]"
                  onClick={() => setShowConfirmPassword((current) => !current)}
                  aria-label={showConfirmPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                >
                  {showConfirmPassword ? <EyeOff className="size-5" /> : <Eye className="size-5" />}
                </button>
              </div>
            <FieldError message={fieldErrors.passwordConfirmed} />
            </label>

            <FormAlert message={formError} />

            <button
              type="submit"
              disabled={submitting}
              className="mt-7 h-[50px] rounded-md bg-[#04793f] text-base font-semibold uppercase tracking-normal text-white hover:bg-[#046d39] disabled:cursor-not-allowed disabled:opacity-60"
            >
              {submitting ? 'Đang tạo tài khoản...' : 'Đăng ký'}
            </button>

            <p className="pt-3 text-center text-sm font-normal text-stone-600">
              Bạn đã có tài khoản?{' '}
              <a className="font-semibold text-[#04793f]" href="/login">
                Đăng nhập
              </a>
            </p>
          </form>
        </div>
      </section>
    </main>
  )
}

/**
 * Nhập mã xác minh email sau khi đăng ký (2.1.1).
 *
 * Tài khoản mới ở trạng thái chờ, chưa đăng nhập được. Nhập đúng mã thì backend kích hoạt
 * tài khoản và cấp token luôn, nên trang này kết thúc bằng việc vào thẳng trang chủ.
 */
function VerifyEmailPage() {
  const pending = readPendingVerification()
  const [code, setCode] = useState(Array(6).fill(''))
  const [secondsLeft, setSecondsLeft] = useState(
    () => secondsUntil((pending?.sentAt ?? 0) + (pending?.resendAfterSeconds ?? 0) * 1000),
  )
  const [submitting, setSubmitting] = useState(false)
  const [resending, setResending] = useState(false)
  const [formError, setFormError] = useState('')
  const [notice, setNotice] = useState('')
  const inputRefs = useRef([])

  useEffect(() => {
    if (secondsLeft <= 0) return undefined
    const timer = window.setInterval(() => setSecondsLeft((value) => Math.max(0, value - 1)), 1000)
    return () => window.clearInterval(timer)
  }, [secondsLeft])

  if (!pending?.email) {
    return (
      <main className="login-bg relative grid min-h-screen place-items-center px-4 text-emerald-950">
        <div className="w-full max-w-[460px] overflow-hidden rounded-xl bg-white p-8 text-center shadow-[0_18px_44px_rgba(4,72,42,0.24)]">
          <span className="mx-auto grid size-14 place-items-center rounded-full bg-emerald-50 text-emerald-700">
            <MailCheck className="size-7" />
          </span>
          <h1 className="mt-5 text-xl font-semibold tracking-tight">Chưa có yêu cầu nào</h1>
          <p className="mt-2 text-sm font-medium leading-6 text-stone-500">
            Bạn cần đăng ký tài khoản trước, hoặc đăng nhập để hệ thống gửi lại mã xác minh.
          </p>
          <a
            className="mt-6 flex h-12 items-center justify-center gap-2 rounded-lg bg-emerald-700 text-sm font-semibold text-white"
            href="/register"
          >
            Về trang đăng ký
            <ChevronRight className="size-4" />
          </a>
        </div>
      </main>
    )
  }

  const maskedEmail = maskRecoveryDestination('email', pending.email)

  function updateDigit(index, value) {
    const digit = value.replace(/\D/g, '').slice(-1)
    setCode((current) => current.map((item, itemIndex) => (itemIndex === index ? digit : item)))
    if (digit && index < 5) inputRefs.current[index + 1]?.focus()
  }

  function handlePaste(event) {
    const digits = event.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6).split('')
    if (!digits.length) return
    event.preventDefault()
    setCode(Array.from({ length: 6 }, (_, index) => digits[index] ?? ''))
    inputRefs.current[Math.min(digits.length, 6) - 1]?.focus()
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting) return
    const value = code.join('')
    if (value.length < 6) return

    setSubmitting(true)
    setFormError('')
    setNotice('')

    try {
      const verifiedUser = await verifyEmailApi({ email: pending.email, code: value })
      saveCachedUser(verifiedUser)
      clearPendingVerification()
      window.location.href = '/'
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : 'Đã có lỗi xảy ra, vui lòng thử lại.')
      setCode(Array(6).fill(''))
      inputRefs.current[0]?.focus()
      setSubmitting(false)
    }
  }

  async function handleResend() {
    if (resending || secondsLeft > 0) return
    setResending(true)
    setFormError('')
    setNotice('')

    try {
      const result = await resendVerificationApi({ email: pending.email })
      savePendingVerification({
        email: pending.email,
        sentAt: Date.now(),
        resendAfterSeconds: result.resendAfterSeconds,
        codeExpiresAt: Date.now() + result.expiresInSeconds * 1000,
      })
      setSecondsLeft(result.resendAfterSeconds)
      setCode(Array(6).fill(''))
      setNotice('Đã gửi lại mã xác minh. Vui lòng kiểm tra hộp thư.')
    } catch (error) {
      if (error instanceof ApiError) {
        setFormError(error.message)
        // Server nói còn phải đợi bao lâu; đếm ngược đúng số đó thay vì đoán lại từ đầu.
        const retryAfter = Number(error.fieldErrors?.retryAfterSeconds)
        if (Number.isFinite(retryAfter) && retryAfter > 0) setSecondsLeft(retryAfter)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
    } finally {
      setResending(false)
    }
  }

  return (
    <main className="login-bg relative min-h-screen overflow-hidden px-4 pb-10 pt-6 text-emerald-950 sm:px-6">
      <header className="relative z-10 mx-auto flex max-w-[620px] items-center justify-center">
        <a
          href="/register"
          className="absolute left-0 top-0 grid size-9 place-items-center rounded-full bg-white/10 text-white backdrop-blur sm:left-5"
          aria-label="Quay lại đăng ký"
        >
          <ArrowLeft className="size-5 stroke-[3]" />
        </a>
        <h1 className="text-[20px] font-semibold leading-9 text-white">Xác minh email</h1>
      </header>

      <section className="relative z-10 mx-auto flex min-h-[calc(100svh-72px)] max-w-[560px] flex-col justify-center py-10">
        <div className="overflow-hidden rounded-xl border border-white/20 bg-white shadow-[0_24px_60px_rgba(4,72,42,0.28)]">
          <div className="border-b border-emerald-100 bg-[linear-gradient(135deg,#effcf5_0%,#ffffff_68%)] px-6 py-6 sm:px-8">
            <span className="grid size-12 place-items-center rounded-lg bg-emerald-700 text-white shadow-[0_10px_24px_rgba(4,121,63,0.18)]">
              <MailCheck className="size-6" />
            </span>
            <p className="mt-5 text-xs font-bold uppercase tracking-[0.16em] text-emerald-600">
              Bước cuối · Kích hoạt tài khoản
            </p>
            <h2 className="mt-1.5 text-2xl font-semibold tracking-tight text-emerald-950">
              Nhập mã gồm 6 chữ số
            </h2>
            <p className="mt-2 max-w-lg text-sm font-medium leading-6 text-stone-500">
              Mã xác minh đã được gửi tới <span className="font-semibold text-emerald-800">{maskedEmail}</span>.
              Tài khoản chỉ đăng nhập được sau khi xác minh.
            </p>
          </div>

          <form className="px-6 py-7 sm:px-8" onSubmit={handleSubmit}>
            <div className="grid grid-cols-6 gap-2 sm:gap-3" onPaste={handlePaste}>
              {code.map((digit, index) => (
                <input
                  key={index}
                  ref={(element) => { inputRefs.current[index] = element }}
                  className="h-14 min-w-0 rounded-lg border border-stone-200 bg-stone-50 text-center text-xl font-bold text-emerald-950 outline-none transition focus:border-emerald-500 focus:bg-white focus:ring-2 focus:ring-emerald-200 sm:h-16 sm:text-2xl"
                  value={digit}
                  onChange={(event) => updateDigit(index, event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === 'Backspace' && !digit && index > 0) inputRefs.current[index - 1]?.focus()
                  }}
                  inputMode="numeric"
                  autoComplete={index === 0 ? 'one-time-code' : 'off'}
                  aria-label={`Chữ số ${index + 1}`}
                  maxLength={1}
                  autoFocus={index === 0}
                  disabled={submitting}
                />
              ))}
            </div>

            <div className="mt-5 flex flex-wrap items-center justify-between gap-3 text-sm">
              <span className="font-medium text-stone-500">
                {secondsLeft > 0 ? `Có thể gửi lại sau ${formatCountdown(secondsLeft)}` : 'Bạn chưa nhận được mã?'}
              </span>
              <button
                type="button"
                className="font-semibold text-emerald-700 disabled:cursor-not-allowed disabled:text-stone-300"
                disabled={secondsLeft > 0 || resending}
                onClick={handleResend}
              >
                {resending ? 'Đang gửi lại...' : 'Gửi lại mã'}
              </button>
            </div>

            {notice && (
              <div className="mt-4 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700">
                {notice}
              </div>
            )}
            {formatError(formError)}

            <button
              className="mt-7 flex h-13 w-full items-center justify-center gap-2 rounded-lg bg-emerald-700 text-sm font-semibold text-white shadow-[0_10px_24px_rgba(4,121,63,0.2)] disabled:cursor-not-allowed disabled:bg-stone-300 disabled:shadow-none"
              disabled={submitting || code.some((digit) => !digit)}
            >
              {submitting ? 'Đang xác minh...' : 'Kích hoạt tài khoản'}
              {!submitting && <ChevronRight className="size-4" />}
            </button>

            <a className="mt-4 flex justify-center text-sm font-semibold text-emerald-700" href="/register">
              Đăng ký bằng email khác
            </a>
          </form>
        </div>
      </section>
    </main>
  )
}

const recoverySteps = ['Tài khoản', 'Xác minh', 'Mật khẩu']

function RecoveryShell({ title, backHref, step, children }) {
  return (
    <main className="login-bg relative min-h-screen overflow-hidden px-4 pb-10 pt-6 text-emerald-950 sm:px-6">
      <header className="relative z-10 mx-auto flex max-w-[720px] items-center justify-center">
        <a
          href={backHref}
          className="absolute left-0 top-0 grid size-9 place-items-center rounded-full bg-white/10 text-white backdrop-blur sm:left-5"
          aria-label="Quay lại"
        >
          <ArrowLeft className="size-5 stroke-[3]" />
        </a>
        <h1 className="text-[20px] font-semibold leading-9 text-white">{title}</h1>
      </header>

      <section className="relative z-10 mx-auto flex min-h-[calc(100svh-72px)] max-w-[620px] flex-col justify-center py-12">
        <div className="mb-5 grid grid-cols-3 gap-2 px-2">
          {recoverySteps.map((label, index) => {
            const number = index + 1
            const complete = step > number
            const active = step === number
            return (
              <div key={label} className="text-center">
                <div className="flex items-center">
                  <span className={classNames('h-px flex-1', index === 0 ? 'bg-transparent' : complete || active ? 'bg-lime-300' : 'bg-white/25')} />
                  <span
                    className={classNames(
                      'grid size-9 shrink-0 place-items-center rounded-full text-sm font-bold ring-1',
                      complete && 'bg-lime-300 text-emerald-950 ring-lime-300',
                      active && 'bg-white text-emerald-800 ring-white',
                      !complete && !active && 'bg-emerald-900/30 text-white/60 ring-white/25',
                    )}
                  >
                    {complete ? <Check className="size-4" /> : number}
                  </span>
                  <span className={classNames('h-px flex-1', index === recoverySteps.length - 1 ? 'bg-transparent' : complete ? 'bg-lime-300' : 'bg-white/25')} />
                </div>
                <p className={classNames('mt-2 text-xs font-semibold', active || complete ? 'text-white' : 'text-white/55')}>
                  {label}
                </p>
              </div>
            )
          })}
        </div>

        <div className="overflow-hidden rounded-xl border border-white/20 bg-white shadow-[0_24px_60px_rgba(4,72,42,0.28)]">
          {children}
        </div>
      </section>
    </main>
  )
}

function RecoveryCardHeader({ icon: Icon, eyebrow, title, description }) {
  return (
    <div className="border-b border-emerald-100 bg-[linear-gradient(135deg,#effcf5_0%,#ffffff_68%)] px-6 py-6 sm:px-8">
      <span className="grid size-12 place-items-center rounded-lg bg-emerald-700 text-white shadow-[0_10px_24px_rgba(4,121,63,0.18)]">
        <Icon className="size-6" />
      </span>
      <p className="mt-5 text-xs font-bold uppercase tracking-[0.16em] text-emerald-600">{eyebrow}</p>
      <h2 className="mt-1.5 text-2xl font-semibold tracking-tight text-emerald-950">{title}</h2>
      <p className="mt-2 max-w-lg text-sm font-medium leading-6 text-stone-500">{description}</p>
    </div>
  )
}

/** Đếm ngược còn lại của một mốc thời gian đã lưu, tính lại theo đồng hồ thật. */
function secondsUntil(timestamp) {
  if (!timestamp) return 0
  return Math.max(0, Math.ceil((timestamp - Date.now()) / 1000))
}

function formatCountdown(totalSeconds) {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

/**
 * Hiện khi phiên khôi phục không còn dùng được: chưa xin mã, mã bị huỷ vì nhập sai
 * quá nhiều, hoặc token đặt mật khẩu đã hết hạn. Luôn đưa người dùng về bước 1.
 */
function RecoveryRestartCard({ step, title, description }) {
  return (
    <RecoveryShell title="Khôi phục tài khoản" backHref="/login" step={step}>
      <RecoveryCardHeader icon={KeyRound} eyebrow="Cần bắt đầu lại" title={title} description={description} />
      <div className="px-6 py-6 sm:px-8 sm:py-7">
        <a
          className="flex h-13 items-center justify-center gap-2 rounded-lg bg-emerald-700 text-sm font-semibold text-white shadow-[0_10px_24px_rgba(4,121,63,0.2)] hover:bg-emerald-800"
          href="/forgot-password"
        >
          Yêu cầu mã mới
          <ChevronRight className="size-4" />
        </a>
      </div>
    </RecoveryShell>
  )
}

function ForgotPasswordPage() {
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting) return

    const formData = new FormData(event.currentTarget)
    const destination = formData.get('courtly_forgot_email')?.toString().trim() ?? ''

    // Kiểm tra sớm cho đỡ một vòng gọi mạng; backend vẫn kiểm tra lại y hệt.
    if (!destination) {
      setFieldErrors({ destination: 'Vui lòng nhập email đã đăng ký' })
      setFormError('')
      return
    }

    setSubmitting(true)
    setFormError('')
    setFieldErrors({})

    try {
      const result = await requestPasswordResetApi({ channel: 'email', destination })
      saveRecoveryRequest({
        method: 'email',
        destination,
        sentAt: Date.now(),
        resendAfterSeconds: result.resendAfterSeconds,
        codeExpiresAt: Date.now() + result.expiresInSeconds * 1000,
      })
      window.location.href = '/forgot-password/verify'
    } catch (error) {
      if (error instanceof ApiError) {
        setFieldErrors(error.fieldErrors ?? {})
        setFormError(error.message)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
      setSubmitting(false)
    }
  }

  return (
    <RecoveryShell title="Khôi phục tài khoản" backHref="/login" step={1}>
      <RecoveryCardHeader
        icon={KeyRound}
        eyebrow="Bước 1 · Tìm tài khoản"
        title="Bạn quên mật khẩu?"
        description="Nhập email đã đăng ký để nhận mã xác minh. Vì lý do bảo mật, chúng tôi luôn hiển thị cùng một kết quả dù tài khoản có tồn tại hay không."
      />

      <form className="grid gap-6 px-6 py-6 sm:px-8 sm:py-7" autoComplete="off" onSubmit={handleSubmit}>
        <div>
          <p className="text-sm font-semibold text-emerald-950">Nhận mã xác minh qua</p>
          <div className="mt-3 grid gap-3 sm:grid-cols-2">
            <div className="flex h-14 items-center gap-3 rounded-lg border border-emerald-500 bg-emerald-50 px-4 text-sm font-semibold text-emerald-800 ring-1 ring-emerald-400">
              <span className="grid size-8 place-items-center rounded-md bg-emerald-700 text-white">
                <Mail className="size-4" />
              </span>
              Email
              <span className="ml-auto grid size-5 place-items-center rounded-full border-2 border-emerald-500">
                <span className="size-2.5 rounded-full bg-emerald-500" />
              </span>
            </div>

            {/* Gửi mã qua SMS chưa triển khai. Hiện rõ thay vì để người dùng chọn rồi mới báo lỗi. */}
            <div
              className="flex h-14 items-center gap-3 rounded-lg border border-stone-200 bg-stone-50 px-4 text-sm font-semibold text-stone-400"
              title="Chức năng đang được phát triển"
            >
              <span className="grid size-8 place-items-center rounded-md bg-stone-100 text-stone-400">
                <Phone className="size-4" />
              </span>
              Số điện thoại
              <span className="ml-auto rounded-full bg-stone-200 px-2 py-0.5 text-[11px] font-bold uppercase tracking-wide text-stone-500">
                Sắp có
              </span>
            </div>
          </div>
        </div>

        <label className="block">
          <span className="text-sm font-semibold text-emerald-950">Email đã đăng ký</span>
          <div className="mt-2.5 flex h-13 items-center gap-3 rounded-lg border border-stone-200 bg-white px-4 focus-within:border-emerald-500 focus-within:ring-1 focus-within:ring-emerald-400">
            <Mail className="size-5 text-emerald-700" />
            <input
              className="min-w-0 flex-1 text-base text-stone-800 outline-none placeholder:text-stone-400"
              placeholder="name@email.com"
              name="courtly_forgot_email"
              type="email"
              autoComplete="email"
              maxLength={255}
            />
          </div>
          <FieldError message={fieldErrors.destination} />
        </label>

        <div className="rounded-lg bg-amber-50 px-4 py-3 text-xs font-medium leading-5 text-amber-800 ring-1 ring-amber-100">
          Mã xác minh có hiệu lực trong 10 phút. Không chia sẻ mã này với bất kỳ ai.
        </div>

        <FormAlert message={formError} />

        <button
          type="submit"
          disabled={submitting}
          className="flex h-13 items-center justify-center gap-2 rounded-lg bg-emerald-700 text-sm font-semibold text-white shadow-[0_10px_24px_rgba(4,121,63,0.2)] hover:bg-emerald-800 disabled:cursor-not-allowed disabled:bg-stone-300 disabled:shadow-none"
        >
          {submitting ? 'Đang gửi mã...' : 'Gửi mã xác minh'}
          {!submitting && <ChevronRight className="size-4" />}
        </button>
      </form>
    </RecoveryShell>
  )
}

function VerifyRecoveryPage() {
  const request = readRecoveryRequest()
  const [code, setCode] = useState(Array(6).fill(''))
  const [secondsLeft, setSecondsLeft] = useState(() => secondsUntil((request?.sentAt ?? 0) + (request?.resendAfterSeconds ?? 0) * 1000))
  const [submitting, setSubmitting] = useState(false)
  const [resending, setResending] = useState(false)
  const [formError, setFormError] = useState('')
  const [notice, setNotice] = useState('')
  const [needsNewCode, setNeedsNewCode] = useState(false)
  const inputRefs = useRef([])

  useEffect(() => {
    if (secondsLeft <= 0) return undefined
    const timer = window.setInterval(() => setSecondsLeft((value) => Math.max(0, value - 1)), 1000)
    return () => window.clearInterval(timer)
  }, [secondsLeft])

  if (!request?.destination) {
    return (
      <RecoveryRestartCard
        step={1}
        title="Chưa có yêu cầu nào"
        description="Bạn cần nhập email và nhận mã xác minh trước khi tới bước này."
      />
    )
  }

  if (needsNewCode) {
    return (
      <RecoveryRestartCard
        step={2}
        title="Mã xác minh đã bị huỷ"
        description="Bạn đã nhập sai quá số lần cho phép. Vui lòng yêu cầu một mã xác minh mới."
      />
    )
  }

  const maskedDestination = maskRecoveryDestination(request.method, request.destination)

  function updateDigit(index, value) {
    const digit = value.replace(/\D/g, '').slice(-1)
    setCode((current) => current.map((item, itemIndex) => (itemIndex === index ? digit : item)))
    if (digit && index < 5) inputRefs.current[index + 1]?.focus()
  }

  function handlePaste(event) {
    const digits = event.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6).split('')
    if (!digits.length) return
    event.preventDefault()
    setCode(Array.from({ length: 6 }, (_, index) => digits[index] ?? ''))
    inputRefs.current[Math.min(digits.length, 6) - 1]?.focus()
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting) return
    const value = code.join('')
    if (value.length < 6) return

    setSubmitting(true)
    setFormError('')
    setNotice('')

    try {
      const result = await verifyPasswordResetCodeApi({ destination: request.destination, code: value })
      saveRecoveryRequest({
        ...request,
        resetToken: result.resetToken,
        tokenExpiresAt: Date.now() + result.expiresInSeconds * 1000,
      })
      window.location.href = '/reset-password'
    } catch (error) {
      if (error instanceof ApiError) {
        setFormError(error.message)
        // Hết lượt thử thì mã đã bị huỷ ở server, ở lại màn này cũng không nhập được nữa.
        if (error.code === 'RESET_TOO_MANY_ATTEMPTS') setNeedsNewCode(true)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
      setCode(Array(6).fill(''))
      inputRefs.current[0]?.focus()
      setSubmitting(false)
    }
  }

  async function handleResend() {
    if (resending || secondsLeft > 0) return
    setResending(true)
    setFormError('')
    setNotice('')

    try {
      const result = await requestPasswordResetApi({ channel: 'email', destination: request.destination })
      saveRecoveryRequest({
        method: 'email',
        destination: request.destination,
        sentAt: Date.now(),
        resendAfterSeconds: result.resendAfterSeconds,
        codeExpiresAt: Date.now() + result.expiresInSeconds * 1000,
      })
      setSecondsLeft(result.resendAfterSeconds)
      setCode(Array(6).fill(''))
      setNotice('Đã gửi lại mã xác minh. Vui lòng kiểm tra hộp thư.')
    } catch (error) {
      if (error instanceof ApiError) {
        setFormError(error.message)
        // Server nói còn phải đợi bao lâu; đếm ngược đúng số đó thay vì đoán lại từ đầu.
        const retryAfter = Number(error.fieldErrors?.retryAfterSeconds)
        if (Number.isFinite(retryAfter) && retryAfter > 0) setSecondsLeft(retryAfter)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
    } finally {
      setResending(false)
    }
  }

  return (
    <RecoveryShell title="Xác minh danh tính" backHref="/forgot-password" step={2}>
      <RecoveryCardHeader
        icon={ShieldCheck}
        eyebrow="Bước 2 · Xác minh"
        title="Nhập mã gồm 6 chữ số"
        description={`Nếu ${maskedDestination} là email đã đăng ký, mã xác minh vừa được gửi tới hộp thư đó.`}
      />

      <form className="px-6 py-7 sm:px-8" onSubmit={handleSubmit}>
        <div className="grid grid-cols-6 gap-2 sm:gap-3" onPaste={handlePaste}>
          {code.map((digit, index) => (
            <input
              key={index}
              ref={(element) => { inputRefs.current[index] = element }}
              className="h-14 min-w-0 rounded-lg border border-stone-200 bg-stone-50 text-center text-xl font-bold text-emerald-950 outline-none transition focus:border-emerald-500 focus:bg-white focus:ring-2 focus:ring-emerald-200 sm:h-16 sm:text-2xl"
              value={digit}
              onChange={(event) => updateDigit(index, event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Backspace' && !digit && index > 0) inputRefs.current[index - 1]?.focus()
              }}
              inputMode="numeric"
              autoComplete={index === 0 ? 'one-time-code' : 'off'}
              aria-label={`Chữ số ${index + 1}`}
              maxLength={1}
              autoFocus={index === 0}
              disabled={submitting}
            />
          ))}
        </div>

        <div className="mt-5 flex flex-wrap items-center justify-between gap-3 text-sm">
          <span className="font-medium text-stone-500">
            {secondsLeft > 0 ? `Có thể gửi lại sau ${formatCountdown(secondsLeft)}` : 'Bạn chưa nhận được mã?'}
          </span>
          <button
            type="button"
            className="font-semibold text-emerald-700 disabled:cursor-not-allowed disabled:text-stone-300"
            disabled={secondsLeft > 0 || resending}
            onClick={handleResend}
          >
            {resending ? 'Đang gửi lại...' : 'Gửi lại mã'}
          </button>
        </div>

        {notice && (
          <div className="mt-4 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700">
            {notice}
          </div>
        )}
        {formatError(formError)}

        <button
          className="mt-7 flex h-13 w-full items-center justify-center gap-2 rounded-lg bg-emerald-700 text-sm font-semibold text-white shadow-[0_10px_24px_rgba(4,121,63,0.2)] disabled:cursor-not-allowed disabled:bg-stone-300 disabled:shadow-none"
          disabled={submitting || code.some((digit) => !digit)}
        >
          {submitting ? 'Đang xác minh...' : 'Xác minh tài khoản'}
          {!submitting && <ChevronRight className="size-4" />}
        </button>

        <a className="mt-4 flex justify-center text-sm font-semibold text-emerald-700" href="/forgot-password">
          Đổi email khác
        </a>
      </form>
    </RecoveryShell>
  )
}

function ResetPasswordPage() {
  const request = readRecoveryRequest()
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmation, setShowConfirmation] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [sessionLost, setSessionLost] = useState(false)

  // Đúng bằng ràng buộc của backend ở PasswordResetConfirmRequest và RegisterRequest.
  const requirements = [
    { label: 'Từ 8 đến 72 ký tự', met: password.length >= 8 && password.length <= 72 },
    { label: 'Có ít nhất một chữ cái', met: /[A-Za-z]/.test(password) },
    { label: 'Có ít nhất một chữ số', met: /\d/.test(password) },
  ]
  const isValid = requirements.every((item) => item.met) && password === confirmation

  if (!request?.resetToken || sessionLost) {
    return (
      <RecoveryRestartCard
        step={2}
        title="Phiên đặt lại đã hết hạn"
        description="Vì lý do bảo mật, bước đặt mật khẩu mới chỉ mở trong ít phút sau khi xác minh. Vui lòng yêu cầu mã mới."
      />
    )
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting || !isValid) return

    setSubmitting(true)
    setFormError('')
    setFieldErrors({})

    try {
      await confirmPasswordResetApi({
        resetToken: request.resetToken,
        password,
        confirmPassword: confirmation,
      })
      clearRecoveryRequest()
      window.location.href = '/reset-password/success'
    } catch (error) {
      if (error instanceof ApiError) {
        setFieldErrors(error.fieldErrors ?? {})
        setFormError(error.message)
        if (error.code === 'RESET_TOKEN_INVALID') setSessionLost(true)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
      setSubmitting(false)
    }
  }

  return (
    <RecoveryShell title="Tạo mật khẩu mới" backHref="/forgot-password/verify" step={3}>
      <RecoveryCardHeader
        icon={LockKeyhole}
        eyebrow="Bước 3 · Mật khẩu"
        title="Bảo vệ tài khoản của bạn"
        description="Tạo mật khẩu mới khác với mật khẩu hiện tại và đủ mạnh để bảo vệ tài khoản."
      />

      <form className="grid gap-5 px-6 py-7 sm:px-8" onSubmit={handleSubmit}>
        <label>
          <span className="text-sm font-semibold text-emerald-950">Mật khẩu mới</span>
          <div className="mt-2.5 flex h-13 items-center gap-3 rounded-lg border border-stone-200 px-4 focus-within:border-emerald-500 focus-within:ring-1 focus-within:ring-emerald-400">
            <LockKeyhole className="size-5 text-emerald-700" />
            <input
              className="min-w-0 flex-1 text-base outline-none placeholder:text-stone-400"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              type={showPassword ? 'text' : 'password'}
              placeholder="Nhập mật khẩu mới"
              autoComplete="new-password"
              maxLength={72}
              required
            />
            <button type="button" className="text-emerald-700" onClick={() => setShowPassword((value) => !value)} aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}>
              {showPassword ? <EyeOff className="size-5" /> : <Eye className="size-5" />}
            </button>
          </div>
          <FieldError message={fieldErrors.password} />
        </label>

        <div className="grid gap-2 rounded-lg bg-stone-50 p-4 ring-1 ring-stone-100 sm:grid-cols-2">
          {requirements.map((item) => (
            <p key={item.label} className={classNames('flex items-center gap-2 text-xs font-semibold', item.met ? 'text-emerald-700' : 'text-stone-400')}>
              <span className={classNames('grid size-4 place-items-center rounded-full', item.met ? 'bg-emerald-600 text-white' : 'bg-stone-200 text-stone-400')}>
                <Check className="size-3" />
              </span>
              {item.label}
            </p>
          ))}
        </div>

        <label>
          <span className="text-sm font-semibold text-emerald-950">Xác nhận mật khẩu mới</span>
          <div className={classNames('mt-2.5 flex h-13 items-center gap-3 rounded-lg border px-4 focus-within:ring-1', confirmation && confirmation !== password ? 'border-red-300 focus-within:ring-red-200' : 'border-stone-200 focus-within:border-emerald-500 focus-within:ring-emerald-400')}>
            <LockKeyhole className="size-5 text-emerald-700" />
            <input
              className="min-w-0 flex-1 text-base outline-none placeholder:text-stone-400"
              value={confirmation}
              onChange={(event) => setConfirmation(event.target.value)}
              type={showConfirmation ? 'text' : 'password'}
              placeholder="Nhập lại mật khẩu mới"
              autoComplete="new-password"
              maxLength={72}
              required
            />
            <button type="button" className="text-emerald-700" onClick={() => setShowConfirmation((value) => !value)} aria-label={showConfirmation ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}>
              {showConfirmation ? <EyeOff className="size-5" /> : <Eye className="size-5" />}
            </button>
          </div>
          {confirmation && confirmation !== password && <span className="mt-2 block text-xs font-medium text-red-600">Mật khẩu xác nhận chưa trùng khớp.</span>}
        </label>

        <FormAlert message={formError} />

        <button
          className="mt-2 flex h-13 items-center justify-center gap-2 rounded-lg bg-emerald-700 text-sm font-semibold text-white shadow-[0_10px_24px_rgba(4,121,63,0.2)] disabled:cursor-not-allowed disabled:bg-stone-300 disabled:shadow-none"
          disabled={submitting || !isValid}
        >
          {submitting ? 'Đang cập nhật...' : 'Cập nhật mật khẩu'}
          {!submitting && <ShieldCheck className="size-4" />}
        </button>
      </form>
    </RecoveryShell>
  )
}

function ResetPasswordSuccessPage() {
  return (
    <RecoveryShell title="Hoàn tất" backHref="/login" step={4}>
      <div className="px-6 py-10 text-center sm:px-10 sm:py-12">
        <span className="mx-auto grid size-20 place-items-center rounded-full bg-emerald-50 text-emerald-600 ring-8 ring-emerald-50/60">
          <CheckCircle2 className="size-11" />
        </span>
        <p className="mt-7 text-xs font-bold uppercase tracking-[0.16em] text-emerald-600">Khôi phục thành công</p>
        <h2 className="mt-2 text-2xl font-semibold tracking-tight text-emerald-950">Mật khẩu đã được cập nhật</h2>
        <p className="mx-auto mt-3 max-w-md text-sm font-medium leading-6 text-stone-500">
          Bạn có thể dùng mật khẩu mới để đăng nhập và tiếp tục đặt sân cùng Courtly.
        </p>
        <a className="mt-8 flex h-13 w-full items-center justify-center gap-2 rounded-lg bg-emerald-700 text-sm font-semibold text-white shadow-[0_10px_24px_rgba(4,121,63,0.2)]" href="/login">
          Quay lại đăng nhập
          <ChevronRight className="size-4" />
        </a>
      </div>
    </RecoveryShell>
  )
}

const skillOptions = [
  { id: 'beginner', label: 'Mới chơi', description: 'Đang làm quen kỹ thuật và luật chơi' },
  { id: 'intermediate', label: 'Trung bình', description: 'Đánh ổn định, chơi phong trào thường xuyên' },
  { id: 'advanced', label: 'Nâng cao', description: 'Kỹ thuật tốt, có kinh nghiệm thi đấu' },
  { id: 'professional', label: 'Chuyên nghiệp', description: 'Tập luyện và thi đấu chuyên sâu' },
]

const playingStyleOptions = [
  { id: 'attacking', label: 'Tấn công', description: 'Ưu tiên tốc độ và những pha cầu chủ động' },
  { id: 'balanced', label: 'Cân bằng', description: 'Linh hoạt giữa tấn công và phòng thủ' },
  { id: 'defensive', label: 'Phòng thủ', description: 'Bền bỉ, điều cầu và chờ cơ hội phản công' },
]

const handOptions = [
  { id: 'right', label: 'Tay phải' },
  { id: 'left', label: 'Tay trái' },
  { id: 'both', label: 'Cả hai tay' },
]

const playTypeOptions = [
  { id: 'single', label: 'Đánh đơn', icon: UserRound },
  { id: 'double', label: 'Đánh đôi', icon: Users },
  { id: 'mixed', label: 'Đôi nam nữ', icon: Heart },
]

const weekDays = [
  { value: 1, short: 'T2', label: 'Thứ hai' },
  { value: 2, short: 'T3', label: 'Thứ ba' },
  { value: 3, short: 'T4', label: 'Thứ tư' },
  { value: 4, short: 'T5', label: 'Thứ năm' },
  { value: 5, short: 'T6', label: 'Thứ sáu' },
  { value: 6, short: 'T7', label: 'Thứ bảy' },
  { value: 7, short: 'CN', label: 'Chủ nhật' },
]

function getOptionLabel(options, value) {
  return options.find((item) => item.id === value)?.label ?? value
}

function ProfileSectionTitle({ icon: Icon, eyebrow, title, action, href }) {
  const actionClass = 'text-xs font-semibold text-emerald-700 hover:text-emerald-900'
  return (
    <div className="flex items-start justify-between gap-4">
      <div className="flex items-start gap-3">
        <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-emerald-50 text-emerald-700 ring-1 ring-emerald-100">
          <Icon className="size-5" />
        </span>
        <div>
          <p className="text-[11px] font-bold uppercase tracking-[0.15em] text-emerald-600">{eyebrow}</p>
          <h2 className="mt-1 text-lg font-semibold text-emerald-950">{title}</h2>
        </div>
      </div>
      {action && (href ? <a className={actionClass} href={href}>{action}</a> : <span className={actionClass}>{action}</span>)}
    </div>
  )
}

function PageBackLink({ href, label = 'Quay lại', tone = 'light' }) {
  return (
    <a
      href={href}
      className={classNames(
        'inline-flex h-10 items-center gap-2 rounded-lg px-3.5 text-sm font-semibold transition',
        tone === 'dark'
          ? 'border border-white/18 bg-white/10 text-white backdrop-blur hover:bg-white/16'
          : 'border border-emerald-100 bg-white text-emerald-800 shadow-sm hover:bg-emerald-50',
      )}
    >
      <ArrowLeft className="size-4" />
      {label}
    </a>
  )
}

function ProfileEmptyState() {
  return (
    <main className="grid min-h-screen place-items-center bg-[linear-gradient(145deg,#effaf4,#f8faf4)] px-4 text-center text-emerald-950">
      <div className="max-w-md rounded-xl border border-emerald-100 bg-white p-8 shadow-[0_20px_50px_rgba(25,70,48,0.12)]">
        <span className="mx-auto grid size-16 place-items-center rounded-full bg-emerald-50 text-emerald-700">
          <UserRound className="size-8" />
        </span>
        <h1 className="mt-5 text-2xl font-semibold">Đăng nhập để xem hồ sơ</h1>
        <p className="mt-2 text-sm font-medium leading-6 text-stone-500">Thông tin và thiết lập chơi của bạn sẽ được lưu trong tài khoản Courtly.</p>
        <a className="mt-6 flex h-12 items-center justify-center rounded-lg bg-emerald-700 text-sm font-semibold text-white" href="/login">Đăng nhập</a>
      </div>
    </main>
  )
}

function ProfileLoading({ user, onLogout }) {
  return (
    <main className="min-h-screen bg-[#f4f7f1] pb-24">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="mx-auto max-w-[1100px] px-4 py-14">
        <div className="grid gap-4">
          <div className="h-32 animate-pulse rounded-xl bg-emerald-100/70" />
          <div className="h-24 animate-pulse rounded-xl bg-emerald-100/50" />
          <div className="h-24 animate-pulse rounded-xl bg-emerald-100/40" />
        </div>
      </section>
    </main>
  )
}

function ProfileLoadError({ user, onLogout, message, onRetry }) {
  return (
    <main className="min-h-screen bg-[#f4f7f1] pb-24">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="mx-auto max-w-[680px] px-4 py-16 text-center">
        <h2 className="text-xl font-semibold text-emerald-950">Không tải được hồ sơ</h2>
        <p className="mt-3 text-sm font-medium text-stone-600">{message}</p>
        <button
          className="mt-6 h-11 rounded-lg bg-emerald-700 px-6 text-sm font-semibold text-white"
          onClick={onRetry}
        >
          Thử lại
        </button>
      </section>
    </main>
  )
}

function ProfilePage({ user, onLogout }) {
  const { status, data, error, reload } = useProfile()

  if (!user) return <ProfileEmptyState />
  if (status === 'loading') return <ProfileLoading user={user} onLogout={onLogout} />
  if (status === 'error') {
    return <ProfileLoadError user={user} onLogout={onLogout} message={error} onRetry={reload} />
  }

  const profile = data.profile

  const availabilityLabels = profile.availability.map((slot) => {
    const day = weekDays.find((item) => item.value === slot.day)?.short
    return `${day} · ${slot.startTime}–${slot.endTime}`
  })
  const statistics = profile.statistics
  const completion = getProfileCompletion(data.user, profile)
  const defaultLocation = profile.locations.find((location) => location.isDefault) ?? profile.locations[0]

  return (
    <main className="min-h-screen bg-[linear-gradient(145deg,#eef8f2_0%,#f8faf5_46%,#edf7f3_100%)] pb-24 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />

      <section className="relative overflow-hidden bg-emerald-950 px-4 pb-24 pt-10 text-white md:px-6 lg:px-8">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_78%_16%,rgba(190,242,100,0.18),transparent_24%),linear-gradient(128deg,rgba(16,185,129,0.18)_0_20%,transparent_20%_62%,rgba(52,211,153,0.12)_62%)]" />
        <div className="relative mx-auto mb-6 max-w-[1200px]">
          <PageBackLink href="/" label="Về trang chủ" tone="dark" />
        </div>
        <div className="relative mx-auto flex max-w-[1200px] flex-col gap-6 md:flex-row md:items-center md:justify-between">
          <div className="flex items-center gap-5">
            <div className="relative grid size-24 shrink-0 place-items-center rounded-2xl bg-lime-300 text-4xl font-semibold text-emerald-950 shadow-[0_18px_38px_rgba(163,230,53,0.18)]">
              {user.name.slice(0, 1).toUpperCase()}
              <span className="absolute -bottom-2 -right-2 grid size-8 place-items-center rounded-full border-4 border-emerald-950 bg-emerald-500 text-white">
                <Check className="size-4" />
              </span>
            </div>
            <div>
              <span className="inline-flex rounded-full bg-white/10 px-3 py-1 text-xs font-semibold text-lime-200 ring-1 ring-white/15">Hồ sơ người chơi</span>
              <h1 className="mt-3 text-3xl font-semibold tracking-tight md:text-4xl">{user.name}</h1>
              <p className="mt-2 text-sm font-medium text-emerald-100/75">Thành viên Courtly{defaultLocation?.address ? ` · ${defaultLocation.address}` : ''}</p>
            </div>
          </div>
          <div className="flex flex-wrap gap-3">
            <a className="flex h-11 items-center gap-2 rounded-lg border border-white/20 bg-white/8 px-5 text-sm font-semibold text-white backdrop-blur" href="/bookings">
              <History className="size-4" /> Lịch đặt sân
            </a>
            <a className="flex h-11 items-center gap-2 rounded-lg border border-white/20 bg-white/8 px-5 text-sm font-semibold text-white backdrop-blur" href="/profile/preferences">
              <SlidersHorizontal className="size-4" /> Thiết lập chơi
            </a>
            <a className="flex h-11 items-center gap-2 rounded-lg bg-lime-300 px-5 text-sm font-semibold text-emerald-950" href="/profile/edit">
              <Edit3 className="size-4" /> Chỉnh sửa hồ sơ
            </a>
          </div>
        </div>
      </section>

      <section className="relative mx-auto -mt-14 max-w-[1200px] px-4 md:px-6">
        <div className="grid overflow-hidden rounded-xl border border-emerald-100 bg-white shadow-[0_18px_46px_rgba(24,65,44,0.12)] sm:grid-cols-2 lg:grid-cols-4">
          {[
            { label: 'Trình độ', value: getOptionLabel(skillOptions, profile.skillLevel) || 'Chưa thiết lập', icon: Award },
            { label: 'Điểm kỹ năng', value: (profile.skillScore ?? statistics.ratingScore).toLocaleString('vi-VN'), icon: Activity },
            { label: 'Trận đã chơi', value: statistics.totalMatches.toLocaleString('vi-VN'), icon: Target },
            { label: 'Tỷ lệ thắng', value: `${statistics.winRate}%`, icon: Star },
          ].map((item, index) => {
            const Icon = item.icon
            return (
              <div key={item.label} className={classNames('flex items-center gap-3 p-5', index > 0 && 'border-t border-emerald-100 sm:border-l sm:border-t-0')}>
                <span className="grid size-11 place-items-center rounded-lg bg-emerald-50 text-emerald-700"><Icon className="size-5" /></span>
                <div><p className="text-xs font-medium text-stone-500">{item.label}</p><p className="mt-1 text-lg font-semibold text-emerald-950">{item.value}</p></div>
              </div>
            )
          })}
        </div>

        <div className="mt-6 grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
          <div className="grid gap-6">
            <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)]">
              <ProfileSectionTitle icon={UserRound} eyebrow="Giới thiệu" title="Thông tin người chơi" action="Chỉnh sửa" href="/profile/edit" />
              <p className="mt-5 text-sm font-medium leading-7 text-stone-600">{profile.bio || 'Chưa có giới thiệu. Bấm Chỉnh sửa để thêm.'}</p>
              <div className="mt-5 grid gap-3 sm:grid-cols-2">
                {[
                  ['Email', user.email],
                  ['Số điện thoại', user.phone],
                  ['Ngày sinh', profile.dateOfBirth ? new Date(profile.dateOfBirth).toLocaleDateString('vi-VN') : 'Chưa thiết lập'],
                  ['Tay thuận', getOptionLabel(handOptions, profile.dominantHand) || 'Chưa thiết lập'],
                ].map(([label, value]) => (
                  <div key={label} className="rounded-lg bg-stone-50 px-4 py-3 ring-1 ring-stone-100">
                    <p className="text-xs font-medium text-stone-400">{label}</p><p className="mt-1 text-sm font-semibold text-emerald-950">{value}</p>
                  </div>
                ))}
              </div>
            </section>

            <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)]">
              <ProfileSectionTitle icon={Target} eyebrow="Phong cách" title="Sở thích chơi" action="Thiết lập" href="/profile/preferences" />
              <div className="mt-5 grid gap-3 sm:grid-cols-3">
                {[
                  ['Trình độ', getOptionLabel(skillOptions, profile.skillLevel) || 'Chưa thiết lập'],
                  ['Phong cách', getOptionLabel(playingStyleOptions, profile.playingStyle) || 'Chưa thiết lập'],
                  ['Hình thức', getOptionLabel(playTypeOptions, profile.preferredPlayType) || 'Chưa thiết lập'],
                ].map(([label, value]) => (
                  <div key={label} className="rounded-lg border border-emerald-100 bg-emerald-50/60 p-4">
                    <p className="text-xs font-medium text-emerald-600">{label}</p><p className="mt-2 text-base font-semibold text-emerald-950">{value}</p>
                  </div>
                ))}
              </div>
            </section>

            <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)]">
              <ProfileSectionTitle icon={CalendarDays} eyebrow="Lịch ưu tiên" title="Thời gian thường chơi" action="Cập nhật" href="/profile/preferences#availability" />
              <div className="mt-5 flex flex-wrap gap-2">
                {availabilityLabels.length > 0
                  ? availabilityLabels.map((label) => <span key={label} className="rounded-lg bg-emerald-700 px-4 py-2.5 text-sm font-semibold text-white">{label}</span>)
                  : <p className="text-sm font-medium text-stone-500">Chưa thiết lập khung giờ nào.</p>}
              </div>
            </section>
          </div>

          <aside className="grid gap-6 lg:sticky lg:top-24">
            <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)]">
              <ProfileSectionTitle icon={MapPinned} eyebrow="Khu vực" title="Địa điểm ưu tiên" action="Quản lý" href="/profile/preferences#locations" />
              <div className="mt-5 grid gap-3">
                {profile.locations.length === 0 && (
                  <p className="rounded-lg bg-stone-50 p-4 text-xs font-medium leading-5 text-stone-500">
                    Chưa có khu vực ưu tiên. Bấm Quản lý để thêm.
                  </p>
                )}
                {profile.locations.map((location) => (
                  <div key={location.id} className="rounded-lg border border-emerald-100 p-4">
                    <div className="flex items-center justify-between gap-3"><p className="font-semibold text-emerald-950">{location.label}</p>{location.isDefault && <span className="rounded-full bg-lime-200 px-2.5 py-1 text-[10px] font-bold uppercase text-emerald-900">Mặc định</span>}</div>
                    <p className="mt-2 text-xs font-medium leading-5 text-stone-500">{location.address || 'Chưa nhập địa chỉ'}</p>
                    <p className="mt-2 text-xs font-semibold text-emerald-700">Trong bán kính {location.radiusKm} km</p>
                    {location.latitude == null && (
                      <p className="mt-2 text-xs font-medium leading-5 text-amber-700">Chưa ghim vị trí trên bản đồ, nên không dùng để tìm sân theo bán kính. Bấm <span className="font-semibold">Quản lý</span> để ghim.</p>
                    )}
                  </div>
                ))}
              </div>
            </section>

            <section className="overflow-hidden rounded-xl bg-emerald-900 p-5 text-white shadow-[0_14px_34px_rgba(6,78,59,0.18)]">
              <p className="text-xs font-bold uppercase tracking-[0.15em] text-lime-300">Mức độ hoàn thiện</p>
              <div className="mt-3 flex items-end justify-between"><p className="text-3xl font-semibold">{completion.percent}%</p><Award className="size-8 text-lime-300" /></div>
              <div className="mt-4 h-2 overflow-hidden rounded-full bg-white/15"><div className="h-full rounded-full bg-lime-300 transition-all" style={{ width: `${completion.percent}%` }} /></div>
              {completion.missing.length > 0 ? (
                <div className="mt-3">
                  <p className="text-xs font-medium text-emerald-100/75">Còn thiếu:</p>
                  <ul className="mt-2 grid gap-1.5">
                    {completion.missing.slice(0, 3).map((item) => (
                      <li key={item} className="flex items-start gap-2 text-xs font-medium leading-5 text-emerald-100/85">
                        <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-lime-300" />{item}
                      </li>
                    ))}
                  </ul>
                </div>
              ) : (
                <p className="mt-3 text-xs font-medium leading-5 text-emerald-100/75">Hồ sơ đã đầy đủ. Bạn sẽ được ghép cặp chính xác hơn.</p>
              )}
            </section>
          </aside>
        </div>
      </section>

      <BottomNav activeLabel="Tài khoản" />
    </main>
  )
}

/**
 * Mức độ hoàn thiện hồ sơ, tính từ những trường thực sự có trong dữ liệu.
 *
 * Dùng cho gợi ý người chơi bổ sung thông tin còn thiếu — càng đầy đủ thì
 * ghép cặp ở Sprint 2 càng chính xác.
 */
function getProfileCompletion(user, profile) {
  const checks = [
    { done: Boolean(user.avatarUrl), label: 'Thêm ảnh đại diện' },
    { done: Boolean(profile.bio), label: 'Viết giới thiệu bản thân' },
    { done: Boolean(profile.dateOfBirth), label: 'Bổ sung ngày sinh' },
    { done: Boolean(profile.gender), label: 'Chọn giới tính' },
    { done: Boolean(profile.skillLevel), label: 'Chọn trình độ' },
    { done: Boolean(profile.playingStyle), label: 'Chọn phong cách chơi' },
    { done: Boolean(profile.preferredPlayType), label: 'Chọn hình thức chơi' },
    { done: Boolean(profile.dominantHand), label: 'Chọn tay thuận' },
    { done: profile.availability.length > 0, label: 'Thiết lập khung giờ chơi' },
    { done: profile.locations.length > 0, label: 'Thêm khu vực ưu tiên' },
    {
      done: profile.locations.some((location) => location.latitude != null),
      label: 'Ghim toạ độ khu vực ưu tiên',
    },
  ]

  const done = checks.filter((check) => check.done).length
  return {
    percent: Math.round((done / checks.length) * 100),
    missing: checks.filter((check) => !check.done).map((check) => check.label),
  }
}

/** Dựng giá trị ban đầu của form /profile/edit từ phản hồi API. */
function toEditForm(data) {
  return {
    name: data.user.name ?? '',
    email: data.user.email ?? '',
    phone: data.user.phone ?? '',
    gender: data.profile.gender ?? '',
    dateOfBirth: data.profile.dateOfBirth ?? '',
    bio: data.profile.bio ?? '',
  }
}

function ProfileEditPage({ user, onLogout }) {
  const { status, data, error, reload } = useProfile()
  const [editedForm, setEditedForm] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})

  if (!user) return <ProfileEmptyState />
  if (status === 'loading') return <ProfileLoading user={user} onLogout={onLogout} />
  if (status === 'error') {
    return <ProfileLoadError user={user} onLogout={onLogout} message={error} onRetry={reload} />
  }

  // Giá trị hiển thị được suy ra lúc render: dữ liệu từ API cho tới khi người dùng sửa.
  const form = editedForm ?? toEditForm(data)

  function updateField(field, value) {
    setEditedForm((current) => ({ ...(current ?? toEditForm(data)), [field]: value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting) return

    // Các điều kiện dưới đây khớp đúng với UpdateProfileRequest ở backend.
    const nextFieldErrors = {}
    if (form.name.trim().length < 2) {
      nextFieldErrors.fullName = 'Họ và tên từ 2 đến 150 ký tự'
    }
    if (!form.email.trim() && !form.phone.trim()) {
      nextFieldErrors.identityProvided = 'Vui lòng nhập email hoặc số điện thoại'
    }
    if (form.email.trim() && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(form.email.trim())) {
      nextFieldErrors.email = 'Email không đúng định dạng'
    }
    if (form.bio.length > 240) {
      nextFieldErrors.bio = 'Giới thiệu tối đa 240 ký tự'
    }
    if (Object.keys(nextFieldErrors).length > 0) {
      setFieldErrors(nextFieldErrors)
      setFormError('')
      return
    }

    setSubmitting(true)
    setFormError('')
    setFieldErrors({})

    try {
      const updated = await updateProfileApi({
        fullName: form.name.trim(),
        email: form.email.trim(),
        phone: form.phone.trim(),
        gender: form.gender,
        dateOfBirth: form.dateOfBirth,
        bio: form.bio,
      })
      saveCachedUser(updated.user)
      window.location.href = '/profile'
    } catch (submitError) {
      if (submitError instanceof ApiError) {
        setFieldErrors(submitError.fieldErrors ?? {})
        setFormError(submitError.message)
      } else {
        setFormError('Không lưu được hồ sơ, vui lòng thử lại.')
      }
      setSubmitting(false)
    }
  }

  return (
    <main className="min-h-screen bg-[linear-gradient(145deg,#eef8f2,#f9faf6)] pb-24 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="mx-auto max-w-[960px] px-4 py-7 md:px-6 md:py-10">
        <div className="mb-4"><PageBackLink href="/profile" label="Về hồ sơ" /></div>
        <div className="mb-6 flex items-center gap-3 text-sm font-semibold text-emerald-700"><a href="/profile">Hồ sơ</a><ChevronRight className="size-4" /><span className="text-stone-500">Chỉnh sửa</span></div>
        <form className="overflow-hidden rounded-xl border border-emerald-100 bg-white shadow-[0_16px_42px_rgba(25,70,48,0.09)]" onSubmit={handleSubmit}>
          <div className="border-b border-emerald-100 bg-[linear-gradient(130deg,#064e3b,#04793f)] px-6 py-7 text-white sm:px-8">
            <p className="text-xs font-bold uppercase tracking-[0.15em] text-lime-300">2.1.7 · Chỉnh sửa hồ sơ</p>
            <h1 className="mt-2 text-2xl font-semibold">Thông tin cá nhân</h1>
            <p className="mt-2 text-sm font-medium text-emerald-100/75">Thông tin này được dùng cho tài khoản, booking và hồ sơ người chơi.</p>
          </div>

          <div className="grid gap-8 p-6 sm:p-8 md:grid-cols-[180px_minmax(0,1fr)]">
            <div className="text-center">
              <div className="relative mx-auto grid size-32 place-items-center rounded-2xl bg-emerald-100 text-5xl font-semibold text-emerald-800">
                {form.name.slice(0, 1).toUpperCase() || 'B'}
                {/* Chưa có API tải ảnh đại diện. Vô hiệu hoá thay vì để nút không làm gì. */}
                <button type="button" disabled title="Chức năng đang được phát triển" className="absolute -bottom-3 -right-3 grid size-11 cursor-not-allowed place-items-center rounded-full border-4 border-white bg-emerald-700 text-white opacity-60 shadow-lg" aria-label="Đổi ảnh đại diện"><Camera className="size-5" /></button>
              </div>
              <p className="mt-5 text-sm font-semibold">Ảnh đại diện</p><p className="mt-1 text-xs font-medium leading-5 text-stone-400">JPG hoặc PNG, tối đa 5 MB</p>
            </div>

            <div className="grid gap-5 sm:grid-cols-2">
              {[
                { field: 'name', label: 'Họ và tên (*)', type: 'text', placeholder: 'Nhập họ và tên', required: true, error: fieldErrors.fullName },
                { field: 'phone', label: 'Số điện thoại', type: 'tel', placeholder: 'Nhập số điện thoại', required: false, error: fieldErrors.phone ?? fieldErrors.identityProvided },
                { field: 'email', label: 'Email', type: 'email', placeholder: 'name@email.com', required: false, error: fieldErrors.email ?? fieldErrors.identityProvided },
                { field: 'dateOfBirth', label: 'Ngày sinh', type: 'date', placeholder: '', required: false, error: fieldErrors.dateOfBirth ?? fieldErrors.ageReasonable },
              ].map((item) => (
                <label key={item.field} className="block"><span className="text-sm font-semibold">{item.label}</span><input className="mt-2.5 h-12 w-full rounded-lg border border-stone-200 px-4 text-sm outline-none focus:border-emerald-500 focus:ring-1 focus:ring-emerald-300" value={form[item.field]} onChange={(event) => updateField(item.field, event.target.value)} type={item.type} placeholder={item.placeholder} required={item.required} /><FieldError message={item.error} /></label>
              ))}

              <label className="block sm:col-span-2"><span className="text-sm font-semibold">Giới tính</span><div className="mt-2.5 grid grid-cols-3 gap-2">{[['male', 'Nam'], ['female', 'Nữ'], ['other', 'Khác']].map(([value, label]) => <button key={value} type="button" className={classNames('h-11 rounded-lg border text-sm font-semibold', form.gender === value ? 'border-emerald-600 bg-emerald-50 text-emerald-700 ring-1 ring-emerald-500' : 'border-stone-200 text-stone-500')} onClick={() => updateField('gender', value)}>{label}</button>)}</div></label>

              <label className="block sm:col-span-2"><span className="text-sm font-semibold">Giới thiệu bản thân</span><textarea className="mt-2.5 min-h-28 w-full resize-none rounded-lg border border-stone-200 p-4 text-sm leading-6 outline-none focus:border-emerald-500 focus:ring-1 focus:ring-emerald-300" value={form.bio} onChange={(event) => updateField('bio', event.target.value)} maxLength={240} /><span className="mt-1.5 block text-right text-xs font-medium text-stone-400">{form.bio.length}/240</span><FieldError message={fieldErrors.bio} /></label>
            </div>
          </div>

          <div className="border-t border-emerald-100 bg-stone-50 px-6 py-5 sm:px-8">
            <div className="mb-4"><FormAlert message={formError} /></div>
            <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
              <a className="flex h-12 items-center justify-center rounded-lg border border-stone-200 bg-white px-6 text-sm font-semibold text-stone-600" href="/profile">Hủy thay đổi</a>
              <button type="submit" disabled={submitting} className="flex h-12 items-center justify-center gap-2 rounded-lg bg-emerald-700 px-7 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60"><Save className="size-4" />{submitting ? 'Đang lưu...' : 'Lưu hồ sơ'}</button>
            </div>
          </div>
        </form>
      </section>
      <BottomNav activeLabel="Tài khoản" />
    </main>
  )
}

function ProfilePreferencesPage({ user, onLogout }) {
  const { status, data, error, reload } = useProfile()
  const [editedProfile, setEditedProfile] = useState(null)
  const [openMapId, setOpenMapId] = useState(null)
  const [saved, setSaved] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})

  if (!user) return <ProfileEmptyState />
  if (status === 'loading') return <ProfileLoading user={user} onLogout={onLogout} />
  if (status === 'error') {
    return <ProfileLoadError user={user} onLogout={onLogout} message={error} onRetry={reload} />
  }

  // Dữ liệu từ API cho tới khi người dùng chạm vào, sau đó là bản đang sửa.
  const profile = editedProfile ?? data.profile
  const setProfile = (updater) =>
    setEditedProfile((current) =>
      typeof updater === 'function' ? updater(current ?? data.profile) : updater)

  function selectPreference(field, value) {
    setSaved(false)
    setProfile((current) => ({ ...current, [field]: value }))
  }

  function toggleDay(day) {
    setSaved(false)
    setProfile((current) => {
      const exists = current.availability.some((slot) => slot.day === day)
      return {
        ...current,
        availability: exists
          ? current.availability.filter((slot) => slot.day !== day)
          : [...current.availability, { day, startTime: '18:00', endTime: '21:00' }].sort((a, b) => a.day - b.day),
      }
    })
  }

  function updateSlot(day, field, value) {
    setSaved(false)
    setProfile((current) => ({ ...current, availability: current.availability.map((slot) => slot.day === day ? { ...slot, [field]: value } : slot) }))
  }

  function updateLocation(id, field, value) {
    setSaved(false)
    setProfile((current) => ({ ...current, locations: current.locations.map((location) => location.id === id ? { ...location, [field]: value } : location) }))
  }

  function addLocation() {
    setSaved(false)
    setProfile((current) => ({ ...current, locations: [...current.locations, { id: `tam-${Date.now()}`, label: 'Địa điểm mới', address: '', latitude: null, longitude: null, radiusKm: 5, isDefault: current.locations.length === 0 }] }))
  }

  function removeLocation(id) {
    setSaved(false)
    setProfile((current) => {
      const remaining = current.locations.filter((location) => location.id !== id)
      // Nghiệp vụ yêu cầu nhiều nhất một mặc định; xoá đúng cái mặc định thì đẩy cho cái đầu tiên.
      if (remaining.length > 0 && !remaining.some((location) => location.isDefault)) {
        remaining[0] = { ...remaining[0], isDefault: true }
      }
      return { ...current, locations: remaining }
    })
  }

  /** Chỉ một địa điểm được là mặc định, nên đặt cái này thì bỏ các cái còn lại. */
  function setDefaultLocation(id) {
    setSaved(false)
    setProfile((current) => ({
      ...current,
      locations: current.locations.map((location) => ({ ...location, isDefault: location.id === id })),
    }))
  }

  function setLocationCoords(id, coords) {
    setSaved(false)
    setProfile((current) => ({
      ...current,
      locations: current.locations.map((location) =>
        location.id === id ? { ...location, ...coords } : location),
    }))
  }

  function toggleMapFor(id) {
    setOpenMapId((current) => (current === id ? null : id))
  }

  async function handleSave() {
    if (submitting) return

    // Khớp đúng UpdatePreferencesRequest ở backend.
    const nextFieldErrors = {}
    if (profile.availability.some((slot) => slot.endTime <= slot.startTime)) {
      nextFieldErrors.availability = 'Giờ kết thúc phải sau giờ bắt đầu'
    }
    if (profile.locations.some((location) => !location.label.trim())) {
      nextFieldErrors.locations = 'Tên địa điểm không được để trống'
    }
    if (profile.locations.filter((location) => location.isDefault).length > 1) {
      nextFieldErrors.locations = 'Chỉ được chọn một địa điểm mặc định'
    }
    if (Object.keys(nextFieldErrors).length > 0) {
      setFieldErrors(nextFieldErrors)
      setFormError('')
      setSaved(false)
      return
    }

    setSubmitting(true)
    setFormError('')
    setFieldErrors({})

    try {
      const updated = await updatePreferencesApi(profile)
      // Đọc lại từ phản hồi của server thay vì giữ state cũ ở client.
      setProfile(updated.profile)
      setSaved(true)
    } catch (saveError) {
      setSaved(false)
      if (saveError instanceof ApiError) {
        setFieldErrors(saveError.fieldErrors ?? {})
        setFormError(saveError.message)
      } else {
        setFormError('Không lưu được thiết lập, vui lòng thử lại.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="min-h-screen bg-[linear-gradient(145deg,#eef8f2,#f9faf6)] pb-28 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="mx-auto max-w-[1100px] px-4 py-7 md:px-6 md:py-10">
        <div className="mb-4"><PageBackLink href="/profile" label="Về hồ sơ" /></div>
        <div className="mb-6 flex flex-wrap items-end justify-between gap-4"><div><div className="flex items-center gap-2 text-sm font-semibold text-emerald-700"><a href="/profile">Hồ sơ</a><ChevronRight className="size-4" /><span className="text-stone-500">Thiết lập chơi</span></div><h1 className="mt-3 text-3xl font-semibold tracking-tight">Thiết lập người chơi</h1><p className="mt-2 text-sm font-medium text-stone-500">Dữ liệu này giúp tìm sân và ghép người chơi phù hợp hơn.</p></div>{saved && <span className="inline-flex items-center gap-2 rounded-full bg-emerald-100 px-4 py-2 text-sm font-semibold text-emerald-700"><CheckCircle2 className="size-4" />Đã lưu thay đổi</span>}</div>

        <div className="grid gap-6">
          <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)] sm:p-6">
            <ProfileSectionTitle icon={Award} eyebrow="2.1.8 · Kỹ năng" title="Trình độ hiện tại" action={profile.skillScore == null ? 'Chưa có điểm kỹ năng' : `Điểm kỹ năng ${profile.skillScore}`} />
            <div className="mt-5 grid gap-3 md:grid-cols-2">{skillOptions.map((option) => <button key={option.id} className={classNames('flex items-start gap-4 rounded-lg border p-4 text-left transition', profile.skillLevel === option.id ? 'border-emerald-500 bg-emerald-50 ring-1 ring-emerald-400' : 'border-stone-200 hover:border-emerald-200')} onClick={() => selectPreference('skillLevel', option.id)}><span className={classNames('mt-0.5 grid size-6 shrink-0 place-items-center rounded-full border-2', profile.skillLevel === option.id ? 'border-emerald-500' : 'border-stone-300')}>{profile.skillLevel === option.id && <span className="size-3 rounded-full bg-emerald-500" />}</span><span><span className="block text-sm font-semibold text-emerald-950">{option.label}</span><span className="mt-1 block text-xs font-medium leading-5 text-stone-500">{option.description}</span></span></button>)}</div>
            <div className="mt-5 border-t border-emerald-100 pt-5"><p className="text-sm font-semibold">Tay thuận</p><div className="mt-3 flex gap-3">{handOptions.map((option) => <button key={option.id} type="button" className={classNames('h-11 flex-1 rounded-lg border text-sm font-semibold', profile.dominantHand === option.id ? 'border-emerald-600 bg-emerald-700 text-white' : 'border-stone-200 text-stone-500')} onClick={() => selectPreference('dominantHand', option.id)}>{option.label}</button>)}</div></div>
          </section>

          <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)] sm:p-6">
            <ProfileSectionTitle icon={Target} eyebrow="2.1.9 · Sở thích" title="Phong cách chơi" />
            <div className="mt-5 grid gap-3 md:grid-cols-3">{playingStyleOptions.map((option) => <button key={option.id} className={classNames('rounded-lg border p-4 text-left', profile.playingStyle === option.id ? 'border-emerald-500 bg-emerald-50 ring-1 ring-emerald-400' : 'border-stone-200')} onClick={() => selectPreference('playingStyle', option.id)}><span className="flex items-center justify-between"><span className="text-sm font-semibold">{option.label}</span>{profile.playingStyle === option.id && <CheckCircle2 className="size-5 text-emerald-600" />}</span><span className="mt-2 block text-xs font-medium leading-5 text-stone-500">{option.description}</span></button>)}</div>
          </section>

          <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)] sm:p-6">
            <ProfileSectionTitle icon={Users} eyebrow="2.1.12 · Hình thức" title="Bạn thích chơi theo hình thức nào?" />
            <div className="mt-5 grid gap-3 sm:grid-cols-3">{playTypeOptions.map((option) => { const Icon = option.icon; const active = profile.preferredPlayType === option.id; return <button key={option.id} className={classNames('flex items-center gap-3 rounded-lg border p-4 text-left', active ? 'border-emerald-500 bg-emerald-700 text-white ring-1 ring-emerald-400' : 'border-stone-200 text-emerald-950')} onClick={() => selectPreference('preferredPlayType', option.id)}><span className={classNames('grid size-10 place-items-center rounded-lg', active ? 'bg-white/15' : 'bg-emerald-50 text-emerald-700')}><Icon className="size-5" /></span><span className="text-sm font-semibold">{option.label}</span></button> })}</div>
          </section>

          <section id="availability" className="scroll-mt-24 rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)] sm:p-6">
            <ProfileSectionTitle icon={CalendarDays} eyebrow="2.1.10 · Thời gian" title="Khung giờ chơi ưu tiên" />
            <div className="mt-5 grid grid-cols-7 gap-2">{weekDays.map((day) => { const active = profile.availability.some((slot) => slot.day === day.value); return <button key={day.value} className={classNames('h-12 rounded-lg text-sm font-semibold ring-1', active ? 'bg-emerald-700 text-white ring-emerald-700' : 'bg-stone-50 text-stone-400 ring-stone-200')} onClick={() => toggleDay(day.value)} title={day.label}>{day.short}</button> })}</div>
            <div className="mt-5 grid gap-3">{profile.availability.map((slot) => <div key={slot.day} className="grid items-center gap-3 rounded-lg bg-emerald-50/70 p-4 ring-1 ring-emerald-100 sm:grid-cols-[140px_1fr_auto_1fr]"><p className="text-sm font-semibold">{weekDays.find((day) => day.value === slot.day)?.label}</p><input className="h-10 rounded-lg border border-emerald-100 bg-white px-3 text-sm font-semibold outline-none focus:border-emerald-500" type="time" value={slot.startTime} onChange={(event) => updateSlot(slot.day, 'startTime', event.target.value)} /><span className="text-center text-xs font-medium text-stone-400">đến</span><input className="h-10 rounded-lg border border-emerald-100 bg-white px-3 text-sm font-semibold outline-none focus:border-emerald-500" type="time" value={slot.endTime} onChange={(event) => updateSlot(slot.day, 'endTime', event.target.value)} /></div>)}</div>
          </section>

          <section id="locations" className="scroll-mt-24 rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)] sm:p-6">
            <ProfileSectionTitle icon={MapPinned} eyebrow="2.1.11 · Địa điểm" title="Khu vực chơi ưu tiên" />
            <div className="mt-5 grid gap-4">
              {profile.locations.length === 0 && (
                <p className="rounded-lg bg-stone-50 p-4 text-sm font-medium text-stone-500">
                  Chưa có địa điểm nào. Thêm khu vực bạn hay chơi để tìm sân gần hơn.
                </p>
              )}
              {profile.locations.map((location) => {
                const hasCoords = location.latitude != null && location.longitude != null
                const mapOpen = openMapId === location.id
                return (
                  <div key={location.id} className="rounded-lg border border-emerald-100 p-4">
                    <div className="flex items-center gap-3">
                      <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-emerald-50 text-emerald-700"><MapPin className="size-5" /></span>
                      <div className="min-w-0 flex-1">
                        <input className="w-full bg-transparent text-sm font-semibold outline-none" value={location.label} onChange={(event) => updateLocation(location.id, 'label', event.target.value)} placeholder="Tên địa điểm" />
                        <input className="mt-1 w-full bg-transparent text-xs font-medium text-stone-500 outline-none" value={location.address} onChange={(event) => updateLocation(location.id, 'address', event.target.value)} placeholder="Nhập khu vực ưu tiên" />
                      </div>
                      {profile.locations.length > 1 && (
                        <button type="button" className="grid size-9 place-items-center rounded-lg text-red-500 hover:bg-red-50" onClick={() => removeLocation(location.id)} aria-label="Xóa địa điểm"><Trash2 className="size-4" /></button>
                      )}
                    </div>

                    <div className="mt-4 flex items-center gap-4">
                      <span className="text-xs font-medium text-stone-500">Bán kính</span>
                      <input className="min-w-0 flex-1 accent-emerald-600" type="range" min="1" max="30" value={location.radiusKm} onChange={(event) => updateLocation(location.id, 'radiusKm', Number(event.target.value))} />
                      <span className="w-12 text-right text-sm font-semibold text-emerald-700">{location.radiusKm} km</span>
                    </div>

                    <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-emerald-100 pt-4">
                      {location.isDefault ? (
                        <span className="inline-flex h-9 items-center rounded-lg bg-lime-200 px-3 text-xs font-bold uppercase text-emerald-900">Mặc định</span>
                      ) : (
                        <button type="button" className="h-9 rounded-lg border border-stone-200 px-3 text-xs font-semibold text-stone-600 hover:border-emerald-300" onClick={() => setDefaultLocation(location.id)}>Đặt mặc định</button>
                      )}

                      <span className={classNames('inline-flex h-9 items-center gap-1.5 rounded-lg px-3 text-xs font-semibold', hasCoords ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700')}>
                        {hasCoords ? <CheckCircle2 className="size-3.5" /> : <MapPinned className="size-3.5" />}
                        {hasCoords ? `${location.latitude.toFixed(4)}, ${location.longitude.toFixed(4)}` : 'Chưa ghim toạ độ'}
                      </span>

                      <button type="button" className="ml-auto h-9 rounded-lg border border-emerald-200 bg-emerald-50 px-3 text-xs font-semibold text-emerald-700" onClick={() => toggleMapFor(location.id)}>
                        {mapOpen ? 'Đóng bản đồ' : hasCoords ? 'Sửa toạ độ' : 'Ghim trên bản đồ'}
                      </button>
                    </div>

                    {!hasCoords && !mapOpen && (
                      <p className="mt-2 text-xs font-medium leading-5 text-amber-700">
                        Bán kính {location.radiusKm} km chỉ có tác dụng khi đã ghim vị trí trên bản đồ.
                        Bấm <span className="font-semibold">Ghim trên bản đồ</span> để chọn điểm xuất phát.
                      </p>
                    )}

                    {mapOpen && (
                      <LocationPicker
                        latitude={location.latitude}
                        longitude={location.longitude}
                        radiusKm={location.radiusKm}
                        onChange={(coords) => setLocationCoords(location.id, coords)}
                      />
                    )}
                  </div>
                )
              })}
            </div>
            <button className="mt-4 flex h-11 w-full items-center justify-center gap-2 rounded-lg border border-dashed border-emerald-300 bg-emerald-50/50 text-sm font-semibold text-emerald-700" onClick={addLocation}><Plus className="size-4" />Thêm địa điểm ưu tiên</button>
          </section>
        </div>

        <div className="sticky bottom-20 z-30 mt-6 rounded-xl border border-emerald-100 bg-white/95 p-4 shadow-[0_14px_38px_rgba(24,65,44,0.16)] backdrop-blur">
          {(formError || fieldErrors.availability || fieldErrors.locations) && (
            <div className="mb-3"><FormAlert message={formError || fieldErrors.availability || fieldErrors.locations} /></div>
          )}
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-xs font-medium text-stone-500">Các thiết lập sẽ được dùng cho tìm sân và ghép cặp thông minh.</p>
            <div className="flex gap-3">
              <a className="flex h-11 items-center justify-center rounded-lg border border-stone-200 px-5 text-sm font-semibold text-stone-600" href="/profile">Hủy</a>
              <button type="button" disabled={submitting} className="flex h-11 items-center justify-center gap-2 rounded-lg bg-emerald-700 px-6 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60" onClick={handleSave}><Save className="size-4" />{submitting ? 'Đang lưu...' : 'Lưu thiết lập'}</button>
            </div>
          </div>
        </div>
      </section>
      <BottomNav activeLabel="Tài khoản" />
    </main>
  )
}

const bookingStatusMeta = {
  pending_payment: { label: 'Chờ thanh toán', className: 'bg-amber-100 text-amber-700' },
  confirmed: { label: 'Đã xác nhận', className: 'bg-emerald-100 text-emerald-700' },
  cancelled: { label: 'Đã hủy', className: 'bg-red-100 text-red-700' },
  completed: { label: 'Đã hoàn thành', className: 'bg-sky-100 text-sky-700' },
  expired: { label: 'Đã hết hạn', className: 'bg-stone-200 text-stone-600' },
  refunded: { label: 'Đã hoàn tiền', className: 'bg-violet-100 text-violet-700' },
}

function BookingStatusBadge({ status }) {
  const meta = bookingStatusMeta[status] ?? bookingStatusMeta.pending_payment
  return <span className={classNames('inline-flex rounded-full px-3 py-1.5 text-xs font-bold', meta.className)}>{meta.label}</span>
}

function formatBookingDate(value) {
  return new Intl.DateTimeFormat('vi-VN', { weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric' }).format(new Date(value))
}

function formatBookingTime(value) {
  return new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value))
}

function BookingEmptyState({ title, description, actionLabel = 'Tìm sân ngay', actionHref = '/' }) {
  return (
    <div className="rounded-xl border border-emerald-100 bg-white px-6 py-12 text-center shadow-[0_12px_34px_rgba(24,65,44,0.08)]">
      <span className="mx-auto grid size-16 place-items-center rounded-full bg-emerald-50 text-emerald-700"><CalendarDays className="size-8" /></span>
      <h2 className="mt-5 text-xl font-semibold text-emerald-950">{title}</h2>
      <p className="mx-auto mt-2 max-w-md text-sm font-medium leading-6 text-stone-500">{description}</p>
      <a className="mt-6 inline-flex h-11 items-center justify-center rounded-lg bg-emerald-700 px-6 text-sm font-semibold text-white" href={actionHref}>{actionLabel}</a>
    </div>
  )
}

function BookingCheckoutPage({ user, onLogout }) {
  const [pending] = useState(() => readPendingBooking())
  const [note, setNote] = useState('')
  const [paymentMethod, setPaymentMethod] = useState('sepay')
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState('')

  // Người chơi trả đúng tiền sân. Phí nền tảng được trừ vào doanh thu chủ sân
  // (owner_balance_transactions), không cộng thêm vào hoá đơn người chơi.
  const totalAmount = Number(pending?.price ?? 0)
  const venue = pending ? { id: pending.venueId, name: pending.venueName } : null
  const court = pending ? { id: pending.courtId, name: pending.courtName } : null

  if (!user) return <ProfileEmptyState />

  /** 2.1.28 - gửi lựa chọn lên server; server tính lại giá và kiểm tra lại khung giờ. */
  async function createBooking(event) {
    event.preventDefault()
    if (submitting || !pending) return

    setSubmitting(true)
    setFormError('')
    try {
      const booking = await createBookingApi({
        courtId: pending.courtId,
        startTime: pending.startAt,
        durationMinutes: pending.durationMinutes,
        note: note.trim(),
      })
      // Tạo xong thì bỏ lựa chọn tạm để bấm back không tạo đơn thứ hai.
      window.sessionStorage.removeItem(PENDING_BOOKING_STORAGE_KEY)
      window.sessionStorage.removeItem(LEGACY_STORAGE_KEYS.pendingBooking)
      window.location.href = `/bookings/${booking.id}`
    } catch (error) {
      setSubmitting(false)
      if (error instanceof ApiError) {
        setFormError(error.code === 'BOOKING_SLOT_TAKEN'
          ? `${error.message} Vui lòng quay lại chọn khung giờ khác.`
          : error.message)
      } else {
        setFormError('Không tạo được đơn, vui lòng thử lại.')
      }
    }
  }

  return (
    <main className="min-h-screen bg-[linear-gradient(145deg,#eef8f2,#f9faf6)] pb-24 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="mx-auto max-w-[1100px] px-4 py-7 md:px-6 md:py-10">
        <PageBackLink href={venue ? `/san/${venue.id}#booking` : '/'} label="Quay lại chọn lịch" />
        <div className="mt-6"><p className="text-xs font-bold uppercase tracking-[0.15em] text-emerald-600">2.1.28 · Tạo yêu cầu đặt sân</p><h1 className="mt-2 text-3xl font-semibold tracking-tight">Xác nhận đặt sân</h1><p className="mt-2 text-sm font-medium text-stone-500">Kiểm tra thông tin trước khi tạo booking và chuyển sang bước thanh toán.</p></div>

        {!pending || !venue || !court ? (
          <div className="mt-7"><BookingEmptyState title="Chưa có lịch sân được chọn" description="Hãy chọn sân con, ngày và khung giờ từ trang chi tiết sân trước khi tiếp tục." actionLabel="Quay lại tìm sân" /></div>
        ) : (
          <form className="mt-7 grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_380px]" onSubmit={createBooking}>
            <div className="grid gap-6">
              <section className="overflow-hidden rounded-xl border border-emerald-100 bg-white shadow-[0_12px_34px_rgba(24,65,44,0.08)]">
                <div className="grid gap-5 p-5 sm:grid-cols-[180px_minmax(0,1fr)]">
                  <img className="h-36 w-full rounded-lg object-cover" src={venue.image} alt={venue.name} />
                  <div><div className="flex flex-wrap items-start justify-between gap-3"><div><p className="text-xs font-bold uppercase tracking-[0.12em] text-emerald-600">Sân đã chọn</p><h2 className="mt-2 text-xl font-semibold">{venue.name}</h2></div><span className="rounded-full bg-lime-100 px-3 py-1 text-xs font-bold text-emerald-800">Giữ chỗ 10 phút</span></div><p className="mt-3 flex gap-2 text-sm font-medium leading-6 text-stone-500"><MapPin className="mt-0.5 size-4 shrink-0 text-emerald-600" />{venue.address}</p><a className="mt-3 inline-flex items-center gap-2 text-xs font-semibold text-emerald-700" href={`/san/${venue.id}`}>Xem lại thông tin sân <ChevronRight className="size-4" /></a></div>
                </div>
                <div className="grid gap-px border-t border-emerald-100 bg-emerald-100 sm:grid-cols-3">{[[court.name, 'Sân con'], [formatBookingDate(`${pending.date}T12:00:00`), 'Ngày chơi'], [`${pending.startTime} - ${pending.endTime}`, `${pending.durationMinutes} phút`]].map(([value, label]) => <div key={label} className="bg-emerald-50/50 p-4"><p className="text-xs font-medium text-stone-400">{label}</p><p className="mt-1.5 text-sm font-semibold text-emerald-950">{value}</p></div>)}</div>
              </section>

              <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_12px_34px_rgba(24,65,44,0.08)]">
                <ProfileSectionTitle icon={UserRound} eyebrow="Người đặt" title="Thông tin liên hệ" />
                <div className="mt-5 grid gap-3 sm:grid-cols-2">{[['Họ và tên', user.name], ['Số điện thoại', user.phone], ['Email', user.email]].map(([label, value], index) => <div key={label} className={classNames('rounded-lg bg-stone-50 px-4 py-3 ring-1 ring-stone-100', index === 2 && 'sm:col-span-2')}><p className="text-xs font-medium text-stone-400">{label}</p><p className="mt-1 text-sm font-semibold">{value}</p></div>)}</div>
                <label className="mt-5 block"><span className="text-sm font-semibold">Ghi chú cho sân</span><textarea className="mt-2.5 min-h-24 w-full resize-none rounded-lg border border-stone-200 p-4 text-sm leading-6 outline-none focus:border-emerald-500" value={note} onChange={(event) => setNote(event.target.value)} placeholder="Ví dụ: chuẩn bị nước, thuê vợt..." maxLength={240} /></label>
              </section>

              <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_12px_34px_rgba(24,65,44,0.08)]">
                <ProfileSectionTitle icon={CreditCard} eyebrow="Thanh toán" title="Phương thức thanh toán" />
                <button type="button" className={classNames('mt-5 flex w-full items-center gap-4 rounded-xl border p-4 text-left', paymentMethod === 'sepay' ? 'border-emerald-500 bg-emerald-50 ring-1 ring-emerald-400' : 'border-stone-200')} onClick={() => setPaymentMethod('sepay')}><span className="grid size-12 place-items-center rounded-lg bg-emerald-700 text-lg font-bold text-white">S</span><span className="flex-1"><span className="block text-sm font-semibold">Thanh toán qua SePay</span><span className="mt-1 block text-xs font-medium text-stone-500">Quét QR hoặc chuyển khoản ngân hàng ở bước tiếp theo.</span></span><CheckCircle2 className="size-5 text-emerald-600" /></button>
              </section>
            </div>

            <aside className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_14px_38px_rgba(24,65,44,0.10)] lg:sticky lg:top-20">
              <ProfileSectionTitle icon={FileText} eyebrow="Đơn đặt sân" title="Chi tiết thanh toán" />
              <div className="mt-5 grid gap-3 text-sm font-medium text-stone-500"><p className="flex justify-between gap-4"><span>Tiền sân</span><span className="font-semibold text-emerald-950">{formatCurrency(pending.price)}</span></p><p className="flex justify-between gap-4"><span>Giảm giá</span><span className="font-semibold text-emerald-700">0đ</span></p></div>
              <div className="mt-5 flex items-center justify-between border-t border-emerald-100 pt-5"><span className="text-sm font-semibold">Tổng thanh toán</span><span className="text-2xl font-semibold text-emerald-700">{formatCurrency(totalAmount)}</span></div>
              <div className="mt-5 rounded-lg bg-amber-50 p-4 text-xs font-medium leading-5 text-amber-800 ring-1 ring-amber-100"><strong>Chính sách giữ chỗ:</strong> booking sẽ hết hạn nếu chưa thanh toán trong 10 phút. Có thể hủy trước giờ chơi theo chính sách của sân.</div>
              <label className="mt-5 flex items-start gap-3 text-xs font-medium leading-5 text-stone-500"><input className="mt-1 accent-emerald-600" type="checkbox" required /><span>Tôi xác nhận thông tin đặt sân và đồng ý với chính sách hủy sân.</span></label>
              {formatError(formError)}
              <button type="submit" disabled={submitting} className="mt-5 flex h-12 w-full items-center justify-center gap-2 rounded-lg bg-lime-300 text-sm font-semibold text-emerald-950 shadow-[0_10px_22px_rgba(163,230,53,0.22)] disabled:cursor-not-allowed disabled:bg-stone-200 disabled:text-stone-400 disabled:shadow-none"><QrCode className="size-4" />{submitting ? 'Đang tạo đơn...' : 'Tạo yêu cầu đặt sân'}</button>
            </aside>
          </form>
        )}
      </section>
      <BottomNav activeLabel="Khám phá" />
    </main>
  )
}

const paymentStatusMeta = {
  pending: { label: 'Đang chờ thanh toán', className: 'bg-amber-100 text-amber-700', icon: Clock3 },
  paid: { label: 'Thanh toán thành công', className: 'bg-emerald-100 text-emerald-700', icon: CheckCircle2 },
  failed: { label: 'Thanh toán thất bại', className: 'bg-red-100 text-red-700', icon: CircleX },
  expired: { label: 'Giao dịch đã hết hạn', className: 'bg-stone-200 text-stone-600', icon: Clock3 },
}

function PaymentStatusBadge({ status }) {
  const meta = paymentStatusMeta[status] ?? paymentStatusMeta.pending
  const Icon = meta.icon
  return <span className={classNames('inline-flex items-center gap-2 rounded-full px-3 py-1.5 text-xs font-bold', meta.className)}><Icon className="size-3.5" />{meta.label}</span>
}

function PaymentPage({ paymentId, user, onLogout }) {
  const { status, payment, error, notFound, setPayment, reload } = usePayment(paymentId)
  const { booking } = useBookingDetail(payment?.bookingId)
  const [copied, setCopied] = useState('')
  const [retrying, setRetrying] = useState(false)
  const [retryError, setRetryError] = useState('')
  // Server trả secondsRemaining mỗi lần hỏi lại; giữa hai lần hỏi thì tự đếm xuống
  // cho mượt, nhưng mốc hết hạn luôn là của server chứ không phải đồng hồ máy.
  const [secondsLeft, setSecondsLeft] = useState(0)

  useEffect(() => {
    if (!payment) return
    // oxlint-disable-next-line react/set-state-in-effect
    setSecondsLeft(payment.secondsRemaining)
  }, [payment])

  useEffect(() => {
    if (payment?.status !== 'pending') return undefined
    const timer = window.setInterval(() => setSecondsLeft((value) => Math.max(0, value - 1)), 1000)
    return () => window.clearInterval(timer)
  }, [payment?.status])

  // Tiền về thì chuyển sang màn xác nhận đặt sân (2.1.40).
  useEffect(() => {
    if (payment?.status === 'paid' && payment.bookingId) {
      window.location.href = `/booking/success/${payment.bookingId}`
    }
  }, [payment?.status, payment?.bookingId])

  if (!user) return <ProfileEmptyState />

  if (status === 'loading') {
    return (
      <main className="min-h-screen bg-[#f4f7f1] pb-24">
        <AppHeader user={user} onLogout={onLogout} />
        <section className="mx-auto max-w-[1100px] px-4 py-10"><div className="h-80 animate-pulse rounded-2xl bg-emerald-100/60" /></section>
      </main>
    )
  }

  if (status === 'error' || !payment) {
    return (
      <main className="min-h-screen bg-[#f4f7f1] pb-24">
        <AppHeader user={user} onLogout={onLogout} />
        <section className="mx-auto max-w-[900px] px-4 py-10">
          <BookingEmptyState
            title={notFound ? 'Không tìm thấy giao dịch' : 'Không tải được giao dịch'}
            description={notFound ? 'Giao dịch có thể không tồn tại hoặc không thuộc về bạn.' : error}
            actionLabel="Xem booking của tôi"
            actionHref="/bookings" />
        </section>
      </main>
    )
  }

  async function handleRetry() {
    setRetrying(true)
    setRetryError('')
    try {
      setPayment(await retryPaymentApi(payment.id))
    } catch (retryFailure) {
      setRetryError(retryFailure?.message ?? 'Không mở lại được giao dịch.')
    } finally {
      setRetrying(false)
    }
  }

  async function copyValue(key, value) {
    try {
      await window.navigator.clipboard.writeText(value)
      setCopied(key)
      window.setTimeout(() => setCopied(''), 1600)
    } catch {
      setCopied('')
    }
  }

  const statusMeta = paymentStatusMeta[payment.status] ?? paymentStatusMeta.pending
  const StatusIcon = statusMeta.icon
  const isPending = payment.status === 'pending'
  const countdown = `${String(Math.floor(secondsLeft / 60)).padStart(2, '0')}:${String(secondsLeft % 60).padStart(2, '0')}`
  const venueName = booking?.venue?.name ?? ''

  return (
    <main className="min-h-screen bg-[radial-gradient(circle_at_top_left,#dff5e8_0,transparent_34%),linear-gradient(145deg,#eef8f2,#f9faf6)] pb-24 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="mx-auto max-w-[1100px] px-4 py-7 md:px-6 md:py-10">
        <PageBackLink href={`/bookings/${payment.bookingId}`} label="Về chi tiết booking" />
        <div className="mt-6 flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.15em] text-emerald-600">2.1.34–2.1.42 · Thanh toán SePay</p>
            <h1 className="mt-2 text-3xl font-semibold tracking-tight">Thanh toán đặt sân</h1>
            <p className="mt-2 text-sm font-medium text-stone-500">Mã booking {payment.bookingCode}{venueName ? ` · ${venueName}` : ''}</p>
          </div>
          <PaymentStatusBadge status={payment.status} />
        </div>

        <div className="mt-7 grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_390px]">
          <div className="grid gap-6">
            {isPending ? (
              <section className="overflow-hidden rounded-2xl border border-emerald-100 bg-white shadow-[0_18px_46px_rgba(24,65,44,0.10)]">
                <div className="border-b border-emerald-100 bg-emerald-950 px-5 py-4 text-white sm:px-6"><div className="flex items-center justify-between gap-4"><div><p className="text-xs font-bold uppercase tracking-[0.14em] text-lime-300">Quét mã để thanh toán</p><p className="mt-1 text-sm font-medium text-emerald-100">Mở ứng dụng ngân hàng hỗ trợ VietQR</p></div><span className="rounded-lg bg-white/10 px-3 py-2 font-mono text-lg font-bold text-lime-300">{countdown}</span></div></div>
                <div className="grid gap-7 p-5 sm:grid-cols-[260px_minmax(0,1fr)] sm:p-7">
                  <div>
                    <div className="mx-auto max-w-[260px] rounded-2xl border border-emerald-100 bg-white p-4 shadow-[0_12px_28px_rgba(6,78,59,0.10)]">
                      {/* Ảnh QR do SePay sinh, backend trả về qua payments.qr_url (2.1.35). */}
                      <img className="aspect-square w-full" src={payment.qrUrl} alt={`Mã QR chuyển khoản ${formatCurrency(payment.amount)}`} />
                    </div>
                    <p className="mt-3 text-center text-[11px] font-semibold text-stone-500">Quét bằng ứng dụng ngân hàng để điền sẵn số tiền và nội dung</p>
                  </div>
                  <div className="flex flex-col justify-center"><span className="grid size-12 place-items-center rounded-xl bg-emerald-50 text-emerald-700"><Landmark className="size-6" /></span><h2 className="mt-4 text-xl font-semibold">Chuyển khoản ngân hàng</h2><p className="mt-2 text-sm font-medium leading-6 text-stone-500">Vui lòng chuyển đúng số tiền và nội dung để SePay tự động đối soát.</p><div className="mt-5 grid gap-3">{[
                    ['Ngân hàng', payment.bankName, 'bank'],
                    ['Số tài khoản', payment.bankAccountNo, 'account'],
                    ['Tên tài khoản', payment.bankAccountName, 'name'],
                    ['Nội dung', payment.transferContent, 'content'],
                  ].map(([label, value, key]) => <div key={key} className="flex items-center justify-between gap-3 rounded-lg bg-stone-50 px-4 py-3 ring-1 ring-stone-100"><div><p className="text-[11px] font-medium text-stone-400">{label}</p><p className="mt-1 text-sm font-bold text-emerald-950">{value}</p></div>{key !== 'bank' && key !== 'name' && <button className="grid size-9 shrink-0 place-items-center rounded-lg bg-white text-emerald-700 shadow-sm ring-1 ring-emerald-100" onClick={() => copyValue(key, String(value))} aria-label={`Sao chép ${label}`}>{copied === key ? <ClipboardCheck className="size-4" /> : <Copy className="size-4" />}</button>}</div>)}</div></div>
                </div>
              </section>
            ) : (
              <section className={classNames('rounded-2xl border bg-white p-7 text-center shadow-[0_18px_46px_rgba(24,65,44,0.10)]', payment.status === 'failed' ? 'border-red-100' : payment.status === 'paid' ? 'border-emerald-100' : 'border-stone-200')}>
                <span className={classNames('mx-auto grid size-20 place-items-center rounded-full', statusMeta.className)}><StatusIcon className="size-10" /></span>
                <h2 className="mt-5 text-2xl font-semibold">{statusMeta.label}</h2>
                <p className="mx-auto mt-2 max-w-lg text-sm font-medium leading-6 text-stone-500">{payment.status === 'failed' ? 'Ngân hàng chưa hoàn tất giao dịch. Bạn có thể thử lại với cùng booking.' : payment.status === 'expired' ? 'Thời gian giữ chỗ đã kết thúc. Bạn có thể thử lại nếu sân vẫn còn giữ chỗ, hoặc chọn lịch khác.' : 'SePay đã xác nhận giao dịch và Courtly đã giữ lịch sân cho bạn.'}</p>
                {retryError && <p className="mt-3 text-sm font-semibold text-red-600">{retryError}</p>}
                <div className="mt-6 flex flex-wrap justify-center gap-3">
                  {(payment.status === 'failed' || payment.status === 'expired') && <button className="flex h-11 items-center gap-2 rounded-lg bg-emerald-700 px-5 text-sm font-semibold text-white disabled:opacity-50" disabled={retrying} onClick={handleRetry}><RefreshCw className={classNames('size-4', retrying && 'animate-spin')} />{retrying ? 'Đang mở lại...' : 'Thử thanh toán lại'}</button>}
                  {booking?.venue?.slug && <a className="flex h-11 items-center rounded-lg border border-emerald-200 px-5 text-sm font-semibold text-emerald-700" href={`/san/${booking.venue.slug}#booking`}>Chọn lịch mới</a>}
                  {payment.status === 'paid' && <a className="flex h-11 items-center rounded-lg bg-emerald-700 px-5 text-sm font-semibold text-white" href={`/booking/success/${payment.bookingId}`}>Xem xác nhận đặt sân</a>}
                </div>
              </section>
            )}

            <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_12px_34px_rgba(24,65,44,0.07)]">
              <ProfileSectionTitle icon={ShieldCheck} eyebrow="Đối soát tự động" title="Sau khi bạn chuyển khoản" />
              <div className="mt-5 grid gap-4 sm:grid-cols-3">{[
                [ReceiptText, '1. Ngân hàng ghi nhận', 'Giao dịch được gửi đến SePay.'],
                [Zap, '2. SePay thông báo', 'Webhook chuyển dữ liệu về Courtly.'],
                [CheckCircle2, '3. Xác nhận lịch', 'Booking tự động chuyển sang đã xác nhận.'],
              ].map(([Icon, title, description]) => <div key={title} className="rounded-xl bg-emerald-50/60 p-4 ring-1 ring-emerald-100"><Icon className="size-5 text-emerald-700" /><p className="mt-3 text-sm font-semibold">{title}</p><p className="mt-1 text-xs font-medium leading-5 text-stone-500">{description}</p></div>)}</div>
            </section>
          </div>

          <aside className="grid gap-5 lg:sticky lg:top-20">
            <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_14px_38px_rgba(24,65,44,0.10)]">
              <ProfileSectionTitle icon={CreditCard} eyebrow="Giao dịch" title="Thông tin thanh toán" />
              <div className="mt-5 grid gap-3 text-sm font-medium text-stone-500">
                <p className="flex justify-between gap-4"><span>Mã booking</span><span className="font-semibold text-emerald-950">{payment.bookingCode}</span></p>
                {venueName && <p className="flex justify-between gap-4"><span>Sân</span><span className="max-w-[210px] text-right font-semibold text-emerald-950">{venueName}</span></p>}
                {booking?.startTime && <p className="flex justify-between gap-4"><span>Khung giờ</span><span className="text-right font-semibold text-emerald-950">{formatBookingTime(booking.startTime)} - {formatBookingTime(booking.endTime)}</span></p>}
                <p className="flex justify-between gap-4"><span>Phương thức</span><span className="font-semibold text-emerald-950">SePay / VietQR</span></p>
                {payment.providerTransactionId && <p className="flex justify-between gap-4"><span>Mã giao dịch</span><span className="max-w-[180px] truncate text-right font-semibold text-emerald-950">{payment.providerTransactionId}</span></p>}
              </div>
              <div className="mt-5 flex items-center justify-between border-t border-emerald-100 pt-5"><span className="text-sm font-semibold">Cần thanh toán</span><span className="text-2xl font-semibold text-emerald-700">{formatCurrency(payment.amount)}</span></div>
            </section>
            {isPending && (
              <section className="rounded-xl border border-amber-200 bg-amber-50 p-5">
                <p className="text-sm font-semibold text-amber-900">Lưu ý khi chuyển khoản</p>
                <p className="mt-2 text-xs font-medium leading-5 text-amber-800">Không sửa nội dung chuyển khoản. Màn hình tự cập nhật trong vài giây sau khi SePay báo tiền về.</p>
                <button className="mt-4 flex h-9 items-center gap-2 rounded-lg border border-amber-300 bg-white px-3 text-xs font-semibold text-amber-900" onClick={reload}><RefreshCw className="size-3.5" />Kiểm tra lại ngay</button>
              </section>
            )}
          </aside>
        </div>
      </section>
      <BottomNav activeLabel="Tài khoản" />
    </main>
  )
}


function BookingSuccessPage({ bookingId, user, onLogout }) {
  const { status, booking } = useBookingDetail(bookingId)

  if (!user) return <ProfileEmptyState />
  if (status === 'loading') {
    return (
      <main className="min-h-screen bg-[#f4f7f1] pb-24">
        <AppHeader user={user} onLogout={onLogout} />
        <section className="mx-auto max-w-[820px] px-4 py-10"><div className="h-64 animate-pulse rounded-2xl bg-emerald-100/60" /></section>
      </main>
    )
  }
  if (status === 'error') {
    return <main className="min-h-screen bg-[#f4f7f1] pb-24"><AppHeader user={user} onLogout={onLogout} /><section className="mx-auto max-w-[900px] px-4 py-10"><BookingEmptyState title="Không tìm thấy booking" description="Không thể hiển thị xác nhận cho booking này." actionLabel="Xem booking của tôi" actionHref="/bookings" /></section></main>
  }

  const venue = booking.venue
  const court = booking.court

  return (
    <main className="min-h-screen bg-[radial-gradient(circle_at_top,#d9f99d_0,transparent_26%),linear-gradient(145deg,#eaf7ef,#fbfcf8)] pb-24 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="mx-auto max-w-[820px] px-4 py-8 md:px-6 md:py-12">
        <PageBackLink href="/bookings" label="Về danh sách booking" />
        <section className="mt-7 overflow-hidden rounded-2xl border border-emerald-100 bg-white shadow-[0_24px_70px_rgba(24,65,44,0.13)]">
          <div className="bg-emerald-950 px-6 py-9 text-center text-white sm:px-10"><span className="mx-auto grid size-20 place-items-center rounded-full bg-lime-300 text-emerald-950 shadow-[0_0_0_10px_rgba(190,242,100,0.12)]"><Check className="size-10" strokeWidth={3} /></span><p className="mt-6 text-xs font-bold uppercase tracking-[0.18em] text-lime-300">2.1.40 · Đặt sân thành công</p><h1 className="mt-2 text-3xl font-semibold">Lịch sân của bạn đã được xác nhận</h1><p className="mx-auto mt-3 max-w-xl text-sm font-medium leading-6 text-emerald-100">Courtly đã nhận thanh toán qua SePay. Thông tin booking được lưu trong lịch sử đặt sân của bạn.</p></div>
          <div className="p-5 sm:p-8"><div className="flex flex-wrap items-start justify-between gap-4"><div><p className="text-xs font-medium text-stone-400">Mã booking</p><p className="mt-1 text-2xl font-semibold text-emerald-700">{booking.bookingCode}</p></div><BookingStatusBadge status={booking.status} /></div><div className="mt-6 grid gap-4 rounded-xl bg-emerald-50/70 p-5 ring-1 ring-emerald-100 sm:grid-cols-2"><div className="sm:col-span-2"><p className="text-xs font-medium text-stone-400">Địa điểm</p><p className="mt-1 text-lg font-semibold">{venue.name}</p><p className="mt-1 text-sm font-medium text-stone-500">{venue.address}</p></div>{[[court.name, 'Sân con'], [formatBookingDate(booking.startTime), 'Ngày chơi'], [`${formatBookingTime(booking.startTime)} - ${formatBookingTime(booking.endTime)}`, 'Khung giờ'], [formatCurrency(booking.totalAmount), 'Đã thanh toán']].map(([value, label]) => <div key={label} className="rounded-lg bg-white p-3.5 ring-1 ring-emerald-100"><p className="text-[11px] font-medium text-stone-400">{label}</p><p className="mt-1 text-sm font-semibold">{value}</p></div>)}</div><div className="mt-6 grid gap-3 sm:grid-cols-2"><a className="flex h-12 items-center justify-center gap-2 rounded-lg bg-emerald-700 text-sm font-semibold text-white" href={`/bookings/${booking.id}`}><ReceiptText className="size-4" />Xem chi tiết booking</a><a className="flex h-12 items-center justify-center gap-2 rounded-lg border border-emerald-200 text-sm font-semibold text-emerald-700" href="/"><Home className="size-4" />Về trang chủ</a></div></div>
        </section>
      </section>
      <BottomNav activeLabel="Tài khoản" />
    </main>
  )
}

function BookingDetailPage({ bookingId, user, onLogout }) {
  const { status, booking, error, notFound, setBooking, reload } = useBookingDetail(bookingId)
  const [showCancelModal, setShowCancelModal] = useState(false)
  const [cancelReason, setCancelReason] = useState('Thay đổi kế hoạch cá nhân')
  const [cancelling, setCancelling] = useState(false)
  const [cancelError, setCancelError] = useState('')

  if (!user) return <ProfileEmptyState />

  if (status === 'loading') {
    return (
      <main className="min-h-screen bg-[#f4f7f1] pb-24">
        <AppHeader user={user} onLogout={onLogout} />
        <section className="mx-auto max-w-[1100px] px-4 py-10">
          <div className="grid gap-4">
            <div className="h-24 animate-pulse rounded-xl bg-emerald-100/70" />
            <div className="h-56 animate-pulse rounded-xl bg-emerald-100/50" />
          </div>
        </section>
      </main>
    )
  }

  if (status === 'error') {
    return (
      <main className="min-h-screen bg-[#f4f7f1] pb-24">
        <AppHeader user={user} onLogout={onLogout} />
        <section className="mx-auto max-w-[900px] px-4 py-10">
          <BookingEmptyState
            title={notFound ? 'Không tìm thấy booking' : 'Không tải được booking'}
            description={notFound ? 'Booking có thể đã bị xoá hoặc không thuộc về tài khoản này.' : error}
            actionLabel="Xem lịch sử đặt sân"
            actionHref="/bookings"
          />
          {!notFound && (
            <div className="mt-5 text-center">
              <button className="h-11 rounded-lg bg-emerald-700 px-6 text-sm font-semibold text-white" onClick={reload}>
                Thử lại
              </button>
            </div>
          )}
        </section>
      </main>
    )
  }

  const venue = booking.venue
  const court = booking.court
  // Server quyết định có huỷ được không, giao diện không tự suy từ trạng thái.
  const canCancel = booking.cancellable

  async function handleCancel() {
    if (cancelling) return
    setCancelling(true)
    setCancelError('')
    try {
      setBooking(await cancelBookingApi(bookingId, cancelReason))
      setShowCancelModal(false)
    } catch (submitError) {
      setCancelError(submitError?.message ?? 'Không huỷ được, vui lòng thử lại.')
    } finally {
      setCancelling(false)
    }
  }


  const directionsHref = `https://www.google.com/maps/dir/?api=1&destination=${venue.latitude},${venue.longitude}`
  return (
    <main className="min-h-screen bg-[linear-gradient(145deg,#eef8f2,#f9faf6)] pb-24 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="mx-auto max-w-[1100px] px-4 py-7 md:px-6 md:py-10">
        <PageBackLink href="/bookings" label="Về lịch sử đặt sân" />
        <div className="mt-6 flex flex-wrap items-start justify-between gap-4"><div><p className="text-xs font-bold uppercase tracking-[0.15em] text-emerald-600">2.1.29–2.1.31 · Chi tiết booking</p><h1 className="mt-2 text-3xl font-semibold">{booking.bookingCode}</h1><p className="mt-2 text-sm font-medium text-stone-500">Tạo lúc {new Date(booking.createdAt).toLocaleString('vi-VN')}</p></div><BookingStatusBadge status={booking.status} /></div>

        <div className="mt-7 grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
          <div className="grid gap-6">
            <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_12px_34px_rgba(24,65,44,0.08)]">
              <ProfileSectionTitle icon={History} eyebrow="Trạng thái" title="Tiến trình đặt sân" />
              <div className="mt-6 grid gap-0">{booking.history.map((item, index) => <div key={`${item.status}-${item.createdAt}`} className="grid grid-cols-[28px_1fr] gap-3"><div className="flex flex-col items-center"><span className="grid size-7 place-items-center rounded-full bg-emerald-700 text-white"><Check className="size-4" /></span>{index < booking.history.length - 1 && <span className="min-h-10 w-px flex-1 bg-emerald-200" />}</div><div className="pb-6"><p className="text-sm font-semibold">{item.label}</p>{item.reason && <p className="mt-1 text-xs font-medium text-stone-500">{item.reason}</p>}<p className="mt-1 text-xs font-medium text-stone-400">{new Date(item.createdAt).toLocaleString('vi-VN')}{item.bySystem && ' · hệ thống'}</p></div></div>)}</div>
            </section>

            <section className="overflow-hidden rounded-xl border border-emerald-100 bg-white shadow-[0_12px_34px_rgba(24,65,44,0.08)]">
              <img className="h-48 w-full object-cover" src={venue.image} alt={venue.name} />
              <div className="p-5"><h2 className="text-xl font-semibold">{venue.name}</h2><p className="mt-2 flex gap-2 text-sm font-medium leading-6 text-stone-500"><MapPin className="mt-0.5 size-4 shrink-0 text-emerald-600" />{venue.address}</p><div className="mt-5 grid gap-3 sm:grid-cols-3">{[[court.name, 'Sân con'], [formatBookingDate(booking.startTime), 'Ngày chơi'], [`${formatBookingTime(booking.startTime)} - ${formatBookingTime(booking.endTime)}`, `${booking.durationMinutes} phút`]].map(([value, label]) => <div key={label} className="rounded-lg bg-emerald-50/70 p-3 ring-1 ring-emerald-100"><p className="text-xs font-medium text-stone-400">{label}</p><p className="mt-1 text-sm font-semibold">{value}</p></div>)}</div><div className="mt-5 flex flex-wrap gap-3"><a className="flex h-10 items-center gap-2 rounded-lg bg-emerald-700 px-4 text-xs font-semibold text-white" href={directionsHref} target="_blank" rel="noreferrer"><Navigation className="size-4" />Chỉ đường</a><a className="flex h-10 items-center gap-2 rounded-lg border border-emerald-100 px-4 text-xs font-semibold text-emerald-700" href={`tel:${venue.phone}`}><Phone className="size-4" />Gọi sân</a></div></div>
            </section>

            {booking.note && <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_12px_34px_rgba(24,65,44,0.08)]"><ProfileSectionTitle icon={FileText} eyebrow="Ghi chú" title="Yêu cầu với sân" /><p className="mt-4 text-sm font-medium leading-6 text-stone-600">{booking.note}</p></section>}
          </div>

          <aside className="grid gap-6 lg:sticky lg:top-20">
            <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_12px_34px_rgba(24,65,44,0.08)]"><ProfileSectionTitle icon={CreditCard} eyebrow="Thanh toán" title="Chi tiết chi phí" /><div className="mt-5 grid gap-3 text-sm font-medium text-stone-500"><p className="flex justify-between"><span>Tiền sân</span><span className="font-semibold text-emerald-950">{formatCurrency(booking.price)}</span></p><p className="flex justify-between border-t border-emerald-100 pt-3"><span className="font-semibold text-emerald-950">Tổng cộng</span><span className="text-lg font-semibold text-emerald-700">{formatCurrency(booking.totalAmount)}</span></p></div>{booking.status === 'pending_payment' && booking.expiresAt && (
              <div className="mt-4 rounded-lg bg-amber-50 p-3 ring-1 ring-amber-100">
                <p className="text-xs font-semibold text-amber-800">Chờ thanh toán</p>
                <p className="mt-1 text-xs font-medium leading-5 text-amber-700">
                  Giữ chỗ đến {new Date(booking.expiresAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}.
                  Quá hạn thì đơn tự huỷ và khung giờ mở lại cho người khác.
                </p>
                {/* TODO 2.1.34: nối màn hình thanh toán SePay. */}
              </div>
            )}</section>
            {canCancel && <section className="rounded-xl border border-red-100 bg-white p-5"><p className="text-sm font-semibold text-red-700">Bạn cần thay đổi kế hoạch?</p><p className="mt-2 text-xs font-medium leading-5 text-stone-500">Việc hoàn tiền phụ thuộc thời điểm hủy và chính sách của sân.</p><button className="mt-4 h-11 w-full rounded-lg border border-red-200 text-sm font-semibold text-red-600" onClick={() => setShowCancelModal(true)}>Hủy đặt sân</button></section>}
            {booking.status === 'cancelled' && <section className="rounded-xl border border-red-100 bg-red-50 p-5"><p className="text-sm font-semibold text-red-700">Booking đã hủy</p><p className="mt-2 text-xs font-medium leading-5 text-red-600">Lý do: {booking.cancellationReason}</p></section>}
          </aside>
        </div>
      </section>

      {showCancelModal && <div className="fixed inset-0 z-[1200] grid place-items-center bg-emerald-950/50 p-4 backdrop-blur-sm" onMouseDown={() => setShowCancelModal(false)}><section className="w-full max-w-md rounded-xl bg-white p-6 shadow-2xl" onMouseDown={(event) => event.stopPropagation()}><span className="grid size-12 place-items-center rounded-full bg-red-50 text-red-600"><CircleAlert className="size-6" /></span><h2 className="mt-5 text-xl font-semibold">Xác nhận hủy đặt sân?</h2><p className="mt-2 text-sm font-medium leading-6 text-stone-500">Thao tác này sẽ cập nhật trạng thái booking và không thể hoàn tác.</p><label className="mt-5 block"><span className="text-sm font-semibold">Lý do hủy</span><select className="mt-2.5 h-12 w-full rounded-lg border border-stone-200 px-3 text-sm outline-none" value={cancelReason} onChange={(event) => setCancelReason(event.target.value)}><option>Thay đổi kế hoạch cá nhân</option><option>Đặt nhầm ngày hoặc giờ</option><option>Muốn chọn sân khác</option><option>Lý do khác</option></select></label>{cancelError && <p className="mt-4 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-xs font-medium text-red-700">{cancelError}</p>}<div className="mt-6 grid grid-cols-2 gap-3"><button type="button" className="h-11 rounded-lg border border-stone-200 text-sm font-semibold text-stone-600" onClick={() => setShowCancelModal(false)}>Giữ booking</button><button type="button" disabled={cancelling} className="h-11 rounded-lg bg-red-600 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60" onClick={handleCancel}>{cancelling ? 'Đang huỷ...' : 'Xác nhận hủy'}</button></div></section></div>}
      <BottomNav activeLabel="Tài khoản" />
    </main>
  )
}

function BookingsPage({ user, onLogout }) {
  const [activeTab, setActiveTab] = useState('upcoming')
  const [query, setQuery] = useState('')

  // Lọc theo tab và tìm kiếm đều do server làm (2.1.32).
  const { status, bookings, error, reload } = useBookings({ tab: activeTab, q: query })

  if (!user) return <ProfileEmptyState />

  const tabs = [
    { id: 'upcoming', label: 'Sắp tới' },
    { id: 'pending', label: 'Chờ thanh toán' },
    { id: 'completed', label: 'Đã hoàn thành' },
    { id: 'cancelled', label: 'Đã hủy' },
  ]
  return (
    <main className="min-h-screen bg-[linear-gradient(145deg,#eef8f2,#f9faf6)] pb-24 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="mx-auto max-w-[1100px] px-4 py-7 md:px-6 md:py-10">
        <div className="flex flex-wrap items-end justify-between gap-4"><div><PageBackLink href="/profile" label="Về hồ sơ" /><p className="mt-6 text-xs font-bold uppercase tracking-[0.15em] text-emerald-600">2.1.32 · Lịch sử đặt sân</p><h1 className="mt-2 text-3xl font-semibold">Booking của tôi</h1><p className="mt-2 text-sm font-medium text-stone-500">Theo dõi các lịch sắp tới và toàn bộ lịch sử đặt sân.</p></div><a className="flex h-11 items-center gap-2 rounded-lg bg-lime-300 px-5 text-sm font-semibold text-emerald-950" href="/"><Plus className="size-4" />Đặt sân mới</a></div>

        <div className="mt-7 rounded-xl border border-emerald-100 bg-white p-3 shadow-[0_12px_34px_rgba(24,65,44,0.08)]"><div className="flex gap-2 overflow-x-auto pb-1">{tabs.map((tab) => { return <button key={tab.id} className={classNames('flex h-11 shrink-0 items-center gap-2 rounded-lg px-4 text-sm font-semibold', activeTab === tab.id ? 'bg-emerald-700 text-white' : 'bg-stone-50 text-stone-500')} onClick={() => setActiveTab(tab.id)}>{tab.label}</button> })}</div><label className="mt-3 flex h-11 items-center gap-3 rounded-lg border border-stone-200 px-4"><Search className="size-4 text-emerald-700" /><input className="min-w-0 flex-1 text-sm outline-none placeholder:text-stone-400" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Tìm theo mã booking hoặc tên sân" />{query && <button onClick={() => setQuery('')} aria-label="Xóa tìm kiếm"><X className="size-4 text-stone-400" /></button>}</label></div>

        {status === 'loading' && (
          <div className="mt-6 grid gap-4">
            {[0, 1].map((index) => <div key={index} className="h-44 animate-pulse rounded-xl bg-emerald-100/60" />)}
          </div>
        )}

        {status === 'error' && (
          <div className="mt-6 rounded-xl border border-red-200 bg-red-50 p-8 text-center">
            <p className="font-medium text-red-700">{error}</p>
            <button className="mt-4 h-11 rounded-lg bg-emerald-700 px-6 text-sm font-semibold text-white" onClick={reload}>
              Thử lại
            </button>
          </div>
        )}

        {status === 'empty' && (
          <div className="mt-6">
            <BookingEmptyState
              title="Chưa có booking phù hợp"
              description={query ? 'Không tìm thấy booking khớp từ khoá này.' : 'Không có booking trong nhóm trạng thái này.'}
              actionLabel="Đặt sân ngay"
              actionHref="/"
            />
          </div>
        )}

        {status === 'success' && (
          <div className="mt-6 grid gap-4">{bookings.map((booking) => (
            <article key={booking.id} className="grid overflow-hidden rounded-xl border border-emerald-100 bg-white shadow-[0_10px_28px_rgba(24,65,44,0.07)] md:grid-cols-[190px_minmax(0,1fr)_180px]">
              {booking.venueImage
                ? <img className="h-44 w-full object-cover md:h-full" src={booking.venueImage} alt={booking.venueName} />
                : <div className="h-44 w-full bg-emerald-50 md:h-full" />}
              <div className="p-5">
                <div className="flex flex-wrap items-center gap-2"><BookingStatusBadge status={booking.status} /><span className="text-xs font-bold text-stone-400">{booking.bookingCode}</span></div>
                <h2 className="mt-3 text-lg font-semibold">{booking.venueName}</h2>
                <p className="mt-2 text-sm font-medium text-stone-500">{booking.courtName} · {formatBookingDate(booking.startTime)}</p>
                <p className="mt-1 text-sm font-semibold text-emerald-700">{formatBookingTime(booking.startTime)} - {formatBookingTime(booking.endTime)} · {booking.durationMinutes} phút</p>
              </div>
              <div className="flex flex-row items-center justify-between gap-3 border-t border-emerald-100 bg-emerald-50/50 p-5 md:flex-col md:items-end md:justify-center md:border-l md:border-t-0">
                <div className="md:text-right"><p className="text-xs font-medium text-stone-400">Tổng tiền</p><p className="mt-1 text-lg font-semibold text-emerald-700">{formatCurrency(booking.totalAmount)}</p></div>
                <a className="flex h-10 items-center gap-2 rounded-lg bg-emerald-700 px-4 text-xs font-semibold text-white" href={`/bookings/${booking.id}`}>Xem chi tiết<ChevronRight className="size-4" /></a>
              </div>
            </article>
          ))}</div>
        )}
      </section>
      <BottomNav activeLabel="Tài khoản" />
    </main>
  )
}

function VenueFilterPanel({ filters, districts, onChange, onReset, onClose }) {
  const priceOptions = [
    { value: 100000, label: 'Đến 100.000đ' },
    { value: 120000, label: 'Đến 120.000đ' },
    { value: 150000, label: 'Đến 150.000đ' },
    { value: 200000, label: 'Không giới hạn' },
  ]

  return (
    <div className="fixed inset-0 z-[1000] flex items-end justify-center bg-emerald-950/45 p-0 backdrop-blur-[2px] sm:items-center sm:p-5" onMouseDown={onClose}>
      <section className="max-h-[90svh] w-full overflow-y-auto rounded-t-2xl bg-white shadow-[0_28px_80px_rgba(4,47,32,0.28)] sm:max-w-[620px] sm:rounded-2xl" onMouseDown={(event) => event.stopPropagation()}>
        <div className="sticky top-0 z-10 flex items-center justify-between border-b border-emerald-100 bg-white px-5 py-4 sm:px-6">
          <div><p className="text-xs font-bold uppercase tracking-[0.14em] text-emerald-600">Tìm chính xác hơn</p><h2 className="mt-1 text-xl font-semibold text-emerald-950">Bộ lọc sân</h2></div>
          <button className="grid size-10 place-items-center rounded-full bg-stone-100 text-stone-500" onClick={onClose} aria-label="Đóng bộ lọc"><X className="size-5" /></button>
        </div>

        <div className="grid gap-7 p-5 sm:p-6">
          <div>
            <div className="flex items-center gap-2"><MapPin className="size-4 text-emerald-700" /><p className="text-sm font-semibold text-emerald-950">Khu vực</p></div>
            <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-3">
              {['all', ...districts].map((district) => (
                <button key={district} className={classNames('min-h-11 rounded-lg border px-3 text-sm font-semibold', filters.district === district ? 'border-emerald-600 bg-emerald-700 text-white' : 'border-stone-200 bg-white text-stone-600')} onClick={() => onChange({ ...filters, district })}>
                  {district === 'all' ? 'Tất cả khu vực' : district}
                </button>
              ))}
            </div>
          </div>

          <div>
            <div className="flex items-center justify-between"><span className="flex items-center gap-2 text-sm font-semibold text-emerald-950"><LocateFixed className="size-4 text-emerald-700" />Khoảng cách tối đa</span><span className="rounded-full bg-emerald-50 px-3 py-1 text-sm font-bold text-emerald-700">{filters.maxDistance} km</span></div>
            <input className="mt-4 w-full accent-emerald-600" type="range" min="2" max="15" step="1" value={filters.maxDistance} onChange={(event) => onChange({ ...filters, maxDistance: Number(event.target.value) })} />
            <div className="mt-1 flex justify-between text-[11px] font-medium text-stone-400"><span>2 km</span><span>15 km</span></div>
          </div>

          <div>
            <div className="flex items-center gap-2"><Tag className="size-4 text-emerald-700" /><p className="text-sm font-semibold text-emerald-950">Mức giá mỗi giờ</p></div>
            <div className="mt-3 grid grid-cols-2 gap-2">
              {priceOptions.map((option) => <button key={option.value} className={classNames('h-11 rounded-lg border text-sm font-semibold', filters.maxPrice === option.value ? 'border-emerald-500 bg-emerald-50 text-emerald-700 ring-1 ring-emerald-400' : 'border-stone-200 text-stone-600')} onClick={() => onChange({ ...filters, maxPrice: option.value })}>{option.label}</button>)}
            </div>
          </div>

          <button className={classNames('flex items-center gap-4 rounded-xl border p-4 text-left', filters.availableOnly ? 'border-emerald-500 bg-emerald-50 ring-1 ring-emerald-400' : 'border-stone-200')} onClick={() => onChange({ ...filters, availableOnly: !filters.availableOnly })}>
            <span className={classNames('grid size-11 place-items-center rounded-lg', filters.availableOnly ? 'bg-emerald-700 text-white' : 'bg-stone-100 text-stone-500')}><CalendarDays className="size-5" /></span>
            <span className="min-w-0 flex-1"><span className="block text-sm font-semibold text-emerald-950">Chỉ hiện sân còn trống</span><span className="mt-1 block text-xs font-medium text-stone-500">Ẩn các sân không còn lịch có thể đặt.</span></span>
            <span className={classNames('relative h-7 w-12 rounded-full transition', filters.availableOnly ? 'bg-emerald-600' : 'bg-stone-200')}><span className={classNames('absolute top-1 size-5 rounded-full bg-white shadow transition', filters.availableOnly ? 'left-6' : 'left-1')} /></span>
          </button>
        </div>

        <div className="sticky bottom-0 flex gap-3 border-t border-emerald-100 bg-white p-4 sm:px-6">
          <button className="h-12 flex-1 rounded-lg border border-stone-200 text-sm font-semibold text-stone-600" onClick={onReset}>Đặt lại</button>
          <button className="h-12 flex-[1.5] rounded-lg bg-emerald-700 text-sm font-semibold text-white" onClick={onClose}>Xem kết quả</button>
        </div>
      </section>
    </div>
  )
}

function SearchDock({ query, setQuery, filters, onFiltersChange, districts, onNearMe, nearMeActive, locating }) {
  const [showFilters, setShowFilters] = useState(false)
  const activeFilterCount = [filters.district !== 'all', filters.maxDistance < 15, filters.maxPrice < 200000, filters.availableOnly, filters.highRatingOnly].filter(Boolean).length

  function toggleQuickFilter(filter) {
    // "Gần tôi" cần toạ độ thật, nên do trang cha xử lý (xin quyền định vị).
    if (filter === 'Gần tôi') onNearMe?.()
    if (filter === 'Còn sân hôm nay') onFiltersChange({ ...filters, availableOnly: !filters.availableOnly })
    if (filter === 'Đánh giá cao') onFiltersChange({ ...filters, highRatingOnly: !filters.highRatingOnly })
    if (filter === 'Giá tốt') onFiltersChange({ ...filters, maxPrice: filters.maxPrice === 120000 ? 200000 : 120000 })
  }

  function quickFilterActive(filter) {
    if (filter === 'Gần tôi') return Boolean(nearMeActive)
    if (filter === 'Còn sân hôm nay') return filters.availableOnly
    if (filter === 'Đánh giá cao') return filters.highRatingOnly
    return filters.maxPrice === 120000
  }

  return (
    <section id="search" className="relative z-30 mx-auto -mt-12 max-w-[1680px] px-4 md:px-6 lg:px-8">
      <div className="rounded-xl border border-emerald-100 bg-white/96 p-4 shadow-[0_18px_46px_rgba(15,57,38,0.14)] backdrop-blur">
        <div className="grid gap-3 lg:grid-cols-[1fr_auto_auto_auto] lg:items-center">
          <label className="flex h-[58px] min-w-0 items-center gap-3 rounded-lg bg-white px-5 text-stone-500 ring-1 ring-stone-200 transition focus-within:ring-emerald-300">
            <Search className="size-5 shrink-0 text-emerald-900" />
            <input
              className="min-w-0 flex-1 bg-transparent text-[15px] font-medium text-stone-800 outline-none placeholder:text-stone-400"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Tìm tên sân, quận hoặc địa chỉ"
            />
            {query && (
              <button type="button" className="grid size-8 shrink-0 place-items-center rounded-full text-stone-400 hover:bg-stone-100" onClick={() => setQuery('')} aria-label="Xóa từ khóa tìm kiếm">
                <X className="size-4" />
              </button>
            )}
          </label>

          <button className="relative flex h-[58px] items-center justify-center gap-2 rounded-lg border border-stone-200 bg-white px-6 text-sm font-semibold text-emerald-900" onClick={() => setShowFilters(true)}>
            <Settings2 className="size-5" />
            Bộ lọc
            {activeFilterCount > 0 && <span className="grid size-5 place-items-center rounded-full bg-emerald-700 text-[10px] font-bold text-white">{activeFilterCount}</span>}
          </button>
          <a href="/map" className="flex h-[58px] items-center justify-center gap-2 rounded-lg border border-stone-200 bg-white px-6 text-sm font-semibold text-emerald-900">
            <Map className="size-5" />
            Bản đồ
          </a>
          <button className="flex h-[58px] items-center justify-center gap-3 rounded-lg bg-lime-300 px-8 text-sm font-semibold text-emerald-950 shadow-[0_12px_26px_rgba(163,230,53,0.26)]">
            <Search className="size-5" />
            Tìm sân
          </button>
        </div>

        <div className="mt-3 flex gap-2 overflow-x-auto pb-1">
          {quickFilters.map((filter, index) => (
            <button
              key={filter}
              className={classNames(
                'inline-flex shrink-0 items-center gap-2 rounded-lg px-5 py-3 text-sm font-semibold',
                quickFilterActive(filter)
                  ? 'bg-emerald-800 text-white shadow-[0_8px_18px_rgba(6,78,59,0.18)]'
                  : 'bg-emerald-50 text-emerald-900 ring-1 ring-emerald-100',
              )}
              onClick={() => toggleQuickFilter(filter)}
              disabled={index === 0 && locating}
            >
              {index === 0 && <MapPin className="size-4" />}
              {index === 1 && <Clock3 className="size-4" />}
              {index === 2 && <Star className="size-4" />}
              {index === 3 && <Tag className="size-4" />}
              {index === 0 && locating ? 'Đang định vị...' : filter}
            </button>
          ))}
        </div>
      </div>
      {showFilters && <VenueFilterPanel filters={filters} districts={districts} onChange={onFiltersChange} onReset={() => onFiltersChange(DEFAULT_VENUE_FILTERS)} onClose={() => setShowFilters(false)} />}
    </section>
  )
}

function VenueCard({ venue }) {
  function openDetail() {
    window.location.href = `/san/${venue.id}`
  }

  return (
    <article
      className="group overflow-hidden rounded-xl border border-emerald-100 bg-white shadow-[0_10px_28px_rgba(34,52,40,0.08)] transition hover:-translate-y-1 hover:shadow-[0_16px_34px_rgba(34,52,40,0.12)]"
      onClick={openDetail}
      role="button"
      tabIndex={0}
      onKeyDown={(event) => {
        if (event.key === 'Enter') openDetail()
      }}
    >
      <div className="relative h-36 overflow-hidden bg-emerald-950">
        <img className="h-full w-full object-cover transition duration-500 group-hover:scale-105" src={venue.image} alt={venue.name} />
        <div className="absolute inset-0 bg-gradient-to-t from-emerald-950/86 via-emerald-950/42 to-emerald-950/12" />
        <div className="absolute left-3 top-3 flex items-center gap-2">
          <span className="inline-flex items-center gap-1 rounded-md bg-white/95 px-3 py-1 text-sm font-semibold text-amber-700">
            <Star className="size-4 fill-amber-400 text-amber-400" />
            {venue.rating}
          </span>
          <span className="rounded-md bg-emerald-500 px-3 py-1 text-sm font-semibold text-white">
            Đón ngày
          </span>
        </div>
        <div className="absolute right-3 top-3 flex gap-2">
          <button className="grid size-10 place-items-center rounded-md bg-white text-emerald-800 shadow-md" onClick={(event) => event.stopPropagation()}>
            <Heart className="size-5" />
          </button>
          <button className="grid size-10 place-items-center rounded-md bg-white text-emerald-800 shadow-md" onClick={(event) => event.stopPropagation()}>
            <LocateFixed className="size-5" />
          </button>
        </div>
      </div>

      <div className="p-4">
        <div className="flex items-start gap-3">
          <div className="grid size-12 shrink-0 place-items-center rounded-lg bg-emerald-50 text-emerald-700 ring-1 ring-emerald-100">
            <MapPin className="size-6" />
          </div>
          <div className="min-w-0 flex-1">
            <h3 className="truncate text-lg font-semibold text-emerald-950">{venue.name}</h3>
            <p className="mt-1 line-clamp-1 text-sm font-semibold text-stone-500">
              {venue.distanceKm != null && <span className="font-bold text-red-500">({venue.distanceKm}km)</span>} {venue.address}
            </p>
          </div>
          <button className="grid size-9 shrink-0 place-items-center rounded-full bg-lime-200 text-emerald-800" onClick={openDetail}>
            <Navigation className="size-4" />
          </button>
        </div>

        <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-2 text-sm font-medium text-stone-500">
          <span className="inline-flex items-center gap-1">
            <Clock3 className="size-4" />
            {venue.openHours}
          </span>
          <span>{venue.courtCount} sân</span>
          <span>{venue.priceLabel}</span>
        </div>

        <div className="mt-4 flex items-center justify-between gap-3">
          <p className="rounded-lg bg-emerald-50 px-3 py-2 text-sm font-bold text-emerald-800">
            {venue.openHours}
          </p>
          <button className="rounded-lg bg-lime-300 px-4 py-3 text-sm font-semibold text-emerald-950 shadow-[0_8px_18px_rgba(163,230,53,0.24)]" onClick={openDetail}>
            ĐẶT LỊCH
          </button>
        </div>
      </div>
    </article>
  )
}

function SectionTitle({ eyebrow, title, action = 'Xem tất cả', icon: Icon, href }) {
  const actionContent = (
    <>
      {action}
      <ChevronRight className="size-4" />
    </>
  )

  return (
    <div className="flex flex-wrap items-end justify-between gap-3">
      <div>
        <p className="inline-flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.16em] text-emerald-600">
          {Icon && <Icon className="size-3.5" />}
          {eyebrow}
        </p>
        <h2 className="mt-1 text-xl font-semibold tracking-normal text-emerald-950 md:text-2xl">
          {title}
        </h2>
      </div>
      {href ? (
        <a
          className="inline-flex items-center gap-2 rounded-md bg-emerald-50 px-4 py-2.5 text-xs font-semibold text-emerald-700 shadow-sm ring-1 ring-emerald-100"
          href={href}
        >
          {actionContent}
        </a>
      ) : (
        <button className="inline-flex items-center gap-2 rounded-md bg-emerald-50 px-4 py-2.5 text-xs font-semibold text-emerald-700 shadow-sm ring-1 ring-emerald-100">
          {actionContent}
        </button>
      )}
    </div>
  )
}

function PromoStrip({ promotions = [] }) {
  if (!promotions.length) return null

  const promoMeta = {
    green: {
      wrap: 'bg-emerald-900 text-white',
      badge: 'bg-white/16 text-emerald-50',
      button: 'bg-white text-emerald-950',
      icon: Tag,
      image: 'https://images.unsplash.com/photo-1626224583764-f87db24ac4ea?auto=format&fit=crop&w=900&q=85',
    },
    gold: {
      wrap: 'bg-gradient-to-br from-amber-200 via-amber-300 to-yellow-400 text-emerald-950',
      badge: 'bg-white/42 text-emerald-950',
      button: 'bg-amber-400 text-emerald-950',
      icon: Zap,
      image: 'https://images.unsplash.com/photo-1613918431703-aa50889e3be2?auto=format&fit=crop&w=900&q=85',
    },
    dark: {
      wrap: 'bg-emerald-950 text-white',
      badge: 'bg-white/16 text-emerald-50',
      button: 'bg-white text-emerald-950',
      icon: Umbrella,
      image: 'https://images.unsplash.com/photo-1626224583764-f87db24ac4ea?auto=format&fit=crop&w=900&q=85',
    },
  }

  return (
    <section className="mx-auto mt-10 max-w-[1680px] px-4 md:px-6 lg:px-8">
      <SectionTitle eyebrow="Ưu đãi cho người chơi" title="Đặt sân lợi hơn" action="Xem ưu đãi" icon={Zap} />
      <div className="mt-5 grid gap-4 md:grid-cols-3">
        {promotions.map((promo) => {
          const meta = promoMeta[promo.tone] ?? promoMeta.green
          const PromoIcon = meta.icon
          return (
          <article
            key={promo.id}
            className={classNames(
              'relative min-h-[210px] overflow-hidden rounded-lg p-6 shadow-[0_14px_34px_rgba(34,52,40,0.12)]',
              meta.wrap,
            )}
          >
            <img
              className="absolute inset-y-0 right-0 h-full w-[48%] object-cover opacity-78"
              src={meta.image}
              alt={promo.title}
            />
            <div className="absolute inset-0 bg-[linear-gradient(110deg,rgba(0,95,61,0.92)_0_48%,rgba(0,95,61,0.20)_48%_100%)] mix-blend-multiply" />
            {promo.tone === 'gold' && (
              <div className="absolute inset-0 bg-[linear-gradient(110deg,rgba(255,229,112,0.95)_0_45%,rgba(255,229,112,0.52)_45%_63%,transparent_63%)] mix-blend-normal" />
            )}
            <div className="absolute -left-14 -bottom-16 size-56 rounded-full bg-white/10" />
            <div className="absolute right-5 top-5 grid size-10 place-items-center rounded-full bg-white/82 text-emerald-900 shadow-sm">
              <PromoIcon className="size-5" />
            </div>
            <div className="relative z-10">
              <span className={classNames('inline-flex items-center gap-2 rounded-lg px-3 py-2 text-xs font-semibold uppercase tracking-[0.08em]', meta.badge)}>
                <Clock3 className="size-4" />
                {promo.tag}
              </span>
              <h3 className="mt-5 max-w-[310px] text-2xl font-semibold leading-tight tracking-normal">
                {promo.title}
              </h3>
              <p className="mt-3 max-w-[300px] text-base font-medium leading-6 opacity-88">
                {promo.tone === 'green' && 'Chơi khỏe, bắt đầu ngày mới năng lượng với ưu đãi đặc biệt.'}
                {promo.tone === 'gold' && 'Ưu đãi đặc biệt cho các khung giờ buổi tối hôm nay.'}
                {promo.tone === 'dark' && 'Không lo nắng mưa, thoải mái chơi mọi lúc.'}
              </p>
              <button className={classNames('mt-5 inline-flex items-center gap-2 rounded-lg px-5 py-3 text-sm font-semibold shadow-sm', meta.button)}>
                {promo.cta}
                <ChevronRight className="size-4" />
              </button>
            </div>
          </article>
          )
        })}
      </div>
    </section>
  )
}

function AreaScroller({ areas = [], onSelect }) {
  if (!areas.length) return null

  return (
    <section className="mx-auto mt-9 max-w-[1680px] px-4 md:px-6 lg:px-8">
      <SectionTitle eyebrow="Khu vực hay chơi" title="Chọn nhanh theo quận" action="Mở bản đồ" href="/map" icon={MapPin} />
      <div className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
        {areas.map((area, index) => (
          <button
            key={area.name}
            type="button"
            onClick={() => onSelect?.(area.name)}
            className="grid grid-cols-[56px_1fr_auto] items-center gap-4 rounded-lg border border-emerald-100 bg-white/92 p-4 text-left shadow-[0_10px_28px_rgba(34,52,40,0.06)] hover:border-emerald-300"
          >
            <span className="grid size-14 place-items-center overflow-hidden rounded-full bg-emerald-50 text-emerald-700 ring-1 ring-emerald-100">
              <img
                className="h-full w-full object-cover"
                src={`https://images.unsplash.com/photo-${[
                  '1583417319070-4a69db38a482',
                  '1500530855697-b586d89ba3ee',
                  '1528127269322-539801943592',
                  '1518005020951-eccb494ad742',
                  '1506744038136-46273834b3fb',
                ][index % 5]}?auto=format&fit=crop&w=160&q=80`}
                alt={area.name}
              />
            </span>
            <span className="min-w-0">
              <span className="block truncate text-base font-semibold text-emerald-950">{area.name}</span>
              <span className="mt-1 block text-sm font-semibold text-emerald-600">{area.count}</span>
            </span>
            <ChevronRight className="size-5 text-emerald-600" />
          </button>
        ))}
      </div>
    </section>
  )
}

function CompactVenueList({ venues = [] }) {
  const compactVenues = venues.slice(3, 6)
  if (!compactVenues.length) return null

  return (
    <section className="mx-auto mt-9 max-w-[1680px] px-4 md:px-6 lg:px-8">
      <SectionTitle eyebrow="Lịch mới cập nhật" title="Sân vừa mở khung giờ" icon={Flame} />
      <div className="mt-5 grid gap-4 lg:grid-cols-3">
        {compactVenues.map((venue) => (
          <article
            key={venue.id}
            className="grid min-h-[170px] grid-cols-[210px_minmax(0,1fr)] gap-4 rounded-lg border border-emerald-100 bg-white p-3 shadow-[0_10px_28px_rgba(34,52,40,0.06)] max-xl:grid-cols-[170px_minmax(0,1fr)] max-sm:grid-cols-1"
          >
            <div className="relative min-h-[146px] overflow-hidden rounded-lg">
              <img className="absolute inset-0 h-full w-full object-cover" src={venue.image} alt={venue.name} />
              <span className="absolute left-3 top-3 rounded-md bg-emerald-500 px-3 py-1 text-xs font-semibold text-white">
                Mới
              </span>
            </div>
            <div className="flex min-w-0 flex-col justify-between py-1">
              <div className="min-w-0">
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <span className="inline-flex items-center gap-1 rounded-md bg-amber-50 px-2 py-1 text-xs font-bold text-amber-700">
                    <Star className="size-3 fill-amber-400 text-amber-400" />
                    {venue.rating}
                  </span>
                  <span className="inline-flex items-center gap-1 rounded-md bg-emerald-50 px-2 py-1 text-xs font-bold text-emerald-700">
                    <Clock3 className="size-3" />
                    {venue.openHours}
                  </span>
                </div>
                <button className="grid size-9 place-items-center rounded-full bg-emerald-50 text-emerald-700">
                  <Heart className="size-4" />
                </button>
              </div>
              <h3 className="mt-3 truncate text-base font-semibold text-emerald-950">{venue.name}</h3>
              <p className="mt-2 flex min-w-0 items-center gap-1 text-sm font-medium text-emerald-700">
                <MapPin className="size-4" />
                <span className="truncate">{venue.district}</span>
              </p>
              <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-sm font-medium text-emerald-700/80">
                <span className="inline-flex items-center gap-1">
                  <Users className="size-4" />
                  {venue.courtCount} sân
                </span>
                <span>{venue.priceLabel}</span>
              </div>
              </div>
              <button className="mt-3 inline-flex w-fit items-center gap-2 rounded-lg bg-yellow-300 px-4 py-2.5 text-xs font-semibold text-emerald-950">
                <CalendarDays className="size-4" />
                Đặt lịch
              </button>
            </div>
          </article>
        ))}
      </div>
    </section>
  )
}

function formatCurrency(value) {
  if (value === null || value === undefined) return 'Miễn phí'
  return `${Number(value).toLocaleString('vi-VN')}đ`
}



/** Hiển thị thời điểm đánh giá theo kiểu tương đối, dễ đọc hơn ngày đầy đủ. */
function formatReviewDate(isoDate) {
  if (!isoDate) return ''
  const days = Math.floor((Date.now() - new Date(isoDate).getTime()) / 86400000)
  if (days <= 0) return 'Hôm nay'
  if (days === 1) return 'Hôm qua'
  if (days < 7) return `${days} ngày trước`
  if (days < 30) return `${Math.floor(days / 7)} tuần trước`
  if (days < 365) return `${Math.floor(days / 30)} tháng trước`
  return new Date(isoDate).toLocaleDateString('vi-VN')
}

function buildBookingDates() {
  const formatter = new Intl.DateTimeFormat('vi-VN', { weekday: 'short' })
  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date()
    date.setHours(0, 0, 0, 0)
    date.setDate(date.getDate() + index)
    return {
      value: date.toISOString().slice(0, 10),
      day: index === 0 ? 'Hôm nay' : formatter.format(date).replace('Th ', 'T'),
      label: `${String(date.getDate()).padStart(2, '0')}/${String(date.getMonth() + 1).padStart(2, '0')}`,
    }
  })
}

/** Tải chi tiết sân từ API rồi mới dựng màn hình (2.1.18). */
function VenueDetailRoute({ venueKey, user, onLogout }) {
  const { status, data, error, notFound, reload } = useVenueDetail(venueKey)

  if (status === 'loading') {
    return (
      <main className="min-h-screen bg-[#f4f7f1] pb-24">
        <AppHeader user={user} onLogout={onLogout} />
        <section className="mx-auto max-w-[1200px] px-4 py-10">
          <div className="grid gap-4">
            <div className="h-72 animate-pulse rounded-xl bg-emerald-100/70" />
            <div className="h-28 animate-pulse rounded-xl bg-emerald-100/50" />
          </div>
        </section>
      </main>
    )
  }

  if (status === 'error') {
    return (
      <main className="min-h-screen bg-[#f4f7f1] pb-24">
        <AppHeader user={user} onLogout={onLogout} />
        <section className="mx-auto max-w-[900px] px-4 py-10">
          <BookingEmptyState
            title={notFound ? 'Không tìm thấy sân' : 'Không tải được thông tin sân'}
            description={notFound ? 'Sân có thể đã ngừng hoạt động hoặc đường dẫn không chính xác.' : error}
            actionLabel="Về trang chủ"
            actionHref="/"
          />
          {!notFound && (
            <div className="mt-5 text-center">
              <button className="h-11 rounded-lg bg-emerald-700 px-6 text-sm font-semibold text-white" onClick={reload}>
                Thử lại
              </button>
            </div>
          )}
        </section>
      </main>
    )
  }

  return <VenueDetailPage venue={data.venue} db={data.tables} user={user} onLogout={onLogout} />
}

function VenueDetailPage({ venue, db = {}, user, onLogout }) {
  const venueImages = (db.venue_images ?? [])
    .filter((image) => image.venue_id === venue?.id)
    .sort((a, b) => a.display_order - b.display_order)
  const coverImage = venueImages.find((image) => image.is_cover) ?? venueImages[0]
  const gallery = venueImages.length
    ? venueImages
    : [{ image_url: venue?.image, caption: venue?.name, is_cover: true }]
  const venueCourts = (db.courts ?? []).filter((court) => court.venue_id === venue?.id && court.status === 'active')
  const priceRules = (db.court_price_rules ?? []).filter((rule) =>
    venueCourts.some((court) => court.id === rule.court_id) && rule.status === 'active'
  )
  const venueServiceRows = (db.venue_services ?? []).filter((item) => item.venue_id === venue?.id)
  const services = venueServiceRows.map((item) => ({
    ...item,
    service: (db.services ?? []).find((service) => service.id === item.service_id),
  })).filter((item) => item.service)
  const operatingHourRows = (db.venue_operating_hours ?? [])
    .filter((item) => item.venue_id === venue?.id)
    .sort((a, b) => a.day_of_week - b.day_of_week)
  const [defaultOpenTime = '05:00', defaultCloseTime = '23:00'] = (venue?.openHours ?? '').split(' - ')
  const operatingHours = operatingHourRows.length
    ? operatingHourRows
    : weekDays.map((day) => ({ venue_id: venue?.id, day_of_week: day.value, open_time: defaultOpenTime, close_time: defaultCloseTime, is_closed: false }))
  const bookingDates = useMemo(() => buildBookingDates(), [])
  const [activeImageIndex, setActiveImageIndex] = useState(0)
  const [showGallery, setShowGallery] = useState(false)
  const [selectedCourtId, setSelectedCourtId] = useState(() => venueCourts[0]?.id ?? '')
  const [showCourtOptions, setShowCourtOptions] = useState(false)
  const [selectedDate, setSelectedDate] = useState(bookingDates[0].value)
  const [selectedSlot, setSelectedSlot] = useState('')
  const [durationMinutes, setDurationMinutes] = useState(60)
  const [selectionSaved, setSelectionSaved] = useState(false)
  const [quote, setQuote] = useState(null)
  const [quoteError, setQuoteError] = useState('')

  // 2.1.26 - lịch trống thật, đổi ngày hoặc thời lượng là tải lại.
  const availabilityState = useAvailability(venue?.id, selectedDate, durationMinutes)
  const availability = availabilityState.data
  // 2.1.23 - đánh giá thật.
  const reviewState = useVenueReviews(venue?.slug ?? venue?.id)
  const reviews = reviewState.reviews

  /** Đổi ngày, sân con hoặc thời lượng thì lựa chọn và tạm tính cũ không còn đúng. */
  function resetSelection() {
    setSelectedSlot('')
    setQuote(null)
    setQuoteError('')
    setSelectionSaved(false)
  }

  /** 2.1.27 - hỏi server giá của khung giờ vừa chọn. */
  async function selectSlot(slot) {
    if (slot.status !== 'available') return
    setSelectedSlot(slot.time)
    setSelectionSaved(false)
    setQuoteError('')
    try {
      setQuote(await fetchQuote({
        courtId: selectedCourtId,
        startTime: slot.startAt,
        durationMinutes,
      }))
    } catch (error) {
      setQuote(null)
      setQuoteError(error?.message ?? 'Không tạm tính được, vui lòng thử lại.')
    }
  }

  if (!venue) {
    return (
      <main className="grid min-h-screen place-items-center bg-emerald-50 px-4 text-center text-emerald-950">
        <div>
          <BrandMark compact />
          <h1 className="mt-5 text-2xl font-semibold">Không tìm thấy sân</h1>
          <a className="mt-5 inline-flex rounded-lg bg-emerald-700 px-5 py-3 text-sm font-semibold text-white" href="/">
            Về trang chủ
          </a>
        </div>
      </main>
    )
  }

  const minPrice = priceRules.length ? Math.min(...priceRules.map((rule) => Number(rule.price_per_hour))) : null
  const maxPrice = priceRules.length ? Math.max(...priceRules.map((rule) => Number(rule.price_per_hour))) : null
  const courtImage = (index) => gallery[(index + 1) % gallery.length]?.image_url ?? coverImage?.image_url ?? venue.image
  const activeImage = gallery[activeImageIndex] ?? gallery[0]
  const selectedCourt = venueCourts.find((court) => court.id === selectedCourtId) ?? venueCourts[0]
  // 2.1.26 - lịch trống lấy từ API, không còn dựng bằng dữ liệu giả.
  const courtAvailability = availability?.courts?.find((item) => item.courtId === selectedCourt?.id)
  const slotRows = courtAvailability?.slots ?? []
  const selectedSlotData = slotRows.find((slot) => slot.time === selectedSlot)
  // 2.1.27 - số tiền do server tính, frontend không tự nhân giá.
  const selectedPrice = quote?.totalAmount ?? selectedSlotData?.totalPrice ?? null
  const selectedEndTime = selectedSlotData?.endTime ?? ''
  const directionsHref = `https://www.google.com/maps/dir/?api=1&destination=${venue.latitude},${venue.longitude}`
  // 2.1.23 - đánh giá lấy từ API venue_reviews.

  return (
    <main className="home-density min-h-screen bg-[#f4f7f1] pb-36 text-[14px] text-emerald-950 lg:pb-36">
      <AppHeader user={user} onLogout={onLogout} activeLabel="Khám phá" />

      <section className="mx-auto max-w-[1680px] px-4 pt-4 md:px-6 lg:px-8">
        <div className="mb-3"><PageBackLink href="/#venues" label="Về danh sách sân" /></div>
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2 text-xs font-medium text-emerald-700/70">
            <a href="/">Trang chủ</a>
            <span>/</span>
            <a href="/#venues">Tìm sân</a>
            <span>/</span>
            <span className="font-semibold text-emerald-900">{venue.name}</span>
          </div>
          <div className="flex items-center gap-2">
            <button className="inline-flex h-9 items-center gap-2 rounded-md border border-emerald-100 bg-white px-3 text-xs font-semibold text-emerald-700 shadow-sm">
              <Share2 className="size-4" />
              Chia sẻ
            </button>
            <button className="inline-flex h-9 items-center gap-2 rounded-md border border-emerald-100 bg-white px-3 text-xs font-semibold text-emerald-700 shadow-sm">
              <Heart className="size-4" />
              Yêu thích
            </button>
          </div>
        </div>

        <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1fr)_390px]">
          <section className="min-w-0">
            <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_260px]">
              <div className="relative min-h-[300px] overflow-hidden rounded-md bg-emerald-950 shadow-[0_14px_34px_rgba(34,52,40,0.10)] md:min-h-[340px]">
                <img className="absolute inset-0 h-full w-full object-cover" src={activeImage?.image_url ?? venue.image} alt={activeImage?.caption ?? venue.name} />
                <div className="absolute inset-0 bg-[linear-gradient(90deg,rgba(0,58,39,0.86)_0%,rgba(0,58,39,0.48)_45%,rgba(0,58,39,0.02)_100%)]" />
                <div className="absolute left-5 top-5 flex flex-wrap gap-2">
                  <span className="inline-flex items-center gap-1.5 rounded-md bg-white/14 px-3 py-1.5 text-xs font-semibold text-white backdrop-blur">
                    <Star className="size-3.5 fill-amber-400 text-amber-400" />
                    {venue.rating} ({venue.reviewCount} đánh giá)
                  </span>
                </div>
                <div className="absolute bottom-6 left-5 right-5">
                  <p className="inline-flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.14em] text-lime-200">
                    <MapPin className="size-3.5" />
                    {venue.district}
                  </p>
                  <h1 className="mt-2 max-w-3xl text-2xl font-semibold leading-tight tracking-normal text-white md:text-[32px]">
                    {venue.name}
                  </h1>
                  <p className="mt-3 max-w-2xl text-sm font-medium leading-6 text-emerald-50/90">
                    {venue.address}
                  </p>
                </div>
                <div className="absolute bottom-4 right-4 flex items-center gap-2">
                  <button className="grid size-8 place-items-center rounded-full bg-white/16 text-white backdrop-blur" onClick={() => setActiveImageIndex((current) => (current - 1 + gallery.length) % gallery.length)} aria-label="Ảnh trước">
                    <ChevronRight className="size-4 rotate-180" />
                  </button>
                  <button className="rounded-full bg-white/16 px-3 py-2 text-xs font-semibold text-white backdrop-blur" onClick={() => setShowGallery(true)}>{activeImageIndex + 1} / {gallery.length}</button>
                  <button className="grid size-8 place-items-center rounded-full bg-white/16 text-white backdrop-blur" onClick={() => setActiveImageIndex((current) => (current + 1) % gallery.length)} aria-label="Ảnh tiếp theo">
                    <ChevronRight className="size-4" />
                  </button>
                </div>
              </div>

              <button className="relative min-h-[220px] overflow-hidden rounded-md bg-emerald-950 shadow-[0_14px_34px_rgba(34,52,40,0.10)] md:min-h-[340px]" onClick={() => { setActiveImageIndex(Math.min(1, gallery.length - 1)); setShowGallery(true) }}>
                <img className="absolute inset-0 h-full w-full object-cover" src={gallery[1]?.image_url ?? coverImage?.image_url ?? venue.image} alt={gallery[1]?.caption ?? venue.name} />
                <div className="absolute inset-0 bg-gradient-to-t from-emerald-950/68 via-transparent to-transparent" />
                <span className="absolute bottom-3 left-3 rounded-md bg-emerald-950/48 px-3 py-1.5 text-xs font-semibold text-white backdrop-blur">
                  {gallery[1]?.caption ?? 'Khu thi đấu'}
                </span>
              </button>
            </div>

            <div className="mt-3 grid grid-cols-4 gap-2.5 max-sm:grid-cols-2">
              {gallery.slice(0, 4).map((image, index) => (
                <button key={image.id ?? image.image_url} className={classNames('relative h-20 overflow-hidden rounded-md bg-emerald-900 text-left ring-2', activeImageIndex === index ? 'ring-emerald-500' : 'ring-emerald-100')} onClick={() => setActiveImageIndex(index)}>
                  <img className="h-full w-full object-cover opacity-90" src={image.image_url} alt={image.caption ?? venue.name} />
                  {index === 0 && <span className="absolute bottom-2 left-2 rounded bg-white/90 px-2 py-1 text-xs font-semibold text-emerald-900">Ảnh chính</span>}
                  {index === 3 && gallery.length > 4 && <span className="absolute inset-0 grid place-items-center bg-emerald-950/60 text-sm font-semibold text-white" onClick={() => setShowGallery(true)}>+{gallery.length - 4} ảnh</span>}
                </button>
              ))}
            </div>

            <div className="mt-4 grid gap-2.5 sm:grid-cols-3">
              {[
                { label: 'Giờ mở cửa', value: venue.openHours, icon: Clock3 },
                { label: 'Số sân đang mở', value: `${venue.courtCount} sân`, icon: Users },
                { label: 'Khoảng giá', value: minPrice && maxPrice ? `${formatCurrency(minPrice)} - ${formatCurrency(maxPrice)}` : venue.priceLabel, icon: Tag },
              ].map((item) => {
                const Icon = item.icon
                return (
                  <div key={item.label} className="border border-emerald-100 bg-white p-3.5 shadow-[0_8px_22px_rgba(34,52,40,0.05)]">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <p className="text-xs font-medium text-emerald-700">{item.label}</p>
                        <p className="mt-1.5 text-base font-semibold text-emerald-950">{item.value}</p>
                      </div>
                      <span className="grid size-9 shrink-0 place-items-center rounded-md bg-emerald-50 text-emerald-700">
                        <Icon className="size-4" />
                      </span>
                    </div>
                  </div>
                )
              })}
            </div>

            <section className="mt-4 overflow-hidden border border-emerald-100 bg-white shadow-[0_10px_28px_rgba(34,52,40,0.06)]">
              <div className="flex flex-wrap items-center justify-between gap-4 p-4">
                <ProfileSectionTitle icon={MapPinned} eyebrow="Vị trí sân" title="Địa chỉ & chỉ đường" />
                <a className="flex h-10 items-center gap-2 rounded-lg bg-emerald-700 px-4 text-xs font-semibold text-white" href={directionsHref} target="_blank" rel="noreferrer">
                  <Navigation className="size-4" /> Mở chỉ đường
                </a>
              </div>
              <div className="grid border-t border-emerald-100 lg:grid-cols-[1fr_320px]">
                <VenueMap latitude={venue.latitude} longitude={venue.longitude} name={venue.name} className="h-64 w-full" />
                <div className="flex flex-col justify-center bg-emerald-50/60 p-5">
                  <p className="text-sm font-semibold text-emerald-950">{venue.name}</p>
                  <p className="mt-2 text-sm font-medium leading-6 text-stone-600">{venue.address}</p>
                  {venue.distanceKm != null && <p className="mt-4 inline-flex items-center gap-2 text-xs font-semibold text-emerald-700"><MapPin className="size-4" />Cách bạn khoảng {venue.distanceKm} km</p>}
                  <p className="mt-2 inline-flex items-center gap-2 text-xs font-semibold text-emerald-700"><Clock3 className="size-4" />Mở cửa {venue.openHours}</p>
                </div>
              </div>
            </section>

            <div className="mt-4 border border-emerald-100 bg-white p-4 shadow-[0_10px_28px_rgba(34,52,40,0.06)]">
              <SectionTitle eyebrow="Dữ liệu sân" title="Sân con & bảng giá" icon={Map} action="Xem lịch" />
              <div className="mt-4 grid gap-2.5">
                {venueCourts.map((court, index) => (
                  <article key={court.id} className="grid gap-3 border border-emerald-100 bg-[#fbfdf8] p-3 sm:grid-cols-[104px_minmax(0,1fr)_124px] sm:items-center">
                    <div className="h-22 overflow-hidden rounded-md bg-emerald-900">
                      <img className="h-full w-full object-cover" src={courtImage(index)} alt={court.name} />
                    </div>
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <h3 className="text-base font-semibold text-emerald-950">{court.name}</h3>
                        <span className="rounded bg-white px-2 py-1 text-xs font-semibold uppercase text-emerald-700 ring-1 ring-emerald-100">
                          {court.court_type}
                        </span>
                      </div>
                      <p className="mt-1.5 text-xs font-medium text-emerald-700">
                        {court.indoor ? 'Trong nhà' : 'Ngoài trời'} · mặt sân {court.surface_type} · mã {court.court_code}
                      </p>
                      <div className="mt-2.5 grid gap-2 md:grid-cols-2">
                      {priceRules.filter((rule) => rule.court_id === court.id).map((rule) => (
                        <p key={`${court.id}-${rule.start_time}`} className="flex items-center justify-between bg-white px-3 py-1.5 text-xs font-medium text-stone-600 ring-1 ring-emerald-100">
                          <span>{rule.start_time} - {rule.end_time}</span>
                          <span className="font-semibold text-emerald-700">{formatCurrency(rule.price_per_hour)}</span>
                        </p>
                      ))}
                      </div>
                    </div>
                    <button className="h-10 rounded-md bg-lime-300 px-3 text-xs font-semibold text-emerald-950" onClick={() => { setSelectedCourtId(court.id); resetSelection(); document.getElementById('booking')?.scrollIntoView({ behavior: 'smooth' }) }}>
                      Xem lịch
                    </button>
                  </article>
                ))}
                {venueCourts.length === 0 && <div className="rounded-lg bg-stone-50 p-5 text-center text-sm font-medium text-stone-500">Sân đang cập nhật danh sách sân con.</div>}
              </div>
            </div>

            <div className="mt-4 grid gap-4 lg:grid-cols-[1fr_0.9fr]">
              <section className="border border-emerald-100 bg-white p-4 shadow-[0_10px_28px_rgba(34,52,40,0.06)]">
                <SectionTitle eyebrow="Tiện ích" title="Dịch vụ tại sân" icon={Tag} action="Chi tiết" />
                <div className="mt-4 grid gap-2.5 sm:grid-cols-2">
                  {services.map((item) => (
                    <div key={item.service_id} className="bg-emerald-50 px-3 py-2.5 ring-1 ring-emerald-100">
                      <p className="text-sm font-semibold text-emerald-950">{item.service.name}</p>
                      <p className="mt-1 text-xs font-medium text-emerald-700">
                        {item.note} · {formatCurrency(item.price)}
                      </p>
                    </div>
                  ))}
                </div>
              </section>

              <section className="border border-emerald-100 bg-white p-4 shadow-[0_10px_28px_rgba(34,52,40,0.06)]">
                <SectionTitle eyebrow="Giờ hoạt động" title="Theo từng ngày" icon={Clock3} action="Tuần này" />
                <div className="mt-4 grid gap-2">
                  {operatingHours.map((item) => (
                    <p key={item.day_of_week} className="flex items-center justify-between bg-stone-50 px-3 py-2.5 text-xs font-medium text-stone-600">
                      <span>Thứ {item.day_of_week === 7 ? 'CN' : item.day_of_week + 1}</span>
                      <span className="font-semibold text-emerald-700">
                        {item.is_closed ? 'Đóng cửa' : `${item.open_time} - ${item.close_time}`}
                      </span>
                    </p>
                  ))}
                </div>
              </section>
            </div>

            <section className="mt-4 border border-emerald-100 bg-white p-4 shadow-[0_10px_28px_rgba(34,52,40,0.06)]">
              <SectionTitle
                eyebrow="Đánh giá"
                title={reviewState.totalElements > 0
                  ? `${venue.rating}/5 từ ${reviewState.totalElements} người chơi`
                  : 'Chưa có đánh giá'}
                icon={Star}
              />
              <div className="mt-4 grid gap-2.5 md:grid-cols-2">
                {reviewState.status === 'empty' && (
                  <p className="text-sm font-medium text-stone-500 md:col-span-2">
                    Sân này chưa có đánh giá nào.
                  </p>
                )}
                {reviews.map((review) => (
                  <article key={review.id} className="bg-[#fbfdf8] p-3.5 ring-1 ring-emerald-100">
                    <div className="flex items-center justify-between gap-3">
                      <div>
                        <p className="text-sm font-semibold text-emerald-950">{review.name}</p>
                        <p className="mt-1 text-sm font-medium text-emerald-700/75">{formatReviewDate(review.createdAt)}</p>
                      </div>
                      <span className="inline-flex items-center gap-1 text-sm font-semibold text-amber-600">
                        <Star className="size-4 fill-amber-400 text-amber-400" />
                        {review.rating}
                      </span>
                    </div>
                    <p className="mt-2.5 text-xs font-medium leading-5 text-stone-600">{review.text}</p>
                  </article>
                ))}
              </div>
            </section>
          </section>

          <aside id="booking" className="h-fit scroll-mt-24 rounded-md border border-emerald-100 bg-white p-5 shadow-[0_14px_34px_rgba(34,52,40,0.10)] xl:sticky xl:top-20">
            <p className="inline-flex items-center gap-1.5 text-xs font-semibold uppercase tracking-[0.12em] text-emerald-600">
              <CalendarDays className="size-3.5" />
              Đặt lịch nhanh
            </p>
            <h2 className="mt-1.5 text-lg font-semibold">Chọn sân & giờ</h2>
            <div className="relative mt-4">
            <button className="flex h-12 w-full items-center justify-between rounded-md border border-emerald-100 bg-white px-4 text-left text-sm font-semibold text-emerald-900" onClick={() => setShowCourtOptions((value) => !value)} disabled={!venueCourts.length}>
              <span>{selectedCourt?.name ?? 'Chưa có sân con'}</span>
              <ChevronDown className="size-4" />
            </button>
            {showCourtOptions && <div className="absolute inset-x-0 top-13 z-20 overflow-hidden rounded-lg border border-emerald-100 bg-white shadow-xl">{venueCourts.map((court) => <button key={court.id} className={classNames('flex w-full items-center justify-between border-b border-stone-100 px-4 py-3 text-left text-sm font-semibold last:border-b-0', selectedCourtId === court.id ? 'bg-emerald-50 text-emerald-700' : 'text-stone-600')} onClick={() => { setSelectedCourtId(court.id); resetSelection(); setShowCourtOptions(false) }}><span><span className="block">{court.name}</span><span className="mt-0.5 block text-[11px] font-medium text-stone-400">{court.indoor ? 'Trong nhà' : 'Ngoài trời'} · {court.court_type}</span></span>{selectedCourtId === court.id && <Check className="size-4" />}</button>)}</div>}
            </div>

            <div className="mt-4 flex gap-2 overflow-x-auto pb-1">
              {bookingDates.map((day) => (
                <button
                  key={day.value}
                  className={classNames(
                    'min-w-[82px] rounded-md px-3 py-2.5 text-sm font-semibold ring-1',
                    selectedDate === day.value ? 'bg-emerald-700 text-white ring-emerald-700' : 'bg-white text-emerald-800 ring-emerald-100',
                  )}
                  onClick={() => { setSelectedDate(day.value); resetSelection() }}
                >
                  <span className="block">{day.day}</span>
                  <span className="mt-1 block text-xs opacity-75">{day.label}</span>
                </button>
              ))}
            </div>

            <div className="mt-4 flex flex-wrap gap-x-4 gap-y-2 text-[11px] font-medium text-stone-500">
              <span className="inline-flex items-center gap-1.5"><span className="size-2.5 rounded-full bg-emerald-500" />Còn trống</span>
              <span className="inline-flex items-center gap-1.5"><span className="size-2.5 rounded-full bg-stone-300" />Đã đặt</span>
              <span className="inline-flex items-center gap-1.5"><span className="size-2.5 rounded-full bg-amber-300" />Tạm khóa</span>
            </div>

            {availabilityState.status === 'loading' && (
              <div className="mt-3 grid grid-cols-3 gap-2">
                {[0, 1, 2, 3, 4, 5].map((index) => <div key={index} className="h-11 animate-pulse rounded-md bg-emerald-100/70" />)}
              </div>
            )}

            {availabilityState.status === 'error' && (
              <div className="mt-3 rounded-md border border-red-200 bg-red-50 p-4 text-center">
                <p className="text-xs font-medium text-red-700">{availabilityState.error}</p>
                <button type="button" className="mt-3 h-9 rounded-md bg-emerald-700 px-4 text-xs font-semibold text-white" onClick={availabilityState.reload}>
                  Thử lại
                </button>
              </div>
            )}

            {availabilityState.status === 'success' && availability?.closed && (
              <p className="mt-3 rounded-md bg-stone-50 p-4 text-center text-xs font-medium text-stone-500">
                Sân nghỉ vào ngày này. Chọn ngày khác nhé.
              </p>
            )}

            {availabilityState.status === 'success' && !availability?.closed && slotRows.length === 0 && (
              <p className="mt-3 rounded-md bg-stone-50 p-4 text-center text-xs font-medium text-stone-500">
                Không còn khung giờ nào phù hợp với thời lượng {durationMinutes} phút trong ngày này.
              </p>
            )}

            <div className="mt-3 grid grid-cols-3 gap-2">
              {slotRows.map((slot) => (
                <button
                  key={slot.time}
                  className={classNames(
                    'relative rounded-md px-3 py-3 text-sm font-semibold ring-1 transition',
                    selectedSlot === slot.time && 'bg-emerald-700 text-white ring-emerald-700',
                    selectedSlot !== slot.time && slot.status === 'available' && 'bg-emerald-50 text-emerald-800 ring-emerald-100 hover:bg-emerald-100',
                    slot.status === 'booked' && 'cursor-not-allowed bg-stone-100 text-stone-300 line-through ring-stone-100',
                    slot.status === 'blocked' && 'cursor-not-allowed bg-amber-50 text-amber-500 ring-amber-100',
                    slot.status === 'past' && 'cursor-not-allowed bg-stone-50 text-stone-300 ring-stone-100',
                  )}
                  disabled={slot.status !== 'available'}
                  title={slot.status === 'booked' ? 'Đã có người đặt'
                    : slot.status === 'blocked' ? 'Sân đang bị khoá'
                    : slot.status === 'past' ? 'Khung giờ đã qua' : `${slot.time} - ${slot.endTime}`}
                  onClick={() => selectSlot(slot)}
                >
                  {slot.time}
                </button>
              ))}
            </div>

            <div className="mt-4">
              <p className="text-xs font-semibold text-emerald-950">Thời lượng chơi</p>
              <div className="mt-2 grid grid-cols-3 gap-2">{[60, 90, 120].map((duration) => <button key={duration} className={classNames('h-10 rounded-md text-xs font-semibold ring-1', durationMinutes === duration ? 'bg-emerald-700 text-white ring-emerald-700' : 'bg-white text-stone-500 ring-stone-200')} onClick={() => { setDurationMinutes(duration); resetSelection() }}>{duration} phút</button>)}</div>
            </div>

            <div className="mt-4 rounded-md bg-emerald-50 p-3 ring-1 ring-emerald-100">
              <div className="flex items-center justify-between gap-3 text-xs font-medium text-emerald-700"><span>Ngày chơi</span><span className="font-semibold text-emerald-950">{bookingDates.find((day) => day.value === selectedDate)?.label}</span></div>
              <div className="mt-2 flex items-center justify-between gap-3 text-xs font-medium text-emerald-700"><span>Khung giờ</span><span className="font-semibold text-emerald-950">{selectedSlot ? `${selectedSlot} - ${selectedEndTime}` : 'Chưa chọn'}</span></div>
              <div className="mt-3 flex items-center justify-between border-t border-emerald-100 pt-3 text-xs font-medium text-emerald-700"><span>Tạm tính</span><span className="text-base font-semibold text-emerald-950">{selectedPrice != null ? formatCurrency(selectedPrice) : '--'}</span></div>
              {quote?.segments?.length > 1 && (
                <div className="mt-2 grid gap-1 border-t border-emerald-100 pt-2">
                  {quote.segments.map((segment) => (
                    <div key={segment.startTime} className="flex items-center justify-between text-[11px] font-medium text-emerald-700">
                      <span>{segment.startTime} - {segment.endTime} · {formatCurrency(segment.pricePerHour)}/giờ</span>
                      <span>{formatCurrency(segment.amount)}</span>
                    </div>
                  ))}
                </div>
              )}
              {quoteError && <p className="mt-2 text-[11px] font-medium text-red-600">{quoteError}</p>}
              {quote && !quote.available && (
                <p className="mt-2 text-[11px] font-medium text-amber-700">{quote.unavailableReason}</p>
              )}
            </div>
            {selectionSaved && <p className="mt-3 flex items-center justify-center gap-2 rounded-lg bg-emerald-100 px-3 py-2.5 text-xs font-semibold text-emerald-700"><CheckCircle2 className="size-4" />Đã lưu lựa chọn, đang chuyển sang xác nhận.</p>}
            <button className="mt-4 flex h-12 w-full items-center justify-center gap-2 rounded-md bg-lime-300 text-sm font-semibold text-emerald-950 shadow-[0_10px_22px_rgba(163,230,53,0.24)] disabled:cursor-not-allowed disabled:bg-stone-200 disabled:text-stone-400 disabled:shadow-none" disabled={!selectedCourt || !selectedSlot || !quote?.available} onClick={() => {
              // Luu lựa chọn tạm để /booking/checkout đọc lại. Số tiền ở đây do server tính;
              // bước tạo đơn thật vẫn phải tính lại, không tin giá trị lưu ở client.
              window.sessionStorage.setItem(PENDING_BOOKING_STORAGE_KEY, JSON.stringify({
                venueId: venue.id,
                venueName: venue.name,
                courtId: selectedCourt.id,
                courtName: selectedCourt.name,
                date: selectedDate,
                startTime: selectedSlot,
                endTime: selectedEndTime,
                startAt: quote.startTime,
                endAt: quote.endTime,
                durationMinutes,
                price: quote.totalAmount,
              }))
              setSelectionSaved(true)
              window.location.href = '/booking/checkout'
            }}>
              <CalendarDays className="size-4" />
              Tiếp tục đặt sân
            </button>
            <a className="mt-2.5 flex h-10 w-full items-center justify-center gap-2 rounded-md border border-emerald-100 text-xs font-semibold text-emerald-700" href={`tel:${venue.phone}`}>
              <Phone className="size-4" />
              Gọi sân
            </a>
          </aside>
        </div>
      </section>

      {showGallery && (
        <div className="fixed inset-0 z-[1200] grid place-items-center bg-emerald-950/92 p-4 backdrop-blur-sm" onMouseDown={() => setShowGallery(false)}>
          <button className="absolute right-4 top-4 grid size-11 place-items-center rounded-full bg-white/12 text-white" onClick={() => setShowGallery(false)} aria-label="Đóng thư viện ảnh"><X className="size-6" /></button>
          <button className="absolute left-3 top-1/2 grid size-11 -translate-y-1/2 place-items-center rounded-full bg-white/12 text-white sm:left-8" onMouseDown={(event) => event.stopPropagation()} onClick={() => setActiveImageIndex((current) => (current - 1 + gallery.length) % gallery.length)} aria-label="Ảnh trước"><ChevronRight className="size-6 rotate-180" /></button>
          <figure className="max-w-[1100px]" onMouseDown={(event) => event.stopPropagation()}>
            <img className="max-h-[76svh] w-full rounded-xl object-contain shadow-2xl" src={activeImage?.image_url ?? venue.image} alt={activeImage?.caption ?? venue.name} />
            <figcaption className="mt-4 text-center text-sm font-semibold text-white">{activeImage?.caption ?? venue.name} · {activeImageIndex + 1}/{gallery.length}</figcaption>
          </figure>
          <button className="absolute right-3 top-1/2 grid size-11 -translate-y-1/2 place-items-center rounded-full bg-white/12 text-white sm:right-8" onMouseDown={(event) => event.stopPropagation()} onClick={() => setActiveImageIndex((current) => (current + 1) % gallery.length)} aria-label="Ảnh tiếp theo"><ChevronRight className="size-6" /></button>
        </div>
      )}

      <BottomNav activeLabel="Khám phá" />
    </main>
  )
}

function MapVenueDetail({ venue, onBack, onClose }) {
  const detailHref = `/san/${venue.id}`
  return (
    <aside className="absolute left-0 top-0 z-30 h-[calc(100svh-74px)] w-[min(704px,100vw)] overflow-y-auto border-r border-stone-200 bg-white shadow-[16px_0_36px_rgba(15,57,38,0.12)]">
      <div className="sticky top-0 z-20 bg-white p-2">
        <div className="flex h-16 items-center gap-3 rounded-full border border-stone-200 bg-white px-4 shadow-[0_4px_14px_rgba(24,48,35,0.12)]">
        <button className="grid size-10 place-items-center rounded-md text-emerald-900 hover:bg-emerald-50" onClick={onBack}>
          <ArrowLeft className="size-5" />
        </button>
        <p className="min-w-0 flex-1 truncate text-sm font-semibold uppercase tracking-[0.08em] text-emerald-950">
          {venue.name}
        </p>
        <button className="grid size-10 place-items-center rounded-md text-emerald-900 hover:bg-emerald-50" onClick={onClose}>
          <X className="size-5" />
        </button>
        </div>
      </div>

      <div className="relative h-[238px] overflow-hidden bg-emerald-800">
        <img className="h-full w-full object-cover opacity-85" src={venue.image} alt={venue.name} />
        <div className="absolute inset-0 bg-gradient-to-t from-emerald-950/70 to-transparent" />
        <span className="absolute bottom-4 left-4 inline-flex items-center gap-2 rounded-md bg-emerald-500 px-4 py-2 text-sm font-semibold text-white shadow-lg">
          <Star className="size-4 fill-white" />
          {venue.rating} ({venue.reviewCount} đánh giá)
        </span>
        <a href={`${detailHref}#booking`} className="absolute right-4 top-4 rounded-md bg-lime-300 px-4 py-3 text-sm font-semibold text-emerald-950 shadow-lg">
          Đặt lịch
        </a>
      </div>

      <div className="relative -mt-20 mx-3 rounded-md border border-emerald-100 bg-emerald-50/95 p-4 shadow-[0_6px_18px_rgba(24,48,35,0.16)]">
        <div className="flex items-start gap-3">
          <div className="grid size-12 shrink-0 place-items-center rounded-md bg-white text-emerald-700 ring-1 ring-emerald-100">
            <MapPin className="size-6" />
          </div>
          <div>
            <h2 className="text-lg font-semibold text-emerald-950">{venue.name}</h2>
            <span className="mt-2 inline-flex rounded-md bg-white px-3 py-1 text-sm font-medium text-emerald-600 ring-1 ring-emerald-100">
              Cầu lông
            </span>
          </div>
        </div>

        <div className="mt-4 grid gap-3 text-sm font-medium text-emerald-900">
          <p className="flex gap-3">
            <MapPin className="mt-0.5 size-5 shrink-0 text-emerald-700" />
            {venue.address}
          </p>
          <p className="flex gap-3">
            <Clock3 className="mt-0.5 size-5 shrink-0 text-emerald-700" />
            {venue.openHours}
          </p>
          <p className="flex gap-3">
            <CalendarDays className="mt-0.5 size-5 shrink-0 text-emerald-700" />
            {venue.priceLabel}
          </p>
        </div>
      </div>

      <div className="grid grid-cols-3 gap-px border-y border-emerald-100 bg-emerald-100">
        {[
          ['Khoảng cách', venue.distanceKm == null ? 'Bật định vị để xem' : `${venue.distanceKm} km`],
          ['Số sân', `${venue.courtCount} sân`],
          ['Giá từ', venue.priceLabel],
        ].map(([label, value]) => (
          <div key={label} className="bg-white px-3 py-4 text-center">
            <p className="text-[11px] font-medium text-stone-400">{label}</p>
            <p className="mt-1 text-sm font-semibold text-emerald-900">{value}</p>
          </div>
        ))}
      </div>

      <div className="grid gap-3 p-4 sm:grid-cols-2">
        <a className="flex h-12 items-center justify-center gap-2 rounded-lg border border-emerald-200 bg-white text-sm font-semibold text-emerald-700" href={detailHref}>
          <Eye className="size-4" />
          Xem chi tiết sân
        </a>
        <a className="flex h-12 items-center justify-center gap-2 rounded-lg bg-emerald-700 text-sm font-semibold text-white shadow-sm" href={`${detailHref}#booking`}>
          <CalendarDays className="size-4" />
          Chọn lịch đặt sân
        </a>
      </div>
    </aside>
  )
}

function MapListPanel({ query, setQuery, venues, selectedVenue, onSelect, onClose }) {
  const filteredVenues = venues.filter((venue) => searchMatches(venue, query))

  return (
    <aside className="absolute left-0 top-0 z-30 h-[calc(100svh-74px)] w-[min(704px,100vw)] overflow-y-auto border-r border-stone-200 bg-white shadow-[16px_0_36px_rgba(15,57,38,0.12)]">
      <div className="sticky top-0 z-10 bg-white p-2">
        <label className="flex h-16 items-center gap-4 rounded-full border border-stone-200 bg-white px-4 shadow-[0_4px_14px_rgba(24,48,35,0.12)]">
          <BrandMark compact />
          <input
            className="min-w-0 flex-1 text-base font-medium text-stone-800 outline-none placeholder:text-stone-400"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Tìm kiếm sân quanh đây."
            autoFocus
          />
          <Search className="size-6 text-emerald-700" />
          {query && <button type="button" className="grid size-8 place-items-center rounded-full text-stone-400 hover:bg-stone-100" onClick={() => setQuery('')} aria-label="Xóa từ khóa"><X className="size-4" /></button>}
        </label>
      </div>

      <div className="flex items-center justify-between border-y border-stone-100 bg-stone-50 px-5 py-3">
        <p className="text-xs font-semibold text-stone-500">{filteredVenues.length} sân phù hợp</p>
        <p className="text-xs font-medium text-emerald-700">Sắp xếp theo khoảng cách</p>
      </div>

      <div>
        {filteredVenues.length > 0 ? filteredVenues.map((venue) => {
          const active = selectedVenue?.id === venue.id
          return (
          <button
            key={venue.id}
            className={classNames(
              'grid w-full grid-cols-[88px_minmax(0,1fr)] gap-4 border-b border-stone-100 p-4 text-left transition hover:bg-emerald-50',
              active && 'bg-emerald-50',
            )}
            onClick={() => onSelect(venue)}
          >
            <span className="relative h-24 overflow-hidden rounded-lg bg-emerald-900">
              <img className="h-full w-full object-cover" src={venue.image} alt="" />
              <span className="absolute bottom-1.5 left-1.5 rounded-md bg-white/95 px-2 py-1 text-[10px] font-bold text-amber-700">★ {venue.rating}</span>
            </span>
            <span className="min-w-0 py-0.5">
              <span className="flex items-start justify-between gap-3"><span className="line-clamp-1 text-base font-semibold text-emerald-950">{venue.name}</span><ChevronRight className="mt-0.5 size-4 shrink-0 text-emerald-600" /></span>
              <span className="mt-1.5 line-clamp-1 text-xs font-medium text-stone-500">{venue.address}</span>
              <span className="mt-2 flex flex-wrap gap-x-3 gap-y-1 text-xs font-semibold text-emerald-700">
                {venue.distanceKm != null && <span>{venue.distanceKm} km</span>}<span>{venue.courtCount} sân</span><span>{venue.priceLabel}</span>
              </span>
            </span>
          </button>
          )
        }) : (
          <div className="px-6 py-12 text-center">
            <span className="mx-auto grid size-12 place-items-center rounded-full bg-stone-100 text-stone-400"><Search className="size-5" /></span>
            <p className="mt-4 text-sm font-semibold text-stone-600">Không tìm thấy sân phù hợp</p>
            <p className="mt-1 text-xs font-medium text-stone-400">Thử tên sân, quận hoặc địa chỉ khác.</p>
          </div>
        )}
      </div>

      <button className="sr-only" onClick={onClose}>
        Đóng
      </button>
    </aside>
  )
}

function LeafletMap({ venues, selectedVenue, userLocation, locateTick, onSelect, onUserLocation, onLocateState }) {
  const containerRef = useRef(null)
  const mapRef = useRef(null)
  const markersRef = useRef([])
  const userMarkerRef = useRef(null)
  const [mapReady, setMapReady] = useState(false)

  useEffect(() => {
    if (!containerRef.current || mapRef.current) return undefined
    let cancelled = false

    import('leaflet').then((L) => {
      if (cancelled || !containerRef.current) return

      const map = L.map(containerRef.current, {
        center: [16.0544, 108.2022],
        zoom: 14,
        zoomControl: false,
        attributionControl: true,
      })

      L.tileLayer('https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png', {
        subdomains: 'abc',
        maxZoom: 19,
        attribution: '© OpenStreetMap contributors, Tiles style by HOT',
      }).addTo(map)

      L.control.zoom({ position: 'bottomright' }).addTo(map)
      mapRef.current = map
      setMapReady(true)
      setTimeout(() => map.invalidateSize(), 120)
    })

    return () => {
      cancelled = true
      if (mapRef.current) {
        mapRef.current.remove()
        mapRef.current = null
      }
      setMapReady(false)
    }
  }, [])

  useEffect(() => {
    const map = mapRef.current
    if (!map || !mapReady) return undefined
    let cancelled = false

    import('leaflet').then((L) => {
      if (cancelled) return

      markersRef.current.forEach((marker) => marker.remove())
      markersRef.current = []

      const bounds = []
      venues.forEach((venue) => {
        const active = selectedVenue?.id === venue.id
        const icon = L.divIcon({
          className: '',
          html: `
            <button class="leaflet-venue-pin ${active ? 'leaflet-venue-pin-active' : ''}" aria-label="${venue.name}">
              <span class="leaflet-venue-pin-icon"></span>
            </button>
          `,
          iconSize: [42, 52],
          iconAnchor: [21, 46],
        })
        const marker = L.marker([venue.lat, venue.lng], { icon }).addTo(map)
        marker.on('click', () => onSelect(venue))
        marker.bindTooltip(`${venue.name} · ${venue.priceLabel}`, {
          direction: 'top',
          offset: [0, -38],
          className: 'leaflet-venue-tooltip',
        })
        markersRef.current.push(marker)
        bounds.push([venue.lat, venue.lng])
      })

      if (bounds.length && !selectedVenue) {
        map.fitBounds(bounds, { padding: [120, 120], maxZoom: 14 })
      }
      if (selectedVenue) {
        map.setView([selectedVenue.lat, selectedVenue.lng], 16, { animate: true })
      }
    })

    return () => {
      cancelled = true
    }
  }, [venues, selectedVenue, onSelect, mapReady])

  useEffect(() => {
    const map = mapRef.current
    if (!map || !mapReady || !locateTick) return

    if (!navigator.geolocation) {
      onLocateState('unsupported')
      onUserLocation(DEFAULT_USER_LOCATION)
      map.setView([DEFAULT_USER_LOCATION.lat, DEFAULT_USER_LOCATION.lng], 16, { animate: true })
      return
    }

    onLocateState('locating')
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const nextLocation = {
          lat: position.coords.latitude,
          lng: position.coords.longitude,
        }
        onUserLocation(nextLocation)
        onLocateState('granted')
        map.setView([nextLocation.lat, nextLocation.lng], 16, { animate: true })
      },
      () => {
        onLocateState('denied')
        onUserLocation(DEFAULT_USER_LOCATION)
        map.setView([DEFAULT_USER_LOCATION.lat, DEFAULT_USER_LOCATION.lng], 16, { animate: true })
      },
      {
        enableHighAccuracy: true,
        timeout: 9000,
        maximumAge: 60000,
      },
    )
  }, [locateTick, mapReady, onLocateState, onUserLocation])

  useEffect(() => {
    const map = mapRef.current
    if (!map || !mapReady) return undefined
    let cancelled = false

    import('leaflet').then((L) => {
      if (cancelled) return

      if (userMarkerRef.current) {
        userMarkerRef.current.remove()
        userMarkerRef.current = null
      }

      if (!userLocation) return

      const icon = L.divIcon({
        className: '',
        html: '<span class="leaflet-user-location"><span></span></span>',
        iconSize: [36, 36],
        iconAnchor: [18, 18],
      })

      userMarkerRef.current = L.marker([userLocation.lat, userLocation.lng], {
        icon,
        zIndexOffset: 900,
      })
        .addTo(map)
        .bindTooltip('Vị trí của tôi', {
          direction: 'top',
          offset: [0, -18],
          className: 'leaflet-venue-tooltip',
        })
    })

    return () => {
      cancelled = true
    }
  }, [userLocation, mapReady])

  return <div ref={containerRef} className="absolute inset-0 z-0 bg-[#dfe9df]" />
}

/** So san toi da lay cho ban do; ban do can ca vung nhin chu khong chi mot trang. */
const MAP_PAGE_SIZE = 100

function MapPage() {
  const [filters, setFilters] = useState(DEFAULT_VENUE_FILTERS)
  const [showFilters, setShowFilters] = useState(false)
  const [isListOpen, setIsListOpen] = useState(false)
  const [selectedVenue, setSelectedVenue] = useState(null)
  const [query, setQuery] = useState('')
  const [userLocation, setUserLocation] = useState(null)
  const [locateTick, setLocateTick] = useState(0)
  const [locateState, setLocateState] = useState('idle')
  const [districts, setDistricts] = useState([])

  // Co vi tri nguoi dung thi backend loc theo ban kinh va tra ve khoang cach that (2.1.14).
  const searchParams = useMemo(() => ({
    q: query.trim() || undefined,
    district: filters.district === 'all' ? undefined : filters.district,
    maxPricePerHour: filters.maxPrice < MAX_PRICE_FILTER ? filters.maxPrice : undefined,
    minRating: filters.highRatingOnly ? 4.5 : undefined,
    availableOnly: filters.availableOnly || undefined,
    lat: userLocation?.lat,
    lng: userLocation?.lng,
    radiusKm: userLocation ? filters.maxDistance : undefined,
    size: MAP_PAGE_SIZE,
  }), [query, filters, userLocation])

  const { status, venues, error, reload } = useVenueSearch(searchParams)
  const isLoading = status === 'loading'
  // Marker va danh sach dung chung mot nguon du lieu da loc san o server.
  const visibleVenues = useMemo(
    () => venues.map((venue) => ({ ...venue, lat: venue.latitude, lng: venue.longitude })),
    [venues],
  )
  const activeFilterCount = [filters.district !== 'all', filters.maxDistance < 15, filters.maxPrice < MAX_PRICE_FILTER, filters.availableOnly, filters.highRatingOnly].filter(Boolean).length

  useEffect(() => {
    fetchDistricts()
      .then((items) => setDistricts(items.map((item) => item.name)))
      .catch(() => setDistricts([]))
  }, [])

  function openVenue(venue) {
    setSelectedVenue(venue)
    setIsListOpen(false)
  }

  function openSearchPanel() {
    setSelectedVenue(null)
    setIsListOpen(true)
  }

  function runMapSearch() {
    // Tu khoa da duoc gui len server, chi can mo ket qua dau tien.
    if (visibleVenues.length > 0) openVenue(visibleVenues[0])
    else setIsListOpen(true)
  }

  return (
    <main className="relative h-screen overflow-hidden bg-[#dfe9df] text-emerald-950">
      <div className="absolute inset-0 bottom-[74px]">
        <LeafletMap
          venues={visibleVenues}
          selectedVenue={selectedVenue}
          userLocation={userLocation}
          locateTick={locateTick}
          onSelect={openVenue}
          onUserLocation={setUserLocation}
          onLocateState={setLocateState}
        />

        {status === 'error' && (
          <div className="absolute inset-x-3 top-[84px] z-30 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 shadow-lg">
            <p className="text-sm font-medium text-red-700">{error}</p>
            <button
              type="button"
              className="h-9 rounded-lg bg-emerald-700 px-4 text-xs font-semibold text-white"
              onClick={reload}
            >
              Thử lại
            </button>
          </div>
        )}

        <div className="absolute left-3 right-3 top-4 z-20 flex items-center gap-3 overflow-x-auto pb-2">
          <label className="flex h-14 min-w-[360px] max-w-[620px] flex-1 items-center gap-3 rounded-full bg-white px-5 shadow-[0_6px_18px_rgba(29,52,39,0.16)]">
            <BrandMark compact />
            <input
              className="min-w-0 flex-1 text-sm font-medium text-stone-700 outline-none placeholder:text-stone-400"
              value={query}
              onFocus={openSearchPanel}
              onClick={openSearchPanel}
              onChange={(event) => {
                setQuery(event.target.value)
                setIsListOpen(true)
                setSelectedVenue(null)
              }}
              onKeyDown={(event) => {
                if (event.key === 'Enter') runMapSearch()
              }}
              placeholder="Tìm kiếm sân quanh đây."
            />
            {query && <button type="button" className="grid size-8 place-items-center rounded-full text-stone-400 hover:bg-stone-100" onClick={() => setQuery('')} aria-label="Xóa từ khóa"><X className="size-4" /></button>}
            <button
              className="-mr-2 grid size-10 place-items-center rounded-full text-emerald-800 hover:bg-emerald-50"
              onClick={runMapSearch}
              aria-label="Tìm kiếm sân"
              type="button"
            >
              <Search className="size-6" />
            </button>
          </label>
          {[
            { label: 'Bộ lọc', active: activeFilterCount > 0, action: () => setShowFilters(true) },
            { label: 'Gần tôi', active: filters.maxDistance === 5, action: () => setFilters((current) => ({ ...current, maxDistance: current.maxDistance === 5 ? 15 : 5 })) },
            { label: 'Còn sân', active: filters.availableOnly, action: () => setFilters((current) => ({ ...current, availableOnly: !current.availableOnly })) },
            { label: 'Giá tốt', active: filters.maxPrice === 120000, action: () => setFilters((current) => ({ ...current, maxPrice: current.maxPrice === 120000 ? 200000 : 120000 })) },
          ].map((item) => (
            <button
              key={item.label}
              className={classNames(
                'relative h-12 shrink-0 rounded-full px-5 text-sm font-semibold shadow-[0_6px_16px_rgba(29,52,39,0.14)]',
                item.active ? 'bg-emerald-600 text-white' : 'bg-white text-emerald-800',
              )}
              onClick={item.action}
            >
              {item.label}
              {item.label === 'Bộ lọc' && activeFilterCount > 0 && <span className="ml-2 inline-grid size-5 place-items-center rounded-full bg-lime-300 text-[10px] font-bold text-emerald-950">{activeFilterCount}</span>}
            </button>
          ))}
        </div>

        <div className="absolute right-5 top-44 z-20 grid gap-3">
          <button className="grid size-12 place-items-center rounded-full bg-white text-emerald-900 shadow-md">
            <Layers className="size-6" />
          </button>
        </div>

        {isLoading && (
          <div className="absolute left-1/2 top-28 z-20 -translate-x-1/2 rounded-full bg-white px-4 py-2 text-xs font-semibold text-emerald-800 shadow-lg">
            Đang tải danh sách sân...
          </div>
        )}

        {locateState !== 'idle' && locateState !== 'locating' && (
          <div className={classNames('absolute left-1/2 top-28 z-20 flex -translate-x-1/2 items-center gap-2 rounded-full px-4 py-2 text-xs font-semibold shadow-lg', locateState === 'granted' ? 'bg-emerald-700 text-white' : 'bg-amber-50 text-amber-800 ring-1 ring-amber-200')}>
            {locateState === 'granted' ? <CheckCircle2 className="size-4" /> : <MapPin className="size-4" />}
            {locateState === 'granted' ? 'Đã tìm thấy vị trí của bạn' : locateState === 'unsupported' ? 'Trình duyệt không hỗ trợ định vị' : 'Không lấy được vị trí, đang dùng vị trí mặc định'}
          </div>
        )}

        <div className="absolute bottom-8 right-5 z-30 flex gap-3">
          <button
            className={classNames(
              'grid size-14 place-items-center rounded-full text-white shadow-[0_10px_28px_rgba(10,76,45,0.24)]',
              isListOpen ? 'bg-emerald-700' : 'bg-emerald-600/80',
            )}
            onClick={() => {
              setIsListOpen((current) => !current)
              setSelectedVenue(null)
            }}
            aria-label="Mở danh sách sân"
          >
            <List className="size-7" />
          </button>
          <button
            className={classNames(
              'grid size-14 place-items-center rounded-full text-white shadow-[0_10px_28px_rgba(10,76,45,0.24)]',
              locateState === 'locating' ? 'bg-emerald-700' : 'bg-emerald-600/80',
            )}
            onClick={() => setLocateTick((current) => current + 1)}
            aria-label="Lấy vị trí của tôi"
            title={
              locateState === 'locating'
                ? 'Đang lấy vị trí'
                : locateState === 'denied'
                  ? 'Không lấy được vị trí, bấm để thử lại'
                  : 'Lấy vị trí của tôi'
            }
          >
            <LocateFixed className={classNames('size-7', locateState === 'locating' && 'animate-spin')} />
          </button>
        </div>

        {isListOpen && (
          <MapListPanel
            query={query}
            setQuery={setQuery}
            venues={visibleVenues}
            selectedVenue={selectedVenue}
            onSelect={openVenue}
            onClose={() => setIsListOpen(false)}
          />
        )}

        {selectedVenue && (
          <MapVenueDetail
            venue={selectedVenue}
            onBack={() => {
              setSelectedVenue(null)
              setIsListOpen(true)
            }}
            onClose={() => setSelectedVenue(null)}
          />
        )}

        {showFilters && <VenueFilterPanel filters={filters} districts={districts} onChange={setFilters} onReset={() => setFilters(DEFAULT_VENUE_FILTERS)} onClose={() => setShowFilters(false)} />}
      </div>
      <BottomNav activeLabel="Bản đồ" />
    </main>
  )
}

function BottomNav({ activeLabel = 'Trang chủ' }) {
  return (
    <nav className="fixed inset-x-0 bottom-0 z-40 border-t border-emerald-100 bg-white/96 px-2 py-1 shadow-[0_-10px_26px_rgba(24,48,35,0.1)] backdrop-blur">
      <div className="mx-auto grid max-w-5xl grid-cols-5 items-end">
        {tabs.map((tab) => {
          const Icon = tab.icon
          const active = tab.label === activeLabel
          const href = tab.label === 'Tài khoản' && readCachedUser() ? '/profile' : tab.href
          return (
            <a
              key={tab.label}
              href={href}
              className={classNames(
                'relative grid place-items-center gap-0.5 rounded-lg py-1 text-[11px] font-bold',
                active ? 'text-emerald-600' : 'text-stone-400',
                tab.raised && '-mt-6',
              )}
            >
              <span
                className={classNames(
                  'grid place-items-center',
                  tab.raised
                    ? 'size-11 rounded-md border-[3px] border-white bg-white text-emerald-600 shadow-[0_8px_20px_rgba(9,128,72,0.22)] ring-2 ring-emerald-500'
                    : 'size-6',
                )}
              >
                <Icon className={tab.raised ? 'size-5' : 'size-5'} />
              </span>
              <span>{tab.label}</span>
            </a>
          )
        })}
      </div>
    </nav>
  )
}

function App() {
  const [showSplash, setShowSplash] = useState(true)
  const [home, setHome] = useState(null)
  const [query, setQuery] = useState('')
  const [venueFilters, setVenueFilters] = useState(DEFAULT_VENUE_FILTERS)
  const [userLocation, setUserLocation] = useState(null)
  const [locating, setLocating] = useState(false)
  const [user, setUser] = useState(() => (readToken() ? readCachedUser() : null))
  const path = window.location.pathname.toLowerCase()
  const isLoginPage = path.includes('login') || path.includes('ulogin')
  const isVerifyEmailPage = path === '/register/verify'
  const isRegisterPage = path.includes('register') || path.includes('uregister')
  const isVerifyRecoveryPage = path === '/forgot-password/verify'
  const isForgotPage = path === '/forgot-password' || path === '/forgot' || path.includes('uforgot')
  const isResetSuccessPage = path === '/reset-password/success'
  const isResetPasswordPage = path === '/reset-password'
  const isProfilePreferencesPage = path === '/profile/preferences'
  const isProfileEditPage = path === '/profile/edit'
  const isProfilePage = path === '/profile'
  const isBookingCheckoutPage = path === '/booking/checkout'
  const isBookingSuccessPage = path.startsWith('/booking/success/')
  const isPaymentPage = path.startsWith('/payments/')
  const isBookingDetailPage = path.startsWith('/bookings/')
  const isBookingsPage = path === '/bookings'
  const bookingSuccessId = decodeURIComponent(path.split('/').filter(Boolean).at(2) ?? '')
  const paymentId = decodeURIComponent(path.split('/').filter(Boolean).at(1) ?? '')
  const bookingDetailId = decodeURIComponent(path.split('/').filter(Boolean).at(1) ?? '')
  const isMapPage = path.includes('map') || path.includes('ban-do')
  const isVenueDetailPage = path.startsWith('/san/') || path.startsWith('/venue/')
  const venueDetailId = decodeURIComponent(path.split('/').filter(Boolean).at(1) ?? '')

  useEffect(() => {
    const timer = window.setTimeout(() => setShowSplash(false), 1050)
    return () => window.clearTimeout(timer)
  }, [])

  useEffect(() => {
    function syncAuth() {
      setUser(readCachedUser())
    }

    // Token het han hoac bi thu hoi: xoa phien va dua ve trang dang nhap.
    function handleUnauthorized() {
      clearCachedUser()
      setUser(null)
    }

    window.addEventListener('storage', syncAuth)
    window.addEventListener('courtly-auth-change', syncAuth)
    window.addEventListener(UNAUTHORIZED_EVENT, handleUnauthorized)
    return () => {
      window.removeEventListener('storage', syncAuth)
      window.removeEventListener('courtly-auth-change', syncAuth)
      window.removeEventListener(UNAUTHORIZED_EVENT, handleUnauthorized)
    }
  }, [])

  // Sau khi tai lai trang, xac nhan lai phien voi backend thay vi tin ban sao o client.
  useEffect(() => {
    if (!readToken()) {
      // State da khoi tao la null; chi can don ban sao con sot lai.
      clearCachedUser()
      return
    }

    let ignore = false
    fetchCurrentUser()
      .then((currentUser) => {
        if (ignore) return
        saveCachedUser(currentUser)
        setUser(currentUser)
      })
      .catch(() => {
        // Loi 401 da duoc handleUnauthorized xu ly; loi mang thi giu nguyen ban sao.
      })

    return () => {
      ignore = true
    }
  }, [])

  const handleLogout = async () => {
    await signOut()
    setUser(null)
    window.location.href = '/'
  }

  useEffect(() => {
    let ignore = false

    async function loadHome() {
      const response = await fetch('/api/home.json')
      const data = await response.json()
      if (!ignore) setHome(data)
    }

    loadHome()
    return () => {
      ignore = true
    }
  }, [])

  // Toan bo viec tim kiem, loc va phan trang do backend lam. Frontend chi gui tham so.
  const searchParams = useMemo(() => ({
    q: query.trim() || undefined,
    district: venueFilters.district === 'all' ? undefined : venueFilters.district,
    maxPricePerHour: venueFilters.maxPrice < MAX_PRICE_FILTER ? venueFilters.maxPrice : undefined,
    minRating: venueFilters.highRatingOnly ? 4.5 : undefined,
    availableOnly: venueFilters.availableOnly || undefined,
    lat: userLocation?.lat,
    lng: userLocation?.lng,
    radiusKm: userLocation ? venueFilters.maxDistance : undefined,
    size: HOME_PAGE_SIZE,
  }), [query, venueFilters, userLocation])

  /**
   * 2.1.20 - chip "Gần tôi" cần toạ độ thật thì lọc bán kính mới có tác dụng.
   * Bấm lần nữa để tắt, quay về danh sách không lọc theo khoảng cách.
   */
  function toggleNearMe() {
    if (userLocation) {
      setUserLocation(null)
      setVenueFilters((current) => ({ ...current, maxDistance: 15 }))
      return
    }
    if (!navigator.geolocation) return
    setLocating(true)
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLocating(false)
        setUserLocation({ lat: position.coords.latitude, lng: position.coords.longitude })
        setVenueFilters((current) => ({ ...current, maxDistance: 5 }))
      },
      () => setLocating(false),
      { enableHighAccuracy: true, timeout: 10000 },
    )
  }

  const venueSearch = useVenueSearch(searchParams)
  const activeVenueFilterCount = [
    venueFilters.district !== 'all',
    venueFilters.maxPrice < MAX_PRICE_FILTER,
    venueFilters.highRatingOnly,
  ].filter(Boolean).length
  const venues = venueSearch.venues
  const [districtStats, setDistrictStats] = useState([])
  const districts = useMemo(() => districtStats.map((item) => item.name), [districtStats])

  useEffect(() => {
    // Danh sach khu vuc lay tu du lieu that, khong khai bao cung trong giao dien.
    fetchDistricts().then(setDistrictStats).catch(() => setDistrictStats([]))
  }, [])

  if (showSplash) {
    return <SplashScreen />
  }

  if (isBookingCheckoutPage) {
    if (!home) return <SplashScreen />
    return <BookingCheckoutPage user={user} onLogout={handleLogout} />
  }

  if (isBookingSuccessPage) {
    if (!home) return <SplashScreen />
    return <BookingSuccessPage bookingId={bookingSuccessId} user={user} onLogout={handleLogout} />
  }

  if (isPaymentPage) {
    if (!home) return <SplashScreen />
    return <PaymentPage paymentId={paymentId} user={user} onLogout={handleLogout} />
  }

  if (isBookingDetailPage) {
    if (!home) return <SplashScreen />
    return <BookingDetailPage bookingId={bookingDetailId} user={user} onLogout={handleLogout} />
  }

  if (isBookingsPage) {
    if (!home) return <SplashScreen />
    return <BookingsPage user={user} onLogout={handleLogout} />
  }

  if (isProfilePreferencesPage) {
    return (
      <ProfilePreferencesPage
        user={user}
        onLogout={handleLogout}
      />
    )
  }

  if (isProfileEditPage) {
    return (
      <ProfileEditPage
        user={user}
        onLogout={handleLogout}
      />
    )
  }

  if (isProfilePage) {
    return (
      <ProfilePage
        user={user}
        onLogout={handleLogout}
      />
    )
  }

  if (isMapPage) {
    return <MapPage />
  }

  if (isVenueDetailPage) {
    return (
      <VenueDetailRoute venueKey={venueDetailId} user={user} onLogout={handleLogout} />
    )
  }

  if (isVerifyRecoveryPage) {
    return <VerifyRecoveryPage />
  }

  if (isResetSuccessPage) {
    return <ResetPasswordSuccessPage />
  }

  if (isResetPasswordPage) {
    return <ResetPasswordPage />
  }

  if (isForgotPage) {
    return <ForgotPasswordPage />
  }

  // Phải đứng trước isRegisterPage: '/register/verify' cũng chứa chuỗi 'register'.
  if (isVerifyEmailPage) {
    return <VerifyEmailPage />
  }

  if (isRegisterPage) {
    return <RegisterPage />
  }

  if (isLoginPage) {
    return <LoginPage />
  }

  return (
    <main className="home-density min-h-screen overflow-x-hidden bg-[linear-gradient(150deg,#f3fbf7_0%,#f7faf4_48%,#eef8f4_100%)] pb-20 text-emerald-950">
      <Header
        slides={home?.slides ?? fallbackSlides}
        user={user}
        onLogout={handleLogout}
      />
      <SearchDock
        query={query}
        setQuery={setQuery}
        filters={venueFilters}
        onFiltersChange={setVenueFilters}
        districts={districts}
        onNearMe={toggleNearMe}
        nearMeActive={Boolean(userLocation)}
        locating={locating}
      />

      <section id="venues" className="mx-auto mt-8 max-w-[1680px] px-4 md:px-6 lg:px-8">
        <SectionTitle eyebrow="Sân phù hợp hôm nay" title="Gần bạn và còn lịch đẹp" icon={Zap} />

        {venueSearch.status === 'loading' && (
          <div className="mt-5 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
            {[0, 1, 2].map((index) => (
              <div key={index} className="h-72 animate-pulse rounded-xl bg-emerald-100/60" />
            ))}
          </div>
        )}

        {venueSearch.status === 'error' && (
          <div className="mt-5 rounded-lg border border-red-200 bg-red-50 p-8 text-center">
            <p className="font-medium text-red-700">{venueSearch.error}</p>
            <button
              className="mt-4 h-11 rounded-lg bg-emerald-700 px-6 text-sm font-semibold text-white"
              onClick={venueSearch.reload}
            >
              Thử lại
            </button>
          </div>
        )}

        {venueSearch.status === 'empty' && (
          <div className="mt-5 rounded-lg border border-stone-200 bg-white p-8 text-center">
            <p className="font-medium text-stone-500">Không tìm thấy sân phù hợp.</p>
            {(query || activeVenueFilterCount > 0) && (
              <button
                className="mt-4 h-11 rounded-lg border border-emerald-200 px-6 text-sm font-semibold text-emerald-700"
                onClick={() => { setQuery(''); setVenueFilters(DEFAULT_VENUE_FILTERS) }}
              >
                Xóa từ khóa và bộ lọc
              </button>
            )}
          </div>
        )}

        {venueSearch.status === 'success' && (
          <>
            <div className="mt-5 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
              {venues.map((venue) => (
                <VenueCard key={venue.id} venue={venue} />
              ))}
            </div>
            {venueSearch.totalElements > venues.length && (
              <p className="mt-5 text-center text-sm font-medium text-stone-500">
                Đang hiển thị {venues.length} trong {venueSearch.totalElements} sân. Thu hẹp bộ lọc để tìm nhanh hơn.
              </p>
            )}
          </>
        )}
      </section>

      <PromoStrip promotions={home?.promotions ?? []} />
      <AreaScroller
        areas={districtStats.map((item) => ({ name: item.name, count: `${item.venueCount} sân` }))}
        onSelect={(name) => setVenueFilters((current) => ({ ...current, district: name }))}
      />
      <CompactVenueList venues={venues} />

      <BottomNav />
    </main>
  )
}

export default App
