import { fetchBookingReconciliation, RECONCILIATION_EVENT_LABEL } from '../../../api/ownerBookings'
import { useApiResource } from '../../../hooks/useApiResource'
import { formatCurrency } from '../../../shared/utils'
import { formatDateTime, formatIsoDate } from '../ownerDates'
import { OwnerError, OwnerLoading } from './OwnerUi'

/** 2.3.22 — lịch sử đối soát hoa hồng của một booking. */
function ReconciliationHistory({ bookingId }) {
  const { status, data, error, reload } = useApiResource(() => fetchBookingReconciliation(bookingId), [bookingId])
  return (
    <section data-testid="reconciliation-history">
      <h3 className="text-sm font-semibold text-emerald-950">Lịch sử đối soát</h3>
      {status === 'loading' && <div className="mt-2"><OwnerLoading rows={1} /></div>}
      {status === 'error' && <div className="mt-2"><OwnerError message={error?.message} onRetry={reload} /></div>}
      {status === 'success' && (!data.commission
        ? <p className="mt-2 text-sm font-medium text-stone-500" data-testid="reconciliation-empty">Chưa có hoa hồng nên chưa có lịch sử đối soát. Hoa hồng được ghi nhận khi booking hoàn thành.</p>
        : (
          <div className="mt-2 grid gap-3">
            {data.settlement && (
              <p className="text-sm font-medium text-stone-600" data-testid="reconciliation-settlement">
                Kỳ đối soát {formatIsoDate(data.settlement.periodFrom)} – {formatIsoDate(data.settlement.periodTo)}
              </p>
            )}
            <ol className="grid gap-3 border-l-2 border-emerald-100 pl-4" data-testid="reconciliation-events">
              {data.events.map((event) => (
                <li key={`${event.type}-${event.at}`} className="relative text-sm" data-testid={`reconciliation-${event.type}`}>
                  <span className="absolute -left-[22px] top-1.5 size-2.5 rounded-full bg-sky-500" />
                  <p className="font-semibold text-emerald-950">{RECONCILIATION_EVENT_LABEL[event.type] ?? event.label}</p>
                  <p className="text-xs font-medium text-stone-500">{formatDateTime(event.at)} · {formatCurrency(event.amount)}</p>
                  {event.note && <p className="mt-0.5 text-xs font-medium text-stone-600">{event.note}</p>}
                </li>
              ))}
            </ol>
            {data.commission.overdue && <p className="text-xs font-semibold text-red-700" data-testid="reconciliation-overdue">Đã quá hạn thanh toán từ {formatDateTime(data.commission.dueAt)}.</p>}
          </div>
        ))}
    </section>
  )
}

export { ReconciliationHistory }
