import { useState } from 'react'
import { AlertTriangle, CheckCircle2, Info } from 'lucide-react'
import { fetchCommissionDebt } from '../../../api/ownerCommissionDebt'
import { useApiResource } from '../../../hooks/useApiResource'
import { classNames, formatCurrency } from '../../../shared/utils'
import { formatDateTime } from '../ownerDates'
import { primaryButtonClass } from '../ownerStyles'
import { CommissionPaymentDialog } from './CommissionPaymentDialog'
import { DebtBookingsCard } from './DebtBookingsCard'
import { PaymentStatusCard } from './PaymentStatusCard'
import { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading } from './OwnerUi'

function Stat({ label, value, count, testId, tone, note }) {
  const tones = { danger: 'border-red-200 bg-red-50', warn: 'border-amber-200 bg-amber-50', ok: 'border-emerald-100 bg-white' }
  return (
    <div className={classNames('rounded-xl border p-4', tones[tone] ?? tones.ok)}>
      <p className="text-xs font-semibold text-stone-500">{label}</p>
      <p className={classNames('mt-1 text-xl font-semibold', tone === 'danger' ? 'text-red-700' : 'text-emerald-950')} data-testid={testId}>{formatCurrency(value)}</p>
      {count !== undefined && <p className="mt-0.5 text-xs font-medium text-stone-500" data-testid={`${testId}-count`}>{count} booking</p>}
      {note && <p className="mt-1 text-xs font-medium text-stone-500">{note}</p>}
    </div>
  )
}

/** 2.3.20 — tổng công nợ hoa hồng hiện tại, tổng hợp từ hoa hồng từng booking. */
function DebtTab() {
  const { status, data, error, reload } = useApiResource(fetchCommissionDebt, [], {
    isEmpty: (result) => result.outstandingCount === 0 && result.suspendedCount === 0 && result.settledAmount === 0,
  })

  const [paying, setPaying] = useState(false)
  const [notice, setNotice] = useState('')
  const [statusKey, setStatusKey] = useState(0)

  return (
    <div className="grid gap-5">
      {notice && <p role="status" className="flex items-start gap-2 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-semibold text-emerald-800" data-testid="payment-notice"><CheckCircle2 className="mt-0.5 size-4 shrink-0" />{notice}</p>}
      <div className="flex items-start gap-3 rounded-xl border border-emerald-100 bg-white p-4 text-sm font-medium text-stone-600">
        <Info className="mt-0.5 size-4 shrink-0 text-emerald-700" />
        <p>Công nợ là tổng hoa hồng Courtly của các booking chưa đối soát. Courtly không giữ tiền sân của bạn nên đây không phải số dư ví. Hoa hồng đang tranh chấp được tạm hoãn và chưa tính vào công nợ.</p>
      </div>

      {status === 'loading' && <OwnerLoading rows={2} />}
      {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
      {status === 'empty' && (
        <>
          <OwnerEmpty title="Bạn chưa có công nợ hoa hồng" description="Hoa hồng phát sinh khi một booking đã xác nhận được chơi xong." />
          <PaymentStatusCard key={statusKey} />
        </>
      )}
      {status === 'success' && (
        <>
          <section className={classNames('rounded-xl border p-5', data.overdueAmount > 0 ? 'border-red-200 bg-red-50' : 'border-emerald-200 bg-emerald-50')} data-testid="debt-summary">
            <p className="text-sm font-semibold text-stone-600">Tổng công nợ hiện tại</p>
            <p className="mt-1 text-4xl font-semibold text-emerald-900" data-testid="debt-total">{formatCurrency(data.totalOutstanding)}</p>
            <p className="mt-1 text-sm font-medium text-stone-600" data-testid="debt-total-count">{data.outstandingCount} booking chưa đối soát</p>
            {data.overdueAmount > 0 && (
              <p className="mt-3 flex items-start gap-2 text-sm font-semibold text-red-700" data-testid="debt-overdue-warning">
                <AlertTriangle className="mt-0.5 size-4 shrink-0" />
                {formatCurrency(data.overdueAmount)} đã quá hạn thanh toán, cũ nhất từ {formatDateTime(data.oldestOverdueAt)}. Công nợ quá hạn có thể dẫn đến hạn chế nhận booking mới, hãy thanh toán sớm.
              </p>
            )}
            {data.overdueAmount === 0 && data.totalOutstanding > 0 && data.nextDueAt && (
              <p className="mt-3 text-sm font-medium text-stone-600" data-testid="debt-next-due">Hạn thanh toán gần nhất: {formatDateTime(data.nextDueAt)}</p>
            )}
            {data.totalOutstanding === 0 && <p className="mt-3 text-sm font-semibold text-emerald-800">Bạn không còn khoản hoa hồng nào phải thanh toán.</p>}
            {data.totalOutstanding > 0 && <button type="button" className={`${primaryButtonClass} mt-4`} data-testid="pay-debt" onClick={() => setPaying(true)}>Gửi thông tin thanh toán</button>}
          </section>

          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Stat label="Đã quá hạn" value={data.overdueAmount} count={data.overdueCount} testId="debt-overdue" tone={data.overdueAmount > 0 ? 'danger' : 'ok'} />
            <Stat label="Chưa đến hạn" value={data.upcomingAmount} count={data.upcomingCount} testId="debt-upcoming" />
            <Stat label="Tạm hoãn do tranh chấp" value={data.suspendedAmount} count={data.suspendedCount} testId="debt-suspended" tone={data.suspendedAmount > 0 ? 'warn' : 'ok'} note="Chưa tính vào công nợ" />
            <Stat label="Đã đối soát" value={data.settledAmount} testId="debt-settled" note="Tổng đã thanh toán xong" />
          </div>

          <OwnerCard title="Công nợ theo sân">
            {data.byVenue.length === 0
              ? <p className="text-sm font-medium text-stone-500" data-testid="debt-by-venue-empty">Không có sân nào còn công nợ.</p>
              : (
                <ul className="divide-y divide-emerald-50" data-testid="debt-by-venue">
                  {data.byVenue.map((venue) => (
                    <li key={venue.venueId} className="flex flex-wrap items-center justify-between gap-2 py-3 text-sm" data-testid={`debt-venue-${venue.venueName}`}>
                      <span className="min-w-0 truncate font-semibold text-emerald-950">{venue.venueName}</span>
                      <span className="shrink-0 text-right">
                        <span className="font-semibold text-emerald-800">{formatCurrency(venue.outstanding)}</span>
                        {venue.overdue > 0 && <span className="ml-2 text-xs font-bold text-red-700">quá hạn {formatCurrency(venue.overdue)}</span>}
                        <span className="ml-2 text-xs font-medium text-stone-500">{venue.bookings} booking</span>
                      </span>
                    </li>
                  ))}
                </ul>
              )}
          </OwnerCard>
          <PaymentStatusCard key={statusKey} />
          <DebtBookingsCard />
          <p className="text-xs font-medium text-stone-400">Cập nhật lúc {formatDateTime(data.asOf)}</p>
          {paying && (
            <CommissionPaymentDialog
              maxAmount={data.totalOutstanding}
              onClose={() => setPaying(false)}
              onSubmitted={(payment) => {
                setPaying(false)
                setNotice(`Đã gửi thông tin thanh toán ${payment.referenceCode}. Courtly sẽ xác nhận sau khi đối chiếu, công nợ giảm khi được xác nhận.`)
                setStatusKey((current) => current + 1)
                reload()
              }}
            />
          )}
        </>
      )}
    </div>
  )
}

export { DebtTab }
