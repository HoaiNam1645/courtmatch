import { useEffect, useMemo, useRef, useState } from 'react'
import { ArrowLeft, CalendarDays, CheckCircle2, ChevronRight, Clock3, Eye, Layers, List, LocateFixed, MapPin, Search, Star, X } from 'lucide-react'
import { fetchDistricts } from '../../api/venues'
import { BottomNav, BrandMark } from '../../app/AppLayout'
import { useVenueSearch } from '../../hooks/useVenueSearch'
import { DEFAULT_USER_LOCATION, DEFAULT_VENUE_FILTERS, MAX_PRICE_FILTER } from '../../app/config'
import { classNames, searchMatches } from '../../shared/utils'
import { VenueFilterPanel } from './HomeVenueComponents'

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

export { MapPage }
