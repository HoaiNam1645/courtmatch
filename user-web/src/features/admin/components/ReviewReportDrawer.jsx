import { useEffect, useState } from 'react'
import { CheckCircle2, Star, X } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { concludeReviewReport, fetchReviewReport, HANDLING_LABEL, reportStatusMeta, reviewStatusMeta, startReviewReport } from '../../../api/adminReviewReports'
import { useApiResource } from '../../../hooks/useApiResource'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { classNames } from '../../../shared/utils'
import { OwnerError, OwnerLoading } from '../../owner/components/OwnerUi'
import { formatDateTime } from '../../owner/ownerDates'
import { dangerButtonClass, primaryButtonClass, secondaryButtonClass, textareaClass } from '../../owner/ownerStyles'

const FORMS = {
  hide: { title: 'Gỡ đánh giá', submit: 'Gỡ đánh giá', done: 'Đã gỡ đánh giá khỏi hồ sơ người chơi.', danger: true, hint: 'Đánh giá sẽ không còn hiển thị ở hồ sơ và không tính vào điểm trung bình.' },
  keep: { title: 'Giữ nguyên đánh giá', submit: 'Giữ nguyên và bác bỏ báo cáo', done: 'Đã giữ nguyên đánh giá và bác bỏ báo cáo.', danger: false, hint: 'Đánh giá được giữ như hiện tại, báo cáo bị bác bỏ.' },
  restore: { title: 'Khôi phục đánh giá', submit: 'Khôi phục đánh giá', done: 'Đã khôi phục đánh giá, đánh giá hiển thị lại.', danger: false, hint: 'Đánh giá hiển thị lại ở hồ sơ và tính lại vào điểm trung bình.' },
}

/** Sao đánh giá: số sao tô đậm, kèm chữ cho trình đọc màn hình. */
function Stars({ rating }) {
  return (
    <span className="inline-flex items-center gap-0.5" role="img" aria-label={`${rating} trên 5 sao`}>
      {[1, 2, 3, 4, 5].map((value) => <Star key={value} className={classNames('size-4', value <= rating ? 'fill-amber-400 text-amber-400' : 'text-slate-300')} />)}
    </span>
  )
}

function NoteForm({ reportId, action, onDone, onCancel }) {
  const form = FORMS[action]
  const [note, setNote] = useState('')
  const [error, setError] = useState('')
  const [formError, setFormError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(event) {
    event.preventDefault()
    if (busy) return
    const problem = note.trim().length < 3 ? 'Vui lòng nhập ghi chú (ít nhất 3 ký tự)' : note.length > 1000 ? 'Ghi chú tối đa 1000 ký tự' : ''
    setError(problem)
    setFormError('')
    if (problem) return
    setBusy(true)
    try {
      onDone(await concludeReviewReport(reportId, action, note), form.done)
    } catch (failure) {
      if (failure instanceof ApiError) {
        setError(failure.fieldErrors?.note ?? '')
        setFormError(failure.message)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
      setBusy(false)
    }
  }

  return (
    <form className="grid gap-3" noValidate onSubmit={submit} data-testid={`review-${action}-form`}>
      <p className="text-sm font-semibold text-slate-900">{form.title}</p>
      <p className="text-xs font-medium text-slate-500">{form.hint}</p>
      <label className="block">
        <span className="text-sm font-semibold text-slate-900">Ghi chú <span className="text-red-600">*</span></span>
        <textarea className={`${textareaClass} mt-2 h-24`} aria-label="Ghi chú kết luận" maxLength={1000} value={note} onChange={(event) => setNote(event.target.value)} aria-invalid={Boolean(error)} data-testid="review-note" />
        <FieldError message={error} />
      </label>
      <FormAlert message={formError} />
      <div className="flex justify-end gap-2">
        <button type="button" className={secondaryButtonClass} disabled={busy} onClick={onCancel}>Hủy</button>
        <button type="submit" className={form.danger ? dangerButtonClass : primaryButtonClass} disabled={busy} data-testid="review-conclude-submit">{busy ? 'Đang lưu...' : form.submit}</button>
      </div>
    </form>
  )
}

/** Chi tiết báo cáo về đánh giá đối tác (2.3.37). */
function ReviewReportDrawer({ reportId, onClose, onChanged }) {
  const { status, data, error, reload } = useApiResource(() => fetchReviewReport(reportId), [reportId])
  const [action, setAction] = useState(null)
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState('')

  useEffect(() => {
    function onKey(event) {
      if (event.key === 'Escape' && !busy) onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose, busy])

  async function start() {
    if (busy) return
    setBusy(true)
    setFormError('')
    try {
      await startReviewReport(reportId)
      reload()
      onChanged('Đã nhận xử lý báo cáo.')
    } catch (failure) {
      setFormError(failure instanceof ApiError ? failure.message : 'Đã có lỗi xảy ra, vui lòng thử lại.')
      if (failure instanceof ApiError && failure.status === 409) reload()
    } finally {
      setBusy(false)
    }
  }

  const meta = data ? reportStatusMeta(data.status) : null
  const review = data?.review
  const reviewMeta = review ? reviewStatusMeta(review.status) : null
  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/40" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose() }}>
      <aside role="dialog" aria-modal="true" aria-label="Chi tiết báo cáo đánh giá" className="h-full w-full max-w-lg overflow-y-auto bg-white p-5 shadow-2xl" data-testid="review-drawer">
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-lg font-semibold text-slate-900">Báo cáo đánh giá</h2>
          <button type="button" className="grid size-9 place-items-center rounded-lg text-slate-500 hover:bg-slate-100" aria-label="Đóng" onClick={onClose} disabled={busy}><X className="size-5" /></button>
        </div>
        {status === 'loading' && <div className="mt-4"><OwnerLoading rows={2} /></div>}
        {status === 'error' && <div className="mt-4"><OwnerError message={error?.message} onRetry={reload} /></div>}
        {status === 'success' && (
          <div className="mt-4 grid gap-5">
            <section>
              <div className="flex flex-wrap items-center gap-2">
                <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', meta.className)} data-testid="review-drawer-status">{meta.label}</span>
                <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-700" data-testid="review-drawer-reason">{data.reason}</span>
              </div>
              <p className="mt-3 whitespace-pre-line text-sm font-medium text-slate-700" data-testid="review-drawer-description">{data.description || 'Người báo cáo không nhập mô tả.'}</p>
              <p className="mt-2 text-xs font-medium text-slate-500">{data.reporterName} báo cáo lúc {formatDateTime(data.createdAt)} · tổng {data.totalReports} báo cáo về đánh giá này</p>
            </section>

            <section className="rounded-xl border border-red-100 bg-red-50/40 p-4" data-testid="review-drawer-review">
              <p className="text-xs font-bold uppercase tracking-wide text-red-400">Đánh giá bị báo cáo</p>
              {review.exists ? (
                <>
                  <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
                    <Stars rating={review.rating} />
                    <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', reviewMeta.className)} data-testid="review-current-status">{reviewMeta.label}</span>
                  </div>
                  <p className="mt-2 whitespace-pre-line text-sm font-medium text-slate-800" data-testid="review-comment">{review.comment || 'Không có nhận xét bằng chữ.'}</p>
                  <p className="mt-2 text-xs font-medium text-slate-500">{review.reviewerName} đánh giá {review.reviewedName}{review.createdAt ? ` · ${formatDateTime(review.createdAt)}` : ''}</p>
                </>
              ) : <p className="mt-2 text-sm font-medium text-slate-500" data-testid="review-missing">Đánh giá không còn tồn tại.</p>}
            </section>

            {data.related.length > 0 && (
              <section>
                <h3 className="text-sm font-semibold text-slate-900">Báo cáo khác về đánh giá này</h3>
                <ul className="mt-2 grid gap-2" data-testid="review-related">
                  {data.related.map((item) => (
                    <li key={item.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-slate-200 p-3 text-sm">
                      <span className="font-semibold text-slate-800">{item.reason} <span className="font-medium text-slate-500">· {item.reporterName}</span></span>
                      <span className={classNames('rounded-full px-2 py-0.5 text-xs font-bold', reportStatusMeta(item.status).className)}>{reportStatusMeta(item.status).label}</span>
                    </li>
                  ))}
                </ul>
              </section>
            )}

            {data.handling && (
              <section className="rounded-xl border border-slate-200 bg-slate-50 p-4" data-testid="review-handling">
                <h3 className="flex items-center gap-2 text-sm font-semibold text-slate-900"><CheckCircle2 className="size-4 text-emerald-600" />Kết luận</h3>
                <p className="mt-2 text-sm font-semibold text-slate-800" data-testid="review-handling-action">{HANDLING_LABEL[data.handling.action] ?? 'Đã kết luận'}</p>
                <p className="mt-1 whitespace-pre-line text-sm font-medium text-slate-700" data-testid="review-handling-note">{data.handling.note}</p>
                <p className="mt-2 text-xs font-medium text-slate-500">{data.handling.handledBy} · {formatDateTime(data.handling.handledAt)}</p>
              </section>
            )}

            <section>
              <FormAlert message={action ? '' : formError} />
              {data.involved && <p className="rounded-lg bg-amber-50 px-3 py-2 text-xs font-semibold text-amber-900" data-testid="review-involved">Bạn là một bên của đánh giá này nên không thể xử lý báo cáo. Hãy nhờ quản trị viên khác.</p>}
              {data.canStart && <button type="button" className={`${primaryButtonClass} mt-2`} data-testid="review-start" disabled={busy} onClick={start}>{busy ? 'Đang nhận...' : 'Nhận xử lý'}</button>}
              {data.canConclude && !action && (
                <div className="mt-2 flex flex-wrap gap-2">
                  {data.canHide && <button type="button" className={dangerButtonClass} data-testid="review-hide-open" onClick={() => { setAction('hide'); setFormError('') }}>Gỡ đánh giá</button>}
                  <button type="button" className={secondaryButtonClass} data-testid="review-keep-open" onClick={() => { setAction('keep'); setFormError('') }}>Giữ nguyên đánh giá</button>
                </div>
              )}
              {data.canRestore && !action && <button type="button" className={`${secondaryButtonClass} mt-2`} data-testid="review-restore-open" onClick={() => { setAction('restore'); setFormError('') }}>Khôi phục đánh giá</button>}
              {action && <NoteForm reportId={reportId} action={action} onCancel={() => setAction(null)} onDone={(updated, message) => { setAction(null); reload(); onChanged(message) }} />}
            </section>
          </div>
        )}
      </aside>
    </div>
  )
}

export { ReviewReportDrawer, Stars }
