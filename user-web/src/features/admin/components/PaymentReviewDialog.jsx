import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { confirmAdminPayment } from '../../../api/adminCommissionPayments'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { formatCurrency } from '../../../shared/utils'
import { formatIsoDate } from '../../owner/ownerDates'
import { primaryButtonClass, secondaryButtonClass, textareaClass } from '../../owner/ownerStyles'

/**
 * Xử lý báo cáo thanh toán công nợ của chủ sân. `payment` là báo cáo đang chờ xác nhận.
 * Xác nhận (2.3.47) đối chiếu số tiền, mã tham chiếu và bằng chứng rồi trừ vào công nợ.
 */
function PaymentReviewDialog({ payment, onClose, onDone }) {
  const [note, setNote] = useState('')
  const [error, setError] = useState('')
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
    if (note.length > 500) {
      setError('Ghi chú tối đa 500 ký tự.')
      return
    }
    setBusy(true)
    setFormError('')
    try {
      const result = await confirmAdminPayment(payment.id, note)
      const offset = result.courtlyOffsetAmount > 0 ? ` Đã bù trừ ${formatCurrency(result.courtlyOffsetAmount)} Courtly còn nợ chủ sân.` : ''
      onDone(result, `Đã xác nhận ${formatCurrency(result.amount)} chủ sân ${result.ownerName} đã thanh toán (${result.referenceCode}).${offset}`)
    } catch (failure) {
      setFormError(failure instanceof ApiError ? failure.message : 'Đã có lỗi xảy ra, vui lòng thử lại.')
      setBusy(false)
    }
  }

  return (
    <div className="fixed inset-0 z-[60] grid place-items-center overflow-y-auto bg-black/40 p-4" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose() }}>
      <form role="dialog" aria-modal="true" aria-label="Xác nhận thanh toán công nợ" noValidate onSubmit={submit} className="w-full max-w-lg rounded-xl bg-white p-5 shadow-2xl" data-testid="payment-dialog">
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-lg font-semibold text-slate-900">Xác nhận chủ sân đã thanh toán</h2>
          <button type="button" className="grid size-9 place-items-center rounded-lg text-slate-500 hover:bg-slate-100" aria-label="Đóng" onClick={onClose} disabled={busy}><X className="size-5" /></button>
        </div>
        <dl className="mt-4 divide-y divide-slate-100 rounded-xl border border-slate-200 px-3 text-sm">
          <div className="flex justify-between gap-3 py-2"><dt className="font-medium text-slate-500">Chủ sân</dt><dd className="text-right font-semibold text-slate-900">{payment.ownerName}</dd></div>
          <div className="flex justify-between gap-3 py-2"><dt className="font-medium text-slate-500">Số tiền</dt><dd className="text-right font-semibold text-slate-900" data-testid="payment-dialog-amount">{formatCurrency(payment.amount)}</dd></div>
          <div className="flex justify-between gap-3 py-2"><dt className="font-medium text-slate-500">Ngày thanh toán</dt><dd className="text-right font-medium text-slate-700">{formatIsoDate(payment.paidOn)}</dd></div>
          <div className="flex justify-between gap-3 py-2"><dt className="font-medium text-slate-500">Mã tham chiếu</dt><dd className="break-all text-right font-medium text-slate-700">{payment.referenceCode}</dd></div>
          <div className="flex justify-between gap-3 py-2"><dt className="font-medium text-slate-500">Áp dụng cho</dt><dd className="text-right font-medium text-slate-700">{payment.settlement ? `Kỳ ${formatIsoDate(payment.settlement.periodFrom)} – ${formatIsoDate(payment.settlement.periodTo)}` : 'Công nợ chung (hoa hồng cũ nhất chưa thuộc kỳ)'}</dd></div>
        </dl>
        {payment.proofUrl && <a className="mt-2 inline-block break-all text-sm font-semibold text-sky-700 underline" href={payment.proofUrl} target="_blank" rel="noreferrer noopener">Xem bằng chứng thanh toán</a>}
        {payment.note && <p className="mt-2 text-sm font-medium text-slate-600">Ghi chú của chủ sân: {payment.note}</p>}
        <p className="mt-3 rounded-lg bg-slate-50 p-3 text-xs font-medium text-slate-600">Hãy đối chiếu số tiền với sao kê trước khi xác nhận. Khi xác nhận, công nợ giảm tương ứng, các hoa hồng đủ tiền chuyển sang đã đối soát và khoản Courtly còn nợ chủ sân (nếu có) được bù trừ vào phần còn lại. Không hoàn tác được.</p>
        <label className="mt-3 block">
          <span className="text-sm font-semibold text-slate-900">Ghi chú xác nhận</span>
          <textarea className={`${textareaClass} mt-2 h-16`} aria-label="Ghi chú xác nhận" maxLength={500} value={note} onChange={(event) => { setNote(event.target.value); setError('') }} />
          <FieldError message={error} />
        </label>
        <div className="mt-3"><FormAlert message={formError} /></div>
        <div className="mt-4 flex justify-end gap-2">
          <button type="button" className={secondaryButtonClass} disabled={busy} onClick={onClose}>Hủy</button>
          <button type="submit" className={primaryButtonClass} disabled={busy} data-testid="payment-dialog-confirm">{busy ? 'Đang xác nhận...' : 'Xác nhận đã nhận tiền'}</button>
        </div>
      </form>
    </div>
  )
}

export { PaymentReviewDialog }
