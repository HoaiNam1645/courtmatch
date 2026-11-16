import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { adjustCommission } from '../../../api/adminCommissionTransactions'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { classNames, formatCurrency } from '../../../shared/utils'
import { formatIsoDate } from '../../owner/ownerDates'
import { inputClass, primaryButtonClass, secondaryButtonClass, textareaClass } from '../../owner/ownerStyles'

/**
 * Điều chỉnh công nợ (2.3.51) bằng cách điều chỉnh hoa hồng của một booking: nhập số tiền mới chủ sân phải trả và lý do.
 * Hoa hồng thuộc kỳ thì số phải thu của kỳ đổi theo; mọi lần điều chỉnh được lưu vào lịch sử cùng lý do.
 */
function AdjustCommissionDialog({ detail, onClose, onDone }) {
  const { commission, settlement } = detail
  const [amount, setAmount] = useState(String(commission.commissionAmount))
  const [reason, setReason] = useState('')
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

  const valid = /^\d+$/.test(amount.trim()) && amount.trim().length <= 10
  const delta = valid ? Number(amount) - commission.commissionAmount : null

  async function submit(event) {
    event.preventDefault()
    if (busy) return
    const problems = {}
    if (!valid) problems.newAmount = 'Nhập số tiền là số nguyên đồng, không âm.'
    else if (delta === 0) problems.newAmount = 'Số tiền mới phải khác số hiện tại.'
    if (reason.trim().length < 5) problems.reason = 'Nhập lý do điều chỉnh (ít nhất 5 ký tự).'
    else if (reason.length > 500) problems.reason = 'Lý do tối đa 500 ký tự.'
    setErrors(problems)
    if (Object.keys(problems).length > 0) return
    setBusy(true)
    setFormError('')
    try {
      const result = await adjustCommission(commission.id, { newAmount: amount, reason })
      onDone(result, `Đã điều chỉnh hoa hồng ${detail.booking.bookingCode} từ ${formatCurrency(commission.commissionAmount)} thành ${formatCurrency(result.commission.commissionAmount)}.`)
    } catch (failure) {
      if (failure instanceof ApiError) {
        setErrors({ newAmount: failure.fieldErrors?.newAmount, reason: failure.fieldErrors?.reason })
        setFormError(failure.message)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
      setBusy(false)
    }
  }

  return (
    <div className="fixed inset-0 z-[60] grid place-items-center overflow-y-auto bg-black/40 p-4" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose() }}>
      <form role="dialog" aria-modal="true" aria-label="Điều chỉnh hoa hồng" noValidate onSubmit={submit} className="w-full max-w-lg rounded-xl bg-white p-5 shadow-2xl" data-testid="adjust-dialog">
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-lg font-semibold text-slate-900">Điều chỉnh công nợ</h2>
          <button type="button" className="grid size-9 place-items-center rounded-lg text-slate-500 hover:bg-slate-100" aria-label="Đóng" onClick={onClose} disabled={busy}><X className="size-5" /></button>
        </div>
        <p className="mt-2 text-sm font-medium text-slate-600">Booking {detail.booking.bookingCode} · {detail.booking.venueName}. Hoa hồng hiện tại <b data-testid="adjust-current">{formatCurrency(commission.commissionAmount)}</b>{settlement ? `, thuộc kỳ ${formatIsoDate(settlement.periodFrom)} – ${formatIsoDate(settlement.periodTo)}` : ', chưa thuộc kỳ nào'}.</p>
        <label className="mt-4 block">
          <span className="text-sm font-semibold text-slate-900">Hoa hồng mới (đồng) <span className="text-red-600">*</span></span>
          <input className={`${inputClass} mt-2`} aria-label="Hoa hồng mới" inputMode="numeric" value={amount} onChange={(event) => { setAmount(event.target.value); setErrors({}); setFormError('') }} aria-invalid={Boolean(errors.newAmount)} data-testid="adjust-amount" />
          <FieldError message={errors.newAmount} />
        </label>
        {delta !== null && delta !== 0 && (
          <p className={classNames('mt-2 rounded-lg p-3 text-xs font-semibold', delta < 0 ? 'bg-emerald-50 text-emerald-800' : 'bg-amber-50 text-amber-800')} data-testid="adjust-delta">
            {delta < 0 ? 'Giảm' : 'Tăng'} {formatCurrency(Math.abs(delta))}{settlement ? `: số phải thu của kỳ ${delta < 0 ? 'giảm' : 'tăng'} tương ứng (còn ${formatCurrency(Math.max(settlement.amountDue + delta - settlement.amountPaid, 0))} phải trả).` : '.'}
          </p>
        )}
        <label className="mt-4 block">
          <span className="text-sm font-semibold text-slate-900">Lý do điều chỉnh <span className="text-red-600">*</span></span>
          <textarea className={`${textareaClass} mt-2 h-20`} aria-label="Lý do điều chỉnh" maxLength={500} value={reason} onChange={(event) => { setReason(event.target.value); setErrors({}) }} aria-invalid={Boolean(errors.reason)} data-testid="adjust-reason" />
          <FieldError message={errors.reason} />
        </label>
        <p className="mt-2 rounded-lg bg-slate-50 p-3 text-xs font-medium text-slate-600">Hoa hồng chuyển sang "Đã điều chỉnh". Lý do và số tiền trước, sau điều chỉnh được lưu vào lịch sử, không xóa được. Phần voucher Courtly đã bù của booking không đổi.</p>
        <div className="mt-3"><FormAlert message={formError} /></div>
        <div className="mt-4 flex justify-end gap-2">
          <button type="button" className={secondaryButtonClass} disabled={busy} onClick={onClose}>Hủy</button>
          <button type="submit" className={primaryButtonClass} disabled={busy} data-testid="adjust-submit">{busy ? 'Đang lưu...' : 'Lưu điều chỉnh'}</button>
        </div>
      </form>
    </div>
  )
}

export { AdjustCommissionDialog }
