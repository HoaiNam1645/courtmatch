import { useState } from 'react'
import { CalendarDays, ChevronRight, Clock3, Flame, Heart, LocateFixed, Map, MapPin, Navigation, Search, Settings2, Star, Tag, Umbrella, Users, X, Zap } from 'lucide-react'
import { quickFilters } from '../../app/config'
import { classNames } from '../../shared/utils'

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

export { AreaScroller, CompactVenueList, PromoStrip, SearchDock, SectionTitle, VenueCard, VenueFilterPanel }
