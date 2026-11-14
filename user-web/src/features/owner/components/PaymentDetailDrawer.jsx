import { useEffect, useState } from 'react'
import { ExternalLink, X } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { bookingStatusMeta } from '../../../api/ownerBookingRequests'
import { PAYMENT_METHOD_LABEL, paymentStatusMeta } from '../../../api/ownerBookings'
import { confirmOwnerPayment, fetchOwnerPaymentDetail, rejectOwnerPayment, safeProofUrl } from '../../../api/ownerPayments'
import { useApiResource } from '../../../hooks/useApiResource'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { classNames, formatCurrency } from '../../../shared/utils'
import { formatDateTime, formatTime } from '../ownerDates'
import { dangerButtonClass, primaryButtonClass, secondaryButtonClass, textareaClass } from '../ownerStyles'
import { OwnerError, OwnerLoading } from './OwnerUi'

function Badge({ meta, testId }) {
  return <span data-testid={testId} className={classNames('inline-flex rounded-full px-2.5 py-1 text-xs font-bold', meta.className)}>{meta.label}</span>
}

/** 2.3.27 — chi tiết xác nhận thanh toán trực tiếp: xác nhận đã thu tiền hoặc từ chối kèm lý do. */
function PaymentDetailDrawer({ paymentId, onClose, onChanged }) {
  const { status, data, error, reload } = useApiResource(() => fetchOwnerPaymentDetail(paymentId), [paymentId])
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState('')
  const [formError, setFormError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    function onKey(event) {
      if (event.key === 'Escape' && !busy) onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose, busy])

  async function run(action, message) {
    if (busy) return
    setBusy(true)
    setFormError('')
    try {
      const updated = await action()
      reload()
      setRejecting(false)
      setReason('')
      onChanged(message(updated))
    } catch (failure) {
      if (failure instanceof ApiError) {
        setReasonError(failure.fieldErrors?.reason ?? '')
        setFormError(failure.message)
        if (failure.status === 409) reload()
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
    } finally {
      setBusy(false)
    }
  }

  function submitReject(event) {
    event.preventDefault()
    const trimmed = reason.trim()
    if (trimmed.length < 3) return setReasonError('Vui lòng nhập lý do (ít nhất 3 ký tự)')
    if (trimmed.length > 500) return setReasonError('Lý do tối đa 500 ký tự')
    setReasonError('')
    return run(() => rejectOwnerPayment(paymentId, trimmed), (updated) => `Đã từ chối báo thanh toán của ${updated.booking.bookingCode}. Người chơi sẽ thấy lý do và có thể báo lại.`)
  }

  const proof = data?.proofUrl ? safeProofUrl(data.proofUrl) : null
  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/40" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose() }}>
      <aside role="dialog" aria-modal="true" aria-label="Chi tiết thanh toán" className="h-full w-full max-w-md overflow-y-auto bg-white p-5 shadow-2xl" data-testid="payment-drawer">
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-lg font-semibold text-emerald-950">Chi tiết thanh toán</h2>
          <button type="button" className="grid size-9 place-items-center rounded-lg text-stone-500 hover:bg-stone-100" aria-label="Đóng" onClick={onClose} disabled={busy}><X className="size-5" /></button>
        </div>
        {status === 'loading' && <div className="mt-4"><OwnerLoading rows={2} /></div>}
        {status === 'error' && <div className="mt-4"><OwnerError message={error?.message} onRetry={reload} /></div>}
        {status === 'success' && (
          <div className="mt-4 grid gap-5">
            <section>
              <div className="flex flex-wrap items-center gap-2">
                <Badge meta={paymentStatusMeta(data.status)} testId="payment-drawer-status" />
                <Badge meta={bookingStatusMeta(data.booking.status)} testId="payment-drawer-booking-status" />
                <span className="text-xs font-bold text-stone-400">{data.booking.bookingCode}</span>
              </div>
              <p className="mt-2 text-base font-semibold text-emerald-950">{data.booking.venueName}</p>
              <ul className="mt-1 grid gap-0.5 text-sm font-medium text-stone-600">
                {data.booking.slots.map((slot) => <li key={`${slot.courtName}-${slot.startTime}`}>{slot.courtName} · {formatDateTime(slot.startTime)}–{formatTime(slot.endTime)}</li>)}
              </ul>
              <p className="mt-3 text-sm font-semibold text-emerald-950">{data.booking.playerName}{data.booking.playerPhone ? ` · ${data.booking.playerPhone}` : ''}</p>
              <p className="mt-2 text-lg font-semibold text-emerald-700" data-testid="payment-drawer-amount">{formatCurrency(data.amount)}</p>
            </section>

            <section>
              <h3 className="text-sm font-semibold text-emerald-950">Thông tin thanh toán</h3>
              <dl className="mt-2 grid gap-1 text-sm font-medium text-stone-600">
                <div>{PAYMENT_METHOD_LABEL[data.method] ?? data.method}</div>
                {data.method === 'owner_bank_transfer' && data.bankAccountNo && <div>Tài khoản nhận: {data.bankName} · {data.bankAccountNo} · {data.bankAccountName}</div>}
                {data.referenceContent && <div>Nội dung chuyển khoản: <span className="font-semibold text-stone-800">{data.referenceContent}</span></div>}
                {data.payerReportedAt && <div>Người chơi báo đã trả: {formatDateTime(data.payerReportedAt)}</div>}
                {data.confirmedAt && <div>Bạn xác nhận đã thu: {formatDateTime(data.confirmedAt)}</div>}
                {data.expiredAt && data.status !== 'confirmed' && <div>Hạn thanh toán: {formatDateTime(data.expiredAt)}</div>}
                {proof && <div><a className="inline-flex items-center gap-1 font-semibold text-emerald-700 underline" href={proof} target="_blank" rel="noreferrer noopener" data-testid="payment-drawer-proof">Xem bằng chứng chuyển khoản<ExternalLink className="size-3" /></a></div>}
                {data.rejectionReason && <div className="rounded-lg bg-red-50 px-3 py-2 text-xs font-semibold text-red-800" data-testid="payment-drawer-reason">Lý do từ chối: {data.rejectionReason}</div>}
              </dl>
            </section>

            {data.expired && data.status === 'payer_reported' && <p className="rounded-lg bg-amber-50 px-3 py-2 text-xs font-semibold text-amber-900" data-testid="payment-drawer-expired">Giao dịch đã quá hạn thanh toán nên không thể xác nhận trên hệ thống. Bạn vẫn có thể từ chối và liên hệ người chơi.</p>}

            {!rejecting && <FormAlert message={formError} />}
            {(data.canConfirm || data.canReject) && !rejecting && (
              <section className="grid gap-2">
                <div className="flex flex-wrap gap-2">
                  {data.canConfirm && <button type="button" className={primaryButtonClass} data-testid="payment-confirm" disabled={busy} onClick={() => run(() => confirmOwnerPayment(paymentId), (updated) => `Đã xác nhận thu tiền ${updated.booking.bookingCode}. Booking đã được xác nhận.`)}>{busy ? 'Đang xử lý...' : 'Xác nhận đã thu tiền'}</button>}
                  {data.canReject && <button type="button" className={dangerButtonClass} data-testid="payment-reject-open" disabled={busy} onClick={() => { setRejecting(true); setFormError('') }}>Chưa nhận được tiền</button>}
                </div>
              </section>
            )}
            {rejecting && (
              <form className="grid gap-3" noValidate onSubmit={submitReject} data-testid="payment-reject-form">
                <label className="block">
                  <span className="text-sm font-semibold text-emerald-950">Lý do từ chối <span className="text-red-600">*</span></span>
                  <textarea className={`${textareaClass} mt-2 h-24`} aria-label="Lý do từ chối thanh toán" maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} aria-invalid={Boolean(reasonError)} />
                  <FieldError message={reasonError} />
                </label>
                <FormAlert message={formError} />
                <div className="flex justify-end gap-2">
                  <button type="button" className={secondaryButtonClass} disabled={busy} onClick={() => { setRejecting(false); setReasonError(''); setFormError('') }}>Hủy</button>
                  <button type="submit" className={dangerButtonClass} disabled={busy}>{busy ? 'Đang từ chối...' : 'Từ chối báo thanh toán'}</button>
                </div>
              </form>
            )}

            <section>
              <h3 className="text-sm font-semibold text-emerald-950">Tiến trình thanh toán</h3>
              <ol className="mt-2 grid gap-3 border-l-2 border-emerald-100 pl-4" data-testid="payment-drawer-timeline">
                {data.timeline.map((step) => (
                  <li key={`${step.newStatus}-${step.createdAt}`} className="relative text-sm">
                    <span className="absolute -left-[22px] top-1.5 size-2.5 rounded-full bg-emerald-500" />
                    <p className="font-semibold text-emerald-950">{paymentStatusMeta(step.newStatus).label}</p>
                    <p className="text-xs font-medium text-stone-500">{formatDateTime(step.createdAt)} · {step.bySystem ? 'Hệ thống' : step.actorName}</p>
                    {step.reason && <p className="mt-0.5 text-xs font-medium text-stone-600">{step.reason}</p>}
                  </li>
                ))}
              </ol>
            </section>
          </div>
        )}
      </aside>
    </div>
  )
}

export { PaymentDetailDrawer }
