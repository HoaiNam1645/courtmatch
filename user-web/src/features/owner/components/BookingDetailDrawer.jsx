import { useEffect } from 'react'
import { X } from 'lucide-react'
import { bookingStatusMeta } from '../../../api/ownerBookingRequests'
import { fetchBookingDetail, paymentStatusMeta, PAYMENT_METHOD_LABEL } from '../../../api/ownerBookings'
import { useApiResource } from '../../../hooks/useApiResource'
import { classNames, formatCurrency } from '../../../shared/utils'
import { formatDateTime, formatTime } from '../ownerDates'
import { OwnerError, OwnerLoading } from './OwnerUi'

function Badge({ meta, testId }) {
  return <span data-testid={testId} className={classNames('inline-flex rounded-full px-2.5 py-1 text-xs font-bold', meta.className)}>{meta.label}</span>
}

/** Khung chi tiết booking bên phải: thông tin, thanh toán và timeline trạng thái (2.3.15). */
function BookingDetailDrawer({ bookingId, onClose }) {
  const { status, data, error, reload } = useApiResource(() => fetchBookingDetail(bookingId), [bookingId])

  useEffect(() => {
    function onKey(event) {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/40" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
      <aside role="dialog" aria-modal="true" aria-label="Chi tiết booking" className="h-full w-full max-w-md overflow-y-auto bg-white p-5 shadow-2xl" data-testid="booking-drawer">
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-lg font-semibold text-emerald-950">Chi tiết booking</h2>
          <button type="button" className="grid size-9 place-items-center rounded-lg text-stone-500 hover:bg-stone-100" aria-label="Đóng" onClick={onClose}><X className="size-5" /></button>
        </div>
        {status === 'loading' && <div className="mt-4"><OwnerLoading rows={2} /></div>}
        {status === 'error' && <div className="mt-4"><OwnerError message={error?.message} onRetry={reload} /></div>}
        {status === 'success' && (
          <div className="mt-4 grid gap-5">
            <section>
              <div className="flex flex-wrap items-center gap-2"><Badge meta={bookingStatusMeta(data.status)} testId="drawer-status" /><span className="text-xs font-bold text-stone-400">{data.bookingCode}</span></div>
              <p className="mt-2 text-base font-semibold text-emerald-950">{data.venueName}</p>
              <ul className="mt-1 grid gap-0.5 text-sm font-medium text-stone-600">
                {data.slots.map((slot) => <li key={`${slot.courtName}-${slot.startTime}`}>{slot.courtName} · {formatDateTime(slot.startTime)}–{formatTime(slot.endTime)}</li>)}
              </ul>
              <p className="mt-3 text-sm font-semibold text-emerald-950">{data.playerName}{data.playerPhone ? ` · ${data.playerPhone}` : ''}</p>
              {data.note && <p className="mt-1 text-sm text-stone-500">Ghi chú: “{data.note}”</p>}
              <p className="mt-2 text-lg font-semibold text-emerald-700">{formatCurrency(data.totalAmount)}</p>
              {data.rejectionReason && <p className="mt-2 rounded-lg bg-red-50 px-3 py-2 text-xs font-semibold text-red-800">Lý do từ chối: {data.rejectionReason}</p>}
              {data.cancellationReason && <p className="mt-2 rounded-lg bg-red-50 px-3 py-2 text-xs font-semibold text-red-800">Lý do hủy: {data.cancellationReason}</p>}
            </section>

            <section>
              <h3 className="text-sm font-semibold text-emerald-950">Thanh toán trực tiếp</h3>
              {data.payment ? (
                <div className="mt-2 grid gap-1 text-sm font-medium text-stone-600" data-testid="drawer-payment">
                  <div><Badge meta={paymentStatusMeta(data.payment.status)} /></div>
                  <p>{PAYMENT_METHOD_LABEL[data.payment.method] ?? data.payment.method} · {formatCurrency(Number(data.payment.amount))}</p>
                  {data.payment.payerReportedAt && <p>Người chơi báo đã trả: {formatDateTime(data.payment.payerReportedAt)}</p>}
                  {data.payment.confirmedAt && <p>Chủ sân xác nhận thu: {formatDateTime(data.payment.confirmedAt)}</p>}
                  {data.payment.rejectionReason && <p className="text-red-700">Chưa nhận được tiền: {data.payment.rejectionReason}</p>}
                </div>
              ) : <p className="mt-2 text-sm font-medium text-stone-500">Chưa có thanh toán — booking chưa được chủ sân chấp nhận.</p>}
            </section>

            <section>
              <h3 className="text-sm font-semibold text-emerald-950">Tiến trình</h3>
              <ol className="mt-2 grid gap-3 border-l-2 border-emerald-100 pl-4" data-testid="drawer-timeline">
                {data.timeline.map((step) => (
                  <li key={`${step.newStatus}-${step.createdAt}`} className="relative text-sm">
                    <span className="absolute -left-[22px] top-1.5 size-2.5 rounded-full bg-emerald-500" />
                    <p className="font-semibold text-emerald-950">{bookingStatusMeta(step.newStatus).label}</p>
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

export { BookingDetailDrawer }
