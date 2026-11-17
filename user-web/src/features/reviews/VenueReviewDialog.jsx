import { LoaderCircle, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { ApiError } from '../../api/client'
import { FieldError, FormAlert } from '../../shared/FormFeedback'
import { primaryButtonClass, secondaryButtonClass, textareaClass } from '../owner/ownerStyles'
import { StarPicker } from './StarRating'

const MAX_COMMENT = 1000
const LABELS = ['', 'Rất tệ', 'Tệ', 'Bình thường', 'Tốt', 'Rất tốt']

/**
 * Biểu mẫu viết / sửa đánh giá sân (2.3.77, 2.3.78).
 *
 * @param initial đánh giá hiện có khi sửa; null khi viết mới
 * @param onSubmit nhận { rating, comment }, trả về Promise; ném ApiError thì hiện lỗi ngay trong biểu mẫu
 */
export function VenueReviewDialog({ venueName, initial = null, onSubmit, onClose }) {
  const [rating, setRating] = useState(initial?.rating ?? 0)
  const [comment, setComment] = useState(initial?.comment ?? '')
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    function onKey(event) {
      if (event.key === 'Escape' && !busy) onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose, busy])

  async function submit(event) {
    event.preventDefault()
    if (busy) return
    if (rating < 1) return setErrors({ rating: 'Vui lòng chọn số sao' })
    if (comment.length > MAX_COMMENT) return setErrors({ comment: `Nhận xét tối đa ${MAX_COMMENT} ký tự` })
    setErrors({})
    setFormError('')
    setBusy(true)
    try {
      await onSubmit({ rating, comment: comment.trim() })
    } catch (failure) {
      if (failure instanceof ApiError) {
        setErrors(failure.fieldErrors ?? {})
        setFormError(failure.message)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
      setBusy(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose() }}>
      <form noValidate onSubmit={submit} role="dialog" aria-modal="true" aria-label={initial ? 'Sửa đánh giá sân' : 'Đánh giá sân'} data-testid="review-dialog"
        className="w-full max-w-md rounded-2xl bg-white p-5 shadow-2xl">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold text-emerald-950">{initial ? 'Sửa đánh giá' : 'Đánh giá sân'}</h2>
            <p className="mt-1 text-sm font-medium text-stone-500">{venueName}</p>
          </div>
          <button type="button" className="grid size-9 place-items-center rounded-lg text-stone-500 hover:bg-stone-100" aria-label="Đóng" onClick={onClose} disabled={busy}><X className="size-5" /></button>
        </div>

        <div className="mt-4">
          <StarPicker value={rating} onChange={(value) => { setRating(value); setErrors((current) => ({ ...current, rating: undefined })) }} disabled={busy} />
          <p className="mt-1 text-xs font-semibold text-amber-700" data-testid="review-rating-label">{LABELS[rating] || 'Chọn số sao'}</p>
          <FieldError message={errors.rating} />
        </div>

        <label className="mt-4 block">
          <span className="text-sm font-semibold text-emerald-950">Nhận xét <span className="font-medium text-stone-400">(không bắt buộc)</span></span>
          <textarea className={`${textareaClass} mt-2 h-28`} aria-label="Nhận xét" data-testid="review-comment" value={comment} disabled={busy}
            placeholder="Chia sẻ trải nghiệm của bạn về sân, mặt sân, dịch vụ..." onChange={(event) => { setComment(event.target.value); setErrors((current) => ({ ...current, comment: undefined })) }} aria-invalid={Boolean(errors.comment)} />
          <span className={`mt-1 block text-right text-xs font-medium ${comment.length > MAX_COMMENT ? 'text-red-600' : 'text-stone-400'}`} data-testid="review-counter">{comment.length}/{MAX_COMMENT}</span>
          <FieldError message={errors.comment} />
        </label>

        <div className="mt-3"><FormAlert message={formError} /></div>
        <div className="mt-4 flex justify-end gap-2">
          <button type="button" className={secondaryButtonClass} onClick={onClose} disabled={busy}>Hủy</button>
          <button type="submit" className={primaryButtonClass} data-testid="review-submit" disabled={busy}>
            {busy && <LoaderCircle className="size-4 animate-spin" />}{busy ? 'Đang gửi...' : initial ? 'Lưu thay đổi' : 'Gửi đánh giá'}
          </button>
        </div>
      </form>
    </div>
  )
}
