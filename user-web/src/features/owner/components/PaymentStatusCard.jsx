import { fetchCommissionPaymentStatus, commissionPaymentStatusMeta } from '../../../api/ownerCommissionPayments'
import { settlementStatusMeta } from '../../../api/ownerSettlements'
import { useApiResource } from '../../../hooks/useApiResource'
import { classNames, formatCurrency } from '../../../shared/utils'
import { formatDateTime, formatIsoDate } from '../ownerDates'
import { OwnerCard, OwnerError, OwnerLoading } from './OwnerUi'

/** 2.3.25 — trạng thái thanh toán công nợ: đang chờ xác nhận, đã xác nhận, bị từ chối và các kỳ chưa trả xong. */
function PaymentStatusCard() {
  const { status, data, error, reload } = useApiResource(fetchCommissionPaymentStatus, [])
  return (
    <OwnerCard title="Trạng thái thanh toán" description="Công nợ chỉ giảm sau khi Courtly xác nhận đã nhận tiền bạn gửi.">
      {status === 'loading' && <OwnerLoading rows={1} />}
      {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
      {status === 'success' && (
        <div className="grid gap-5" data-testid="payment-status">
          <div className="grid gap-3 sm:grid-cols-3">
            <div className="rounded-xl border border-sky-200 bg-sky-50 p-4">
              <p className="text-xs font-semibold text-stone-500">Đang chờ Courtly xác nhận</p>
              <p className="mt-1 text-xl font-semibold text-sky-800" data-testid="status-pending">{formatCurrency(data.pendingAmount)}</p>
              <p className="mt-0.5 text-xs font-medium text-stone-500" data-testid="status-pending-count">{data.pendingCount} lần gửi</p>
            </div>
            <div className="rounded-xl border border-emerald-100 p-4">
              <p className="text-xs font-semibold text-stone-500">Courtly đã xác nhận</p>
              <p className="mt-1 text-xl font-semibold text-emerald-900" data-testid="status-confirmed">{formatCurrency(data.confirmedAmount)}</p>
              <p className="mt-0.5 text-xs font-medium text-stone-500">{data.confirmedCount} lần gửi</p>
            </div>
            <div className={classNames('rounded-xl border p-4', data.rejectedCount > 0 ? 'border-red-200 bg-red-50' : 'border-emerald-100')}>
              <p className="text-xs font-semibold text-stone-500">Bị từ chối</p>
              <p className="mt-1 text-xl font-semibold text-stone-800" data-testid="status-rejected">{data.rejectedCount} lần</p>
            </div>
          </div>

          {data.lastPayment ? (
            <div className="rounded-xl border border-emerald-100 p-4" data-testid="status-last">
              <p className="text-xs font-semibold text-stone-500">Lần gửi gần nhất</p>
              <div className="mt-1 flex flex-wrap items-center justify-between gap-2">
                <p className="text-sm font-semibold text-emerald-950">{formatCurrency(data.lastPayment.amount)} · {data.lastPayment.referenceCode}</p>
                <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', commissionPaymentStatusMeta(data.lastPayment.status).className)} data-testid="status-last-badge">{commissionPaymentStatusMeta(data.lastPayment.status).label}</span>
              </div>
              <p className="mt-1 text-xs font-medium text-stone-500">Thanh toán ngày {formatIsoDate(data.lastPayment.paidOn)} · gửi lúc {formatDateTime(data.lastPayment.createdAt)}</p>
              {data.lastPayment.status === 'rejected' && data.lastPayment.reviewNote && <p className="mt-2 rounded-lg bg-red-50 px-3 py-2 text-xs font-semibold text-red-800" data-testid="status-last-reason">Lý do từ chối: {data.lastPayment.reviewNote}</p>}
            </div>
          ) : <p className="text-sm font-medium text-stone-500" data-testid="status-none">Bạn chưa gửi thông tin thanh toán nào.</p>}

          {data.unpaidSettlements.length > 0 && (
            <div>
              <h3 className="text-sm font-semibold text-emerald-950">Kỳ đối soát chưa thanh toán xong</h3>
              <ul className="mt-2 grid gap-2" data-testid="status-settlements">
                {data.unpaidSettlements.map((item) => {
                  const meta = settlementStatusMeta(item.status)
                  return (
                    <li key={item.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-emerald-100 p-3 text-sm" data-testid={`status-settlement-${item.periodFrom}`}>
                      <span className="font-semibold text-emerald-950">Kỳ {formatIsoDate(item.periodFrom)} – {formatIsoDate(item.periodTo)}</span>
                      <span className="text-right text-xs font-medium text-stone-600">
                        <span className={classNames('mr-2 rounded-full px-2 py-0.5 font-bold', meta.className)}>{meta.label}</span>
                        còn {formatCurrency(item.remainingAmount)}{item.pendingAmount > 0 ? ` · chờ xác nhận ${formatCurrency(item.pendingAmount)}` : ''}
                      </span>
                    </li>
                  )
                })}
              </ul>
            </div>
          )}
        </div>
      )}
    </OwnerCard>
  )
}

export { PaymentStatusCard }
