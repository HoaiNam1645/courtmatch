import { useEffect, useMemo, useState } from 'react'
import { Zap } from 'lucide-react'
import { fetchDistricts } from '../../api/venues'
import { BottomNav, Header } from '../../app/AppLayout'
import { DEFAULT_VENUE_FILTERS, HOME_PAGE_SIZE, MAX_PRICE_FILTER, fallbackSlides } from '../../app/config'
import { useApp } from '../../app/useApp'
import { useVenueSearch } from '../../hooks/useVenueSearch'
import { AreaScroller, CompactVenueList, PromoStrip, SearchDock, SectionTitle, VenueCard } from '../venues/HomeVenueComponents'

function HomePage() {
  const { user, logout } = useApp()
  const [home, setHome] = useState(null)
  const [query, setQuery] = useState('')
  const [venueFilters, setVenueFilters] = useState(DEFAULT_VENUE_FILTERS)
  const [userLocation, setUserLocation] = useState(null)
  const [locating, setLocating] = useState(false)
  const [districtStats, setDistrictStats] = useState([])

  useEffect(() => {
    let ignore = false
    fetch('/api/home.json')
      .then((response) => response.json())
      .then((data) => {
        if (!ignore) setHome(data)
      })
    return () => {
      ignore = true
    }
  }, [])

  useEffect(() => {
    fetchDistricts().then(setDistrictStats).catch(() => setDistrictStats([]))
  }, [])

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

  const venueSearch = useVenueSearch(searchParams)
  const venues = venueSearch.venues
  const districts = useMemo(() => districtStats.map((item) => item.name), [districtStats])
  const activeVenueFilterCount = [
    venueFilters.district !== 'all',
    venueFilters.maxPrice < MAX_PRICE_FILTER,
    venueFilters.highRatingOnly,
  ].filter(Boolean).length

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

  function resetSearch() {
    setQuery('')
    setVenueFilters(DEFAULT_VENUE_FILTERS)
  }

  return (
    <main className="home-density min-h-screen overflow-x-hidden bg-[linear-gradient(150deg,#f3fbf7_0%,#f7faf4_48%,#eef8f4_100%)] pb-20 text-emerald-950">
      <Header slides={home?.slides ?? fallbackSlides} user={user} onLogout={logout} />
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
            {[0, 1, 2].map((index) => <div key={index} className="h-72 animate-pulse rounded-xl bg-emerald-100/60" />)}
          </div>
        )}

        {venueSearch.status === 'error' && (
          <div className="mt-5 rounded-lg border border-red-200 bg-red-50 p-8 text-center">
            <p className="font-medium text-red-700">{venueSearch.error}</p>
            <button className="mt-4 h-11 rounded-lg bg-emerald-700 px-6 text-sm font-semibold text-white" onClick={venueSearch.reload}>Thử lại</button>
          </div>
        )}

        {venueSearch.status === 'empty' && (
          <div className="mt-5 rounded-lg border border-stone-200 bg-white p-8 text-center">
            <p className="font-medium text-stone-500">Không tìm thấy sân phù hợp.</p>
            {(query || activeVenueFilterCount > 0) && (
              <button className="mt-4 h-11 rounded-lg border border-emerald-200 px-6 text-sm font-semibold text-emerald-700" onClick={resetSearch}>Xóa từ khóa và bộ lọc</button>
            )}
          </div>
        )}

        {venueSearch.status === 'success' && (
          <>
            <div className="mt-5 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
              {venues.map((venue) => <VenueCard key={venue.id} venue={venue} />)}
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

export default HomePage
