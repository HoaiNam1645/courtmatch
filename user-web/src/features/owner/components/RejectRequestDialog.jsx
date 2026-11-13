import { useEffect, useRef, useState } from 'react'
import { X } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { fetchRejectReasons, rejectBookingRequest } from '../../../api/ownerBookingRequests'
import { useApiResource } from '../../../hooks/useApiResource'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { dangerButtonClass, inputClass, secondaryButtonClass, textareaClass } from '../ownerStyles'
import { OwnerError, OwnerLoading } from './OwnerUi'

/** 2.3.14 — chọn lý do trong danh mục rồi từ chối yêu cầu đặt sân. */
function RejectRequestDialog({ item, onClose, onRejected }) {
  const { status, data: reasons, error, reload } = useApiResource(fetchRejectReasons, [])
  const [reasonCode, setReasonCode] = useState('')
  const [note, setNote] = useState('')
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const firstField = useRef(null)

  useEffect(() => {
    function onKey(event) {
      if (event.key === 'Escape' && !submitting) onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose, submitting])

  useEffect(() => {
    if (status === 'success') firstField.current?.focus()
  }, [status])

  const selected = reasons?.find((reason) => reason.code === reasonCode)

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting) return
    const nextErrors = {}
    if (!reasonCode) nextErrors.reasonCode = 'Vui lòng chọn lý do từ chối'
    if (selected?.noteRequired && note.trim().length < 3) nextErrors.note = 'Vui lòng nhập ghi chú (ít nhất 3 ký tự) khi chọn lý do khác'
    if (note.length > 500) nextErrors.note = 'Ghi chú tối đa 500 ký tự'
    setErrors(nextErrors)
    setFormError('')
    if (Object.keys(nextErrors).length > 0) return

    setSubmitting(true)
    try {
      await rejectBookingRequest(item.bookingId, { reasonCode, note })
      onRejected(item)
    } catch (failure) {
      if (failure instanceof ApiError) {
        setErrors(failure.fieldErrors ?? {})
        setFormError(failure.message)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
      setSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-black/50 px-4" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !submitting) onClose() }}>
      <div role="dialog" aria-modal="true" aria-labelledby="reject-title" className="w-full max-w-md rounded-xl bg-white p-5 shadow-2xl">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 id="reject-title" className="text-lg font-semibold text-emerald-950">Từ chối yêu cầu {item.bookingCode}</h2>
            <p className="mt-1 text-sm font-medium text-stone-500">Khung giờ được giải phóng ngay và người chơi sẽ thấy lý do bạn chọn.</p>
          </div>
          <button type="button" className="grid size-9 shrink-0 place-items-center rounded-lg text-stone-500 hover:bg-stone-100" aria-label="Đóng" onClick={onClose} disabled={submitting}><X className="size-5" /></button>
        </div>

        {status === 'loading' && <div className="mt-4"><OwnerLoading rows={1} /></div>}
        {status === 'error' && <div className="mt-4"><OwnerError message={error?.message} onRetry={reload} /></div>}
        {status === 'success' && (
          <form className="mt-4 grid gap-4" noValidate onSubmit={handleSubmit}>
            <label className="block">
              <span className="text-sm font-semibold text-emerald-950">Lý do <span className="text-red-600">*</span></span>
              <select ref={firstField} className={`${inputClass} mt-2`} aria-label="Lý do từ chối" value={reasonCode} onChange={(event) => setReasonCode(event.target.value)} aria-invalid={Boolean(errors.reasonCode)}>
                <option value="">Chọn lý do</option>
                {reasons.map((reason) => <option key={reason.code} value={reason.code}>{reason.label}</option>)}
              </select>
              <FieldError message={errors.reasonCode} />
            </label>
            <label className="block">
              <span className="text-sm font-semibold text-emerald-950">Ghi chú{selected?.noteRequired ? <span className="text-red-600"> *</span> : ' (không bắt buộc)'}</span>
              <textarea className={`${textareaClass} mt-2 h-24`} aria-label="Ghi chú từ chối" maxLength={500} value={note} onChange={(event) => setNote(event.target.value)} aria-invalid={Boolean(errors.note)} />
              <FieldError message={errors.note} />
            </label>
            <FormAlert message={formError} />
            <div className="flex justify-end gap-2">
              <button type="button" className={secondaryButtonClass} onClick={onClose} disabled={submitting}>Hủy</button>
              <button type="submit" className={dangerButtonClass} disabled={submitting}>{submitting ? 'Đang từ chối...' : 'Từ chối yêu cầu'}</button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}

export { RejectRequestDialog }
