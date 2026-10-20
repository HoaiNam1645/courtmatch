import { Clock3, Heart, LocateFixed, MapPin, Navigation, Star } from 'lucide-react'

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

export { VenueCard }
