import { CalendarDays, Clock3, Flame, Heart, MapPin, Star, Users } from 'lucide-react'
import { SectionTitle } from './SectionTitle'

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

export { CompactVenueList }
