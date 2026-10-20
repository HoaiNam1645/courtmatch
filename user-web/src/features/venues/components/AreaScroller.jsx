import { ChevronRight, MapPin } from 'lucide-react'
import { SectionTitle } from './SectionTitle'

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

export { AreaScroller }
