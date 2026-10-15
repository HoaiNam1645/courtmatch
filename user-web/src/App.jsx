import { useEffect, useMemo, useState } from 'react'
import 'leaflet/dist/leaflet.css'
import { Zap } from 'lucide-react'
import { UNAUTHORIZED_EVENT, readToken } from './api/client'
import { fetchCurrentUser } from './api/auth'
import { fetchDistricts } from './api/venues'
import { Header, BottomNav, SplashScreen } from './app/AppLayout'
import { DEFAULT_VENUE_FILTERS, HOME_PAGE_SIZE, MAX_PRICE_FILTER, fallbackSlides } from './app/config'
import { ForgotPasswordPage, LoginPage, RegisterPage, ResetPasswordPage, ResetPasswordSuccessPage, VerifyEmailPage, VerifyRecoveryPage } from './features/auth/AuthPages'
import { BookingCheckoutPage, BookingDetailPage, BookingsPage, BookingSuccessPage, PaymentPage } from './features/bookings/BookingPages'
import { MatchDetailPage, MatchesPage, NewMatchPage, PerformancePage } from './features/matches/MatchPages'
import { ProfileEditPage, ProfilePage, ProfilePreferencesPage } from './features/profile/ProfilePages'
import { AreaScroller, CompactVenueList, MapPage, PromoStrip, SearchDock, SectionTitle, VenueCard, VenueDetailRoute } from './features/venues/VenuePages'
import { useVenueSearch } from './hooks/useVenueSearch'
import { clearCachedUser, readCachedUser, saveCachedUser, signOut } from './shared/session'

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
  const isNewMatchPage = path === '/matches/new'
  const isMatchDetailPage = path.startsWith('/matches/') && !isNewMatchPage
  const isMatchesPage = path === '/matches'
  const isPerformancePage = path === '/performance'
  const bookingSuccessId = decodeURIComponent(path.split('/').filter(Boolean).at(2) ?? '')
  const paymentId = decodeURIComponent(path.split('/').filter(Boolean).at(1) ?? '')
  const bookingDetailId = decodeURIComponent(path.split('/').filter(Boolean).at(1) ?? '')
  const matchDetailId = decodeURIComponent(path.split('/').filter(Boolean).at(1) ?? '')
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

  if (isNewMatchPage) {
    return <NewMatchPage user={user} onLogout={handleLogout} />
  }

  if (isMatchDetailPage) {
    return <MatchDetailPage matchId={matchDetailId} user={user} onLogout={handleLogout} />
  }

  if (isMatchesPage) {
    return <MatchesPage user={user} onLogout={handleLogout} />
  }

  if (isPerformancePage) {
    return <PerformancePage user={user} onLogout={handleLogout} />
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
