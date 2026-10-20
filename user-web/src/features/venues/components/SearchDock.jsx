import { useState } from 'react'
import { Clock3, Map, MapPin, Search, Settings2, Star, Tag, X } from 'lucide-react'
import { quickFilters } from '../../../app/config'
import { classNames } from '../../../shared/utils'
import { VenueFilterPanel } from './VenueFilterPanel'

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

export { SearchDock }
