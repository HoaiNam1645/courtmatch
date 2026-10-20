import { CalendarDays, LocateFixed, MapPin, Tag, X } from 'lucide-react'
import { classNames } from '../../../shared/utils'

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

export { VenueFilterPanel }
