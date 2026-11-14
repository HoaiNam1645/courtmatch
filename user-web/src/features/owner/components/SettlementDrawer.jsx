import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import { commissionStatusMeta } from '../../../api/ownerCommissions'
import { fetchSettlementDetail, settlementStatusMeta } from '../../../api/ownerSettlements'
import { useApiResource } from '../../../hooks/useApiResource'
import { classNames, formatCurrency } from '../../../shared/utils'
import { formatDateTime, formatIsoDate } from '../ownerDates'
import { secondaryButtonClass } from '../ownerStyles'
import { OwnerError, OwnerLoading } from './OwnerUi'

function Row({ label, value, testId, strong }) {
  return (
    <div className="flex items-center justify-between gap-3 py-1.5 text-sm">
      <dt className="font-medium text-stone-500">{label}</dt>
      <dd className={classNames('text-right', strong ? 'font-semibold text-emerald-900' : 'font-semibold text-stone-700')} data-testid={testId}>{value}</dd>
    </div>
  )
}

/** Khung chi tiết một kỳ đối soát: số liệu của kỳ và các booking thuộc kỳ (2.3.23). */
function SettlementDrawer({ settlementId, onClose, footer }) {
  const [page, setPage] = useState(0)
  const { status, data, error, reload } = useApiResource(() => fetchSettlementDetail(settlementId, { page }), [settlementId, page])

  useEffect(() => {
    function onKey(event) {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const settlement = data?.settlement
  const meta = settlement ? settlementStatusMeta(settlement.status) : null
  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/40" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
      <aside role="dialog" aria-modal="true" aria-label="Chi tiết kỳ đối soát" className="h-full w-full max-w-md overflow-y-auto bg-white p-5 shadow-2xl" data-testid="settlement-drawer">
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-lg font-semibold text-emerald-950">Chi tiết kỳ đối soát</h2>
          <button type="button" className="grid size-9 place-items-center rounded-lg text-stone-500 hover:bg-stone-100" aria-label="Đóng" onClick={onClose}><X className="size-5" /></button>
        </div>
        {status === 'loading' && <div className="mt-4"><OwnerLoading rows={2} /></div>}
        {status === 'error' && <div className="mt-4"><OwnerError message={error?.message} onRetry={reload} /></div>}
        {status === 'success' && (
          <div className="mt-4 grid gap-5">
            <section>
              <span className={classNames('inline-flex rounded-full px-2.5 py-1 text-xs font-bold', meta.className)} data-testid="settlement-drawer-status">{meta.label}</span>
              <p className="mt-2 text-base font-semibold text-emerald-950">Kỳ {formatIsoDate(settlement.periodFrom)} – {formatIsoDate(settlement.periodTo)}</p>
              <dl className="mt-2 divide-y divide-emerald-50">
                <Row label="Nợ kỳ trước chuyển sang" value={formatCurrency(settlement.openingOutstanding)} />
                <Row label="Hoa hồng của kỳ" value={formatCurrency(settlement.commissionTotal)} />
                <Row label="Điều chỉnh" value={formatCurrency(settlement.adjustmentTotal)} />
                <Row label="Phải thanh toán" value={formatCurrency(settlement.amountDue)} testId="settlement-drawer-due" strong />
                <Row label="Đã thanh toán" value={formatCurrency(settlement.amountPaid)} testId="settlement-drawer-paid" />
                <Row label="Còn phải trả" value={formatCurrency(settlement.remainingAmount)} testId="settlement-drawer-remaining" strong />
              </dl>
              {settlement.note && <p className="mt-2 rounded-lg bg-stone-50 px-3 py-2 text-xs font-medium text-stone-600">Ghi chú: {settlement.note}</p>}
              {settlement.paidAt && <p className="mt-2 text-xs font-medium text-stone-500">Hoàn tất thanh toán: {formatDateTime(settlement.paidAt)}</p>}
            </section>

            <section>
              <h3 className="text-sm font-semibold text-emerald-950">Booking trong kỳ ({data.totalBookings})</h3>
              {data.bookings.length === 0
                ? <p className="mt-2 text-sm font-medium text-stone-500" data-testid="settlement-drawer-empty">Kỳ này chưa có booking nào.</p>
                : (
                  <ul className="mt-2 grid gap-2" data-testid="settlement-drawer-bookings">
                    {data.bookings.map((item) => {
                      const commissionMeta = commissionStatusMeta(item.status)
                      return (
                        <li key={item.bookingId} className="rounded-lg border border-emerald-100 p-3 text-sm" data-testid={`settlement-booking-${item.bookingCode}`}>
                          <div className="flex items-center justify-between gap-2">
                            <span className="text-xs font-bold text-stone-400">{item.bookingCode}</span>
                            <span className="font-semibold text-emerald-800">{formatCurrency(item.commissionAmount)}</span>
                          </div>
                          <p className="mt-1 font-semibold text-emerald-950">{item.venueName}</p>
                          <p className="text-xs font-medium text-stone-500">{item.playerName} · gộp {formatCurrency(item.grossAmount)} · <span className={classNames('rounded-full px-2 py-0.5 text-[11px] font-bold', commissionMeta.className)}>{commissionMeta.label}</span></p>
                        </li>
                      )
                    })}
                  </ul>
                )}
              {data.totalPages > 1 && (
                <div className="mt-3 flex items-center justify-between gap-3">
                  <button type="button" className={secondaryButtonClass} disabled={page === 0} onClick={() => setPage((current) => current - 1)}>Trang trước</button>
                  <p className="text-xs font-semibold text-stone-600">Trang {data.page + 1}/{data.totalPages}</p>
                  <button type="button" className={secondaryButtonClass} disabled={page + 1 >= data.totalPages} onClick={() => setPage((current) => current + 1)}>Trang sau</button>
                </div>
              )}
            </section>
            {footer?.(settlement)}
          </div>
        )}
      </aside>
    </div>
  )
}

export { SettlementDrawer }
