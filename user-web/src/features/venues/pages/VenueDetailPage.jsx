import { useMemo, useState } from 'react'
import { CalendarDays, Check, CheckCircle2, ChevronDown, ChevronRight, Clock3, Heart, Map, MapPin, MapPinned, Navigation, Phone, Share2, Star, Tag, Users, X } from 'lucide-react'
import { fetchQuote } from '../../../api/venues'
import { AppHeader, BottomNav, BrandMark } from '../../../app/AppLayout'
import { VenueMap } from '../../../components/VenueMap'
import { useAvailability } from '../../../hooks/useAvailability'
import { useVenueDetail } from '../../../hooks/useVenueDetail'
import { useVenueReviews } from '../../../hooks/useVenueReviews'
import { BookingEmptyState } from '../../bookings/components/BookingEmptyState'
import { PageBackLink, ProfileSectionTitle } from '../../profile/components/ProfileUi'
import { weekDays } from '../../profile/profileOptions'
import { classNames } from '../../../shared/utils'
import { savePendingBooking } from '../../../shared/session'
import { SectionTitle } from '../HomeVenueComponents'

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
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const dayOfMonth = String(date.getDate()).padStart(2, '0')
    return {
      value: `${year}-${month}-${dayOfMonth}`,
      day: index === 0 ? 'Hôm nay' : formatter.format(date).replace('Th ', 'T'),
      label: `${dayOfMonth}/${month}`,
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
              savePendingBooking({
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
              })
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


export default VenueDetailRoute
