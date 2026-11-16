import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { createAdminSettlement } from '../../../api/adminSettlements'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { formatIsoDate, rangeProblem, shiftIsoDate, todayInVietnam } from '../../owner/ownerDates'
import { inputClass, primaryButtonClass, secondaryButtonClass, textareaClass } from '../../owner/ownerStyles'

/** Chốt kỳ (2.3.45): gom hoa hồng chưa đối soát của một chủ sân trong khoảng ngày thành một kỳ công nợ. */
function SettlementDialog({ owners, onClose, onCreated }) {
  const today = todayInVietnam()
  const [form, setForm] = useState({ ownerId: '', periodFrom: shiftIsoDate(today, -6), periodTo: today, note: '' })
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

  function change(patch) {
    setForm((current) => ({ ...current, ...patch }))
    setErrors({})
    setFormError('')
  }

  async function submit(event) {
    event.preventDefault()
    if (busy) return
    const problems = {}
    if (!form.ownerId) problems.ownerId = 'Chọn chủ sân.'
    if (!form.periodFrom) problems.periodFrom = 'Chọn ngày bắt đầu.'
    if (!form.periodTo) problems.periodTo = 'Chọn ngày kết thúc.'
    if (form.periodFrom && form.periodTo) {
      const problem = rangeProblem(form.periodFrom, form.periodTo)
      if (problem) problems.periodTo = problem
      else if (form.periodTo > today) problems.periodTo = 'Ngày kết thúc không được sau hôm nay.'
    }
    if (form.note.length > 500) problems.note = 'Ghi chú tối đa 500 ký tự.'
    setErrors(problems)
    if (Object.keys(problems).length > 0) return
    setBusy(true)
    try {
      const created = await createAdminSettlement(form)
      onCreated(created, `Đã chốt kỳ ${formatIsoDate(created.periodFrom)} – ${formatIsoDate(created.periodTo)} của ${created.ownerName} (${created.bookingCount} booking).`)
    } catch (failure) {
      if (failure instanceof ApiError) {
        setErrors({ ownerId: failure.fieldErrors?.ownerId, periodFrom: failure.fieldErrors?.periodFrom, periodTo: failure.fieldErrors?.periodTo, note: failure.fieldErrors?.note })
        setFormError(failure.status === 409
          ? 'Không có hoa hồng chưa đối soát nào trong khoảng này, hoặc chủ sân đã có kỳ cùng khoảng thời gian. Hãy chọn khoảng khác.'
          : failure.message)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
      setBusy(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-black/40 p-4" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose() }}>
      <form role="dialog" aria-modal="true" aria-label="Chốt kỳ công nợ" noValidate onSubmit={submit} className="w-full max-w-lg rounded-xl bg-white p-5 shadow-2xl" data-testid="settlement-dialog">
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-lg font-semibold text-slate-900">Chốt kỳ công nợ</h2>
          <button type="button" className="grid size-9 place-items-center rounded-lg text-slate-500 hover:bg-slate-100" aria-label="Đóng" onClick={onClose} disabled={busy}><X className="size-5" /></button>
        </div>
        <p className="mt-2 text-sm font-medium text-slate-600">Gom hoa hồng chưa đối soát được ghi nhận trong khoảng ngày đã chọn (giờ Việt Nam) thành một kỳ. Hoa hồng đã thuộc kỳ khác, đang tranh chấp hoặc được miễn không được gom.</p>
        <div className="mt-4 grid gap-4">
          <label className="block">
            <span className="text-sm font-semibold text-slate-900">Chủ sân <span className="text-red-600">*</span></span>
            <select className={`${inputClass} mt-2`} aria-label="Chủ sân" value={form.ownerId} onChange={(event) => change({ ownerId: event.target.value })} aria-invalid={Boolean(errors.ownerId)} data-testid="settlement-form-owner">
              <option value="">Chọn chủ sân</option>
              {owners.map((owner) => <option key={owner.id} value={owner.id}>{owner.fullName} · {owner.email}</option>)}
            </select>
            <FieldError message={errors.ownerId} />
          </label>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block">
              <span className="text-sm font-semibold text-slate-900">Từ ngày <span className="text-red-600">*</span></span>
              <input type="date" className={`${inputClass} mt-2`} aria-label="Kỳ từ ngày" value={form.periodFrom} onChange={(event) => change({ periodFrom: event.target.value })} aria-invalid={Boolean(errors.periodFrom)} data-testid="settlement-form-from" />
              <FieldError message={errors.periodFrom} />
            </label>
            <label className="block">
              <span className="text-sm font-semibold text-slate-900">Đến ngày <span className="text-red-600">*</span></span>
              <input type="date" className={`${inputClass} mt-2`} aria-label="Kỳ đến ngày" value={form.periodTo} max={today} onChange={(event) => change({ periodTo: event.target.value })} aria-invalid={Boolean(errors.periodTo)} data-testid="settlement-form-to" />
              <FieldError message={errors.periodTo} />
            </label>
          </div>
          <label className="block">
            <span className="text-sm font-semibold text-slate-900">Ghi chú</span>
            <textarea className={`${textareaClass} mt-2 h-16`} aria-label="Ghi chú kỳ" maxLength={500} value={form.note} onChange={(event) => change({ note: event.target.value })} />
            <FieldError message={errors.note} />
          </label>
        </div>
        <div className="mt-4"><FormAlert message={formError} /></div>
        <div className="mt-4 flex justify-end gap-2">
          <button type="button" className={secondaryButtonClass} disabled={busy} onClick={onClose}>Hủy</button>
          <button type="submit" className={primaryButtonClass} disabled={busy} data-testid="settlement-form-submit">{busy ? 'Đang chốt...' : 'Chốt kỳ'}</button>
        </div>
      </form>
    </div>
  )
}

export { SettlementDialog }
