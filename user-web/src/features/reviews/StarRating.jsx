import { Star } from 'lucide-react'
import { classNames } from '../../shared/utils'

/** Hàng sao chỉ đọc (điểm 1-5). */
export function StarRow({ value, className }) {
  return (
    <span className={classNames('inline-flex items-center gap-0.5', className)} role="img" aria-label={`${value} trên 5 sao`}>
      {[1, 2, 3, 4, 5].map((star) => <Star key={star} className={classNames('size-4', star <= value ? 'fill-amber-400 text-amber-400' : 'text-stone-300')} />)}
    </span>
  )
}

/** Chọn số sao: nhóm nút radio, bàn phím tab + Enter/Space. */
export function StarPicker({ value, onChange, disabled }) {
  return (
    <div className="flex gap-1" role="radiogroup" aria-label="Số sao">
      {[1, 2, 3, 4, 5].map((star) => (
        <button key={star} type="button" role="radio" aria-checked={value === star} aria-label={`${star} sao`} disabled={disabled} data-testid={`review-star-${star}`}
          onClick={() => onChange(star)} className="grid size-11 place-items-center rounded-lg hover:bg-amber-50 disabled:opacity-60">
          <Star className={classNames('size-7', star <= value ? 'fill-amber-400 text-amber-400' : 'text-stone-300')} />
        </button>
      ))}
    </div>
  )
}
