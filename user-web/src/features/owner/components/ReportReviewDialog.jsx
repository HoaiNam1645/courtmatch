import { Flag, LoaderCircle, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { ApiError } from '../../../api/client'
import { fetchReviewReportReasons, reportVenueReview } from '../../../api/venueReviews'
import { useApiResource } from '../../../hooks/useApiResource'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { classNames } from '../../../shared/utils'
import { primaryButtonClass, secondaryButtonClass, textareaClass } from '../ownerStyles'
import { OwnerError, OwnerLoading } from './OwnerUi'

const MAX_DESCRIPTION = 1000

/** Báo cáo một đánh giá có nội dung không phù hợp (2.3.83). Đánh giá vẫn hiển thị cho tới khi quản trị viên kết luận. */
export function ReportReviewDialog({ review, onClose, onReported }) {
  const reasons = useApiResource(fetchReviewReportReasons, [])
  const [reason, setReason] = useState('')
  const [description, setDescription] = useState('')
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [busy, setBusy] = useState(false)
  const selected = (reasons.data ?? []).find((item) => item.code === reason)

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
    const text = description.trim()
    if (!reason) return setErrors({ reason: 'Vui lòng chọn lý do báo cáo' })
    if (selected?.descriptionRequired && text.length < 10) return setErrors({ description: 'Vui lòng mô tả lý do (ít nhất 10 ký tự)' })
    if (text.length > MAX_DESCRIPTION) return setErrors({ description: `Mô tả tối đa ${MAX_DESCRIPTION} ký tự` })
    setErrors({})
    setFormError('')
    setBusy(true)
    try {
      await reportVenueReview(review.id, { reason, description: text })
      onReported()
    } catch (failure) {
      if (failure instanceof ApiError) {
        setErrors(failure.fieldErrors ?? {})
        setFormError(failure.message)
        if (failure.status === 409) onReported(failure.message)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
      setBusy(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-black/45 p-4" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose() }}>
      <form noValidate onSubmit={submit} role="dialog" aria-modal="true" aria-label="Báo cáo đánh giá" data-testid="report-dialog" className="w-full max-w-lg rounded-2xl bg-white p-5 shadow-2xl">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold text-emerald-950">Báo cáo đánh giá không phù hợp</h2>
            <p className="mt-1 text-sm font-medium text-stone-500">Đánh giá của {review.reviewerName}. Chỉ báo cáo lời lẽ xúc phạm hoặc sai sự thật; không dùng cho tranh chấp tiền đặt sân.</p>
          </div>
          <button type="button" className="grid size-9 place-items-center rounded-lg text-stone-500 hover:bg-stone-100" aria-label="Đóng" onClick={onClose} disabled={busy}><X className="size-5" /></button>
        </div>
        <blockquote className="mt-3 rounded-lg bg-stone-50 p-3 text-sm font-medium text-stone-600">{review.comment || 'Khách chỉ chấm sao, không viết nhận xét.'}</blockquote>

        {reasons.status === 'loading' && <div className="mt-4"><OwnerLoading rows={1} /></div>}
        {reasons.status === 'error' && <div className="mt-4"><OwnerError message={reasons.error?.message} onRetry={reasons.reload} /></div>}
        {reasons.data && (
          <fieldset className="mt-4 grid gap-2" disabled={busy}>
            <legend className="text-sm font-semibold text-emerald-950">Lý do báo cáo</legend>
            {reasons.data.map((item) => (
              <label key={item.code} className={classNames('flex cursor-pointer items-start gap-3 rounded-lg border p-3', reason === item.code ? 'border-emerald-600 bg-emerald-50/60' : 'border-stone-200')}>
                <input type="radio" name="report-reason" value={item.code} checked={reason === item.code} data-testid={`report-reason-${item.code}`} className="mt-1" onChange={() => { setReason(item.code); setErrors({}) }} />
                <span><span className="block text-sm font-semibold text-emerald-950">{item.label}</span><span className="block text-xs font-medium text-stone-500">{item.hint}</span></span>
              </label>
            ))}
            <FieldError message={errors.reason} />
          </fieldset>
        )}

        <label className="mt-3 block">
          <span className="text-sm font-semibold text-emerald-950">Mô tả thêm {selected?.descriptionRequired ? <span className="text-red-600">*</span> : <span className="font-medium text-stone-400">(không bắt buộc)</span>}</span>
          <textarea className={`${textareaClass} mt-2 h-24`} aria-label="Mô tả thêm" data-testid="report-description" value={description} disabled={busy} onChange={(event) => { setDescription(event.target.value); setErrors((current) => ({ ...current, description: undefined })) }} aria-invalid={Boolean(errors.description)} />
          <span className={classNames('mt-1 block text-right text-xs font-medium', description.length > MAX_DESCRIPTION ? 'text-red-600' : 'text-stone-400')}>{description.length}/{MAX_DESCRIPTION}</span>
          <FieldError message={errors.description} />
        </label>

        <div className="mt-3"><FormAlert message={formError} /></div>
        <div className="mt-4 flex justify-end gap-2">
          <button type="button" className={secondaryButtonClass} onClick={onClose} disabled={busy}>Hủy</button>
          <button type="submit" className={primaryButtonClass} data-testid="report-submit" disabled={busy || !reasons.data}>
            {busy ? <LoaderCircle className="size-4 animate-spin" /> : <Flag className="size-4" />}{busy ? 'Đang gửi...' : 'Gửi báo cáo'}
          </button>
        </div>
      </form>
    </div>
  )
}
