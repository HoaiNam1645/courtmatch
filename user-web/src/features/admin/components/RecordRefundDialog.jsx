import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { recordRefund } from '../../../api/adminCommissionTransactions'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { formatCurrency } from '../../../shared/utils'
import { inputClass, primaryButtonClass, secondaryButtonClass, textareaClass } from '../../owner/ownerStyles'

/** Số tiền chủ sân đã hoàn xác nhận cho khoản thanh toán này. */
export function refundedTotal(refunds) {
  return refunds.filter((item) => item.status === 'confirmed').reduce((sum, item) => sum + item.amount, 0)
}

/**
 * Ghi nhận hoàn tiền trực tiếp (2.3.52): chủ sân đã tự hoàn tiền cho người chơi, Courtly xác nhận và điều chỉnh hoa hồng của
 * booking theo phần còn lại của khoản đã thu. Xem trước hoa hồng mới theo đúng công thức của máy chủ.
 */
function RecordRefundDialog({ detail, onClose, onDone }) {
  const { commission, payment, refunds } = detail
  const alreadyRefunded = refundedTotal(refunds)
  const refundable = payment.amount - alreadyRefunded
  const [amount, setAmount] = useState('')
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

  const valid = /^\d+$/.test(amount.trim()) && amount.trim().length <= 10 && Number(amount) >= 1 && Number(amount) <= refundable
  const full = valid && Number(amount) === refundable
  // Tỉ lệ còn lại so với trước lần hoàn này, như máy chủ: (đã thu - hoàn sau) / (đã thu - hoàn trước).
  const ratio = valid ? (refundable - Number(amount)) / refundable : null
  const preview = valid ? (full ? 0 : Math.round(commission.commissionAmount * ratio)) : null

  async function submit(event) {
    event.preventDefault()
    if (busy) return
    const problems = {}
    if (!/^\d+$/.test(amount.trim()) || Number(amount) < 1) problems.amount = 'Nhập số tiền hoàn là số nguyên đồng, tối thiểu 1.'
    else if (Number(amount) > refundable) problems.amount = `Tối đa ${formatCurrency(refundable)} (phần còn có thể hoàn).`
    if (reason.trim().length < 5) problems.reason = 'Nhập lý do hoàn tiền (ít nhất 5 ký tự).'
    else if (reason.length > 500) problems.reason = 'Lý do tối đa 500 ký tự.'
    setErrors(problems)
    if (Object.keys(problems).length > 0) return
    setBusy(true)
    setFormError('')
    try {
      const result = await recordRefund(commission.id, { amount, reason })
      onDone(result, `Đã ghi nhận hoàn ${formatCurrency(Number(amount))} cho booking ${detail.booking.bookingCode}. Hoa hồng ${formatCurrency(commission.commissionAmount)} → ${formatCurrency(result.commission.commissionAmount)}.`)
    } catch (failure) {
      if (failure instanceof ApiError) {
        setErrors({ amount: failure.fieldErrors?.amount, reason: failure.fieldErrors?.reason })
        setFormError(failure.message)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
      setBusy(false)
    }
  }

  return (
    <div className="fixed inset-0 z-[60] grid place-items-center overflow-y-auto bg-black/40 p-4" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose() }}>
      <form role="dialog" aria-modal="true" aria-label="Ghi nhận hoàn tiền" noValidate onSubmit={submit} className="w-full max-w-lg rounded-xl bg-white p-5 shadow-2xl" data-testid="refund-dialog">
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-lg font-semibold text-slate-900">Ghi nhận hoàn tiền trực tiếp</h2>
          <button type="button" className="grid size-9 place-items-center rounded-lg text-slate-500 hover:bg-slate-100" aria-label="Đóng" onClick={onClose} disabled={busy}><X className="size-5" /></button>
        </div>
        <p className="mt-2 text-sm font-medium text-slate-600">Dùng khi chủ sân đã tự hoàn tiền cho người chơi. Courtly không giữ tiền sân nên đây chỉ là xác nhận khoản đã hoàn; hoa hồng của booking giảm theo phần hoàn.</p>
        <dl className="mt-3 divide-y divide-slate-100 rounded-xl border border-slate-200 px-3 text-sm">
          <div className="flex justify-between gap-3 py-2"><dt className="font-medium text-slate-500">Đã thu của người chơi</dt><dd className="font-semibold text-slate-900" data-testid="refund-paid">{formatCurrency(payment.amount)}</dd></div>
          <div className="flex justify-between gap-3 py-2"><dt className="font-medium text-slate-500">Đã hoàn trước đó</dt><dd className="font-semibold text-slate-900" data-testid="refund-before">{formatCurrency(alreadyRefunded)}</dd></div>
          <div className="flex justify-between gap-3 py-2"><dt className="font-medium text-slate-500">Còn có thể hoàn</dt><dd className="font-semibold text-slate-900" data-testid="refund-refundable">{formatCurrency(refundable)}</dd></div>
          <div className="flex justify-between gap-3 py-2"><dt className="font-medium text-slate-500">Hoa hồng hiện tại</dt><dd className="font-semibold text-slate-900">{formatCurrency(commission.commissionAmount)}</dd></div>
        </dl>
        <label className="mt-4 block">
          <span className="text-sm font-semibold text-slate-900">Số tiền hoàn (đồng) <span className="text-red-600">*</span></span>
          <input className={`${inputClass} mt-2`} aria-label="Số tiền hoàn" inputMode="numeric" value={amount} onChange={(event) => { setAmount(event.target.value); setErrors({}); setFormError('') }} aria-invalid={Boolean(errors.amount)} data-testid="refund-amount" />
          <FieldError message={errors.amount} />
        </label>
        {valid && (
          <p className="mt-2 rounded-lg bg-emerald-50 p-3 text-xs font-semibold text-emerald-800" data-testid="refund-preview">
            {full ? `Hoàn toàn bộ: hoa hồng chưa thu về ${formatCurrency(0)} (được miễn).` : `Hoa hồng sau khi hoàn khoảng ${formatCurrency(preview)} (giảm ${formatCurrency(commission.commissionAmount - preview)}).`}
            {commission.status === 'settled' ? ' Hoa hồng này đã đối soát nên phần giảm được giữ làm số dư trả trước của chủ sân.' : ''}
          </p>
        )}
        <label className="mt-4 block">
          <span className="text-sm font-semibold text-slate-900">Lý do hoàn tiền <span className="text-red-600">*</span></span>
          <textarea className={`${textareaClass} mt-2 h-20`} aria-label="Lý do hoàn tiền" maxLength={500} value={reason} onChange={(event) => { setReason(event.target.value); setErrors({}) }} aria-invalid={Boolean(errors.reason)} data-testid="refund-reason" />
          <FieldError message={errors.reason} />
        </label>
        <div className="mt-3"><FormAlert message={formError} /></div>
        <div className="mt-4 flex justify-end gap-2">
          <button type="button" className={secondaryButtonClass} disabled={busy} onClick={onClose}>Hủy</button>
          <button type="submit" className={primaryButtonClass} disabled={busy} data-testid="refund-submit">{busy ? 'Đang ghi nhận...' : 'Ghi nhận hoàn tiền'}</button>
        </div>
      </form>
    </div>
  )
}

export { RecordRefundDialog }
