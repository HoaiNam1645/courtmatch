import { useEffect, useRef, useState } from 'react'
import { X } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { submitCommissionPayment } from '../../../api/ownerCommissionPayments'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { formatCurrency } from '../../../shared/utils'
import { formatIsoDate, todayInVietnam } from '../ownerDates'
import { inputClass, primaryButtonClass, secondaryButtonClass, textareaClass } from '../ownerStyles'

/** Cùng điều kiện với CreateCommissionPaymentRequest ở backend. */
function validate({ amount, paidOn, referenceCode, proofUrl, note }, maxAmount) {
  const errors = {}
  const value = Number(amount)
  if (!amount || !Number.isFinite(value) || value <= 0) errors.amount = 'Vui lòng nhập số tiền lớn hơn 0'
  else if (!Number.isInteger(value)) errors.amount = 'Số tiền phải là số nguyên (đồng)'
  else if (maxAmount != null && value > maxAmount) errors.amount = `Tối đa ${formatCurrency(maxAmount)}`
  if (!paidOn) errors.paidOn = 'Vui lòng chọn ngày thanh toán'
  else if (paidOn > todayInVietnam()) errors.paidOn = 'Ngày thanh toán không được ở tương lai'
  const reference = referenceCode.trim()
  if (reference.length < 3) errors.referenceCode = 'Mã tham chiếu từ 3 đến 100 ký tự'
  else if (reference.length > 100) errors.referenceCode = 'Mã tham chiếu từ 3 đến 100 ký tự'
  if (proofUrl.trim() && !/^https?:\/\/\S+$/.test(proofUrl.trim())) errors.proofUrl = 'Đường dẫn phải bắt đầu bằng http:// hoặc https://'
  if (note.length > 500) errors.note = 'Ghi chú tối đa 500 ký tự'
  return errors
}

/**
 * 2.3.24 — gửi thông tin đã thanh toán hoa hồng cho Courtly.
 * settlement: kỳ đối soát đang trả (null = trả chung vào công nợ hiện tại); maxAmount: số tối đa nên nhập.
 */
function CommissionPaymentDialog({ settlement = null, maxAmount = null, onClose, onSubmitted }) {
  const [form, setForm] = useState({ amount: maxAmount ? String(maxAmount) : '', paidOn: todayInVietnam(), referenceCode: '', proofUrl: '', note: '' })
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const firstField = useRef(null)

  useEffect(() => {
    firstField.current?.focus()
    function onKey(event) {
      if (event.key === 'Escape' && !submitting) onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose, submitting])

  function update(field, value) {
    setForm((current) => ({ ...current, [field]: value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting) return
    const nextErrors = validate(form, maxAmount)
    setErrors(nextErrors)
    setFormError('')
    if (Object.keys(nextErrors).length > 0) return
    setSubmitting(true)
    try {
      const payment = await submitCommissionPayment({ ...form, settlementId: settlement?.id ?? null })
      onSubmitted(payment)
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
    <div className="fixed inset-0 z-[60] grid place-items-center overflow-y-auto bg-black/50 px-4 py-6" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !submitting) onClose() }}>
      <div role="dialog" aria-modal="true" aria-labelledby="commission-payment-title" className="w-full max-w-md rounded-xl bg-white p-5 shadow-2xl" data-testid="commission-payment-dialog">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 id="commission-payment-title" className="text-lg font-semibold text-emerald-950">Gửi thông tin thanh toán hoa hồng</h2>
            <p className="mt-1 text-sm font-medium text-stone-500">
              {settlement ? `Kỳ ${formatIsoDate(settlement.periodFrom)} – ${formatIsoDate(settlement.periodTo)}. ` : 'Thanh toán vào công nợ hiện tại. '}
              Công nợ chỉ giảm sau khi Courtly xác nhận đã nhận tiền.
            </p>
          </div>
          <button type="button" className="grid size-9 shrink-0 place-items-center rounded-lg text-stone-500 hover:bg-stone-100" aria-label="Đóng" onClick={onClose} disabled={submitting}><X className="size-5" /></button>
        </div>

        <form className="mt-4 grid gap-4" noValidate onSubmit={handleSubmit}>
          <label className="block">
            <span className="text-sm font-semibold text-emerald-950">Số tiền đã thanh toán (đ) <span className="text-red-600">*</span></span>
            <input ref={firstField} type="number" inputMode="numeric" min="1" className={`${inputClass} mt-2`} aria-label="Số tiền đã thanh toán" value={form.amount} onChange={(event) => update('amount', event.target.value)} aria-invalid={Boolean(errors.amount)} />
            {maxAmount != null && <span className="mt-1 block text-xs font-medium text-stone-500">Tối đa {formatCurrency(maxAmount)}</span>}
            <FieldError message={errors.amount} />
          </label>
          <label className="block">
            <span className="text-sm font-semibold text-emerald-950">Ngày thanh toán <span className="text-red-600">*</span></span>
            <input type="date" max={todayInVietnam()} className={`${inputClass} mt-2`} aria-label="Ngày thanh toán" value={form.paidOn} onChange={(event) => update('paidOn', event.target.value)} aria-invalid={Boolean(errors.paidOn)} />
            <FieldError message={errors.paidOn} />
          </label>
          <label className="block">
            <span className="text-sm font-semibold text-emerald-950">Mã tham chiếu giao dịch <span className="text-red-600">*</span></span>
            <input className={`${inputClass} mt-2`} aria-label="Mã tham chiếu giao dịch" maxLength={100} value={form.referenceCode} onChange={(event) => update('referenceCode', event.target.value)} aria-invalid={Boolean(errors.referenceCode)} />
            <FieldError message={errors.referenceCode} />
          </label>
          <label className="block">
            <span className="text-sm font-semibold text-emerald-950">Đường dẫn ảnh chứng từ (không bắt buộc)</span>
            <input className={`${inputClass} mt-2`} aria-label="Đường dẫn ảnh chứng từ" placeholder="https://..." maxLength={1000} value={form.proofUrl} onChange={(event) => update('proofUrl', event.target.value)} aria-invalid={Boolean(errors.proofUrl)} />
            <FieldError message={errors.proofUrl} />
          </label>
          <label className="block">
            <span className="text-sm font-semibold text-emerald-950">Ghi chú (không bắt buộc)</span>
            <textarea className={`${textareaClass} mt-2 h-20`} aria-label="Ghi chú thanh toán" maxLength={500} value={form.note} onChange={(event) => update('note', event.target.value)} aria-invalid={Boolean(errors.note)} />
            <FieldError message={errors.note} />
          </label>
          <FormAlert message={formError} />
          <div className="flex justify-end gap-2">
            <button type="button" className={secondaryButtonClass} onClick={onClose} disabled={submitting}>Hủy</button>
            <button type="submit" className={primaryButtonClass} disabled={submitting}>{submitting ? 'Đang gửi...' : 'Gửi thông tin thanh toán'}</button>
          </div>
        </form>
      </div>
    </div>
  )
}

export { CommissionPaymentDialog }
