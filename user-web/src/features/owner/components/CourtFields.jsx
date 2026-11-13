import { FieldError } from '../../../shared/FormFeedback'
import { inputClass } from '../ownerStyles'

/**
 * Các ô của một sân con (2.3.3, 2.3.4).
 *
 * @param errorPrefix tiền tố khóa lỗi từ backend, ví dụ `courts[0].`
 */
function CourtFields({ court, onChange, errors, errorPrefix = '', options, labelSuffix = '' }) {
  function set(field, value) {
    onChange({ ...court, [field]: value })
  }

  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-[minmax(0,1.4fr)_minmax(0,0.8fr)_minmax(0,1fr)_minmax(0,1fr)_auto] lg:items-start">
      <label className="block">
        <span className="text-xs font-semibold text-stone-600">Tên sân con</span>
        <input className={`${inputClass} mt-1`} aria-label={`Tên sân con${labelSuffix}`} value={court.name} maxLength={100} onChange={(event) => set('name', event.target.value)} />
        <FieldError message={errors[`${errorPrefix}name`]} />
      </label>
      <label className="block">
        <span className="text-xs font-semibold text-stone-600">Mã</span>
        <input className={`${inputClass} mt-1 uppercase`} aria-label={`Mã sân con${labelSuffix}`} value={court.courtCode} maxLength={20} onChange={(event) => set('courtCode', event.target.value)} />
        <FieldError message={errors[`${errorPrefix}courtCode`]} />
      </label>
      <label className="block">
        <span className="text-xs font-semibold text-stone-600">Loại sân</span>
        <select className={`${inputClass} mt-1`} aria-label={`Loại sân${labelSuffix}`} value={court.courtType} onChange={(event) => set('courtType', event.target.value)}>
          {options.courtTypes.map((type) => <option key={type.value} value={type.value}>{type.label}</option>)}
        </select>
        <FieldError message={errors[`${errorPrefix}courtType`]} />
      </label>
      <label className="block">
        <span className="text-xs font-semibold text-stone-600">Mặt sân</span>
        <select className={`${inputClass} mt-1`} aria-label={`Mặt sân${labelSuffix}`} value={court.surfaceType} onChange={(event) => set('surfaceType', event.target.value)}>
          <option value="">Không ghi</option>
          {options.surfaceTypes.map((type) => <option key={type.value} value={type.value}>{type.label}</option>)}
        </select>
        <FieldError message={errors[`${errorPrefix}surfaceType`]} />
      </label>
      <label className="flex h-11 items-center gap-2 text-sm font-semibold text-emerald-950 lg:mt-5">
        <input type="checkbox" className="size-4 accent-emerald-700" checked={court.indoor} onChange={(event) => set('indoor', event.target.checked)} />
        Trong nhà
      </label>
    </div>
  )
}

export { CourtFields }
