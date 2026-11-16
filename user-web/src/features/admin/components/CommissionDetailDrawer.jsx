import { useEffect } from 'react'
import { X } from 'lucide-react'
import { DISPUTE_STATUS_LABEL, fetchCommissionDetail, HISTORY_ACTION_LABEL, REFUND_STATUS_LABEL } from '../../../api/adminCommissionTransactions'
import { commissionStatusMeta, describeFee } from '../../../api/ownerCommissions'
import { paymentStatusMeta } from '../../../api/ownerBookings'
import { settlementStatusMeta } from '../../../api/ownerSettlements'
import { useApiResource } from '../../../hooks/useApiResource'
import { classNames, formatCurrency } from '../../../shared/utils'
import { OwnerError, OwnerLoading } from '../../owner/components/OwnerUi'
import { formatDateTime, formatIsoDate } from '../../owner/ownerDates'

function Line({ label, value, testId, strong }) {
  return (
    <div className="flex items-start justify-between gap-3 py-1.5 text-sm">
      <dt className="font-medium text-slate-500">{label}</dt>
      <dd className={classNames('text-right', strong ? 'font-semibold text-slate-900' : 'font-medium text-slate-700')} data-testid={testId}>{value}</dd>
    </div>
  )
}

function Section({ title, children, testId }) {
  return (
    <section data-testid={testId}>
      <h3 className="text-sm font-semibold text-slate-900">{title}</h3>
      <div className="mt-2">{children}</div>
    </section>
  )
}

/** Mô tả thay đổi trong audit log: các khóa có trong dữ liệu mới, kèm giá trị cũ nếu có. */
function describeChange(entry) {
  const keys = Object.keys(entry.newData ?? {})
  if (keys.length === 0) return null
  return keys.map((key) => `${key}: ${entry.oldData?.[key] ?? '—'} → ${entry.newData[key]}`).join(' · ')
}

/** 2.3.44 — chi tiết đối soát của một booking: hoa hồng đã chốt, thanh toán trực tiếp, hoàn tiền, tranh chấp và lịch sử. */
function CommissionDetailDrawer({ commissionId, onClose }) {
  const { status, data, error, reload } = useApiResource(() => fetchCommissionDetail(commissionId), [commissionId])

  useEffect(() => {
    function onKey(event) {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const meta = data ? commissionStatusMeta(data.commission.status) : null
  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/40" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
      <aside role="dialog" aria-modal="true" aria-label="Chi tiết đối soát booking" className="h-full w-full max-w-xl overflow-y-auto bg-white p-5 shadow-2xl" data-testid="commission-drawer">
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-lg font-semibold text-slate-900">Chi tiết đối soát booking</h2>
          <button type="button" className="grid size-9 place-items-center rounded-lg text-slate-500 hover:bg-slate-100" aria-label="Đóng" onClick={onClose}><X className="size-5" /></button>
        </div>
        {status === 'loading' && <div className="mt-4"><OwnerLoading rows={3} /></div>}
        {status === 'error' && <div className="mt-4"><OwnerError message={error?.message} onRetry={reload} /></div>}
        {status === 'success' && (
          <div className="mt-4 grid gap-6">
            <section>
              <div className="flex flex-wrap items-center gap-2">
                <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', meta.className)} data-testid="drawer-status">{meta.label}</span>
                {data.commission.overdue && <span className="rounded-full bg-red-100 px-2.5 py-1 text-xs font-bold text-red-700" data-testid="drawer-overdue">Quá hạn</span>}
                {data.unconfirmedPayment && <span className="rounded-full bg-amber-100 px-2.5 py-1 text-xs font-bold text-amber-800" data-testid="drawer-unconfirmed" title="Booking tự hoàn thành sau giờ chơi; hoa hồng vẫn được ghi nhận">Chưa xác nhận thu tiền</span>}
                <span className="text-xs font-bold text-slate-400" data-testid="drawer-code">{data.booking.bookingCode}</span>
              </div>
              <p className="mt-2 text-base font-semibold text-slate-900">{data.booking.venueName}</p>
              <p className="text-xs font-medium text-slate-500">Người chơi {data.booking.playerName} · chơi {formatDateTime(data.booking.startTime)} – {formatDateTime(data.booking.endTime)}</p>
            </section>

            <Section title="Hoa hồng đã chốt" testId="drawer-commission">
              <dl className="divide-y divide-slate-100 rounded-xl border border-slate-200 px-3">
                <Line label="Chính sách phí" value={`${data.commission.feePolicyName ?? 'Không rõ'} · ${describeFee(data.commission.feeType, data.commission.feeValue, formatCurrency)}`} />
                <Line label="Doanh thu gộp (giá gốc)" value={formatCurrency(data.commission.grossAmount)} testId="drawer-gross" />
                <Line label="Hoa hồng tính trên giá gốc" value={formatCurrency(data.commission.baseCommission)} testId="drawer-base" />
                {data.commission.voucherDiscount > 0 && <Line label="Courtly bù do voucher" value={`− ${formatCurrency(data.commission.voucherDiscount)}`} testId="drawer-voucher" />}
                <Line label="Chủ sân phải trả" value={formatCurrency(data.commission.commissionAmount)} testId="drawer-amount" strong />
                {data.commission.courtlyOwed > 0 && (
                  <Line label="Courtly còn nợ chủ sân" value={data.commission.courtlyOwedClearedAt ? `${formatCurrency(data.commission.courtlyOwed)} (đã bù trừ ${formatDateTime(data.commission.courtlyOwedClearedAt)})` : formatCurrency(data.commission.courtlyOwed)} testId="drawer-owed" />
                )}
                <Line label="Ghi nhận" value={formatDateTime(data.commission.calculatedAt)} />
                <Line label="Hạn thanh toán" value={formatDateTime(data.commission.dueAt)} testId="drawer-due" />
                {data.commission.settledAt && <Line label="Đối soát xong" value={formatDateTime(data.commission.settledAt)} />}
              </dl>
            </Section>

            <Section title="Booking và thanh toán trực tiếp" testId="drawer-booking">
              <dl className="divide-y divide-slate-100 rounded-xl border border-slate-200 px-3">
                <Line label="Giá gốc" value={formatCurrency(data.booking.totalAmount)} />
                {data.booking.discountAmount > 0 && <Line label={data.booking.voucherCode ? `Voucher ${data.booking.voucherCode}` : 'Giảm giá'} value={`− ${formatCurrency(data.booking.discountAmount)}`} testId="drawer-booking-voucher" />}
                <Line label="Người chơi phải trả" value={formatCurrency(data.booking.payableAmount)} testId="drawer-payable" strong />
                <Line label="Thanh toán" value={data.payment ? `${paymentStatusMeta(data.payment.status).label} · ${data.payment.method === 'cash' ? 'Tiền mặt' : 'Chuyển khoản chủ sân'}` : 'Chưa có thanh toán'} testId="drawer-payment" />
                {data.payment?.confirmedAt && <Line label="Chủ sân xác nhận thu" value={formatDateTime(data.payment.confirmedAt)} />}
                {data.payment?.rejectionReason && <Line label="Lý do từ chối" value={data.payment.rejectionReason} />}
              </dl>
              <p className="mt-2 text-xs font-medium text-slate-500">Chủ sân {data.owner.fullName} · {data.owner.email}{data.owner.phone ? ` · ${data.owner.phone}` : ''}. Courtly không giữ tiền sân của người chơi.</p>
            </Section>

            <Section title="Kỳ đối soát" testId="drawer-settlement">
              {data.settlement
                ? <p className="text-sm font-medium text-slate-700">Kỳ {formatIsoDate(data.settlement.periodFrom)} – {formatIsoDate(data.settlement.periodTo)} ({settlementStatusMeta(data.settlement.status).label}) · phải thu {formatCurrency(data.settlement.amountDue)}, đã thu {formatCurrency(data.settlement.amountPaid)}</p>
                : <p className="text-sm font-medium text-slate-500">Chưa gán kỳ đối soát.</p>}
            </Section>

            <Section title={`Hoàn tiền (${data.refunds.length})`} testId="drawer-refunds">
              {data.refunds.length === 0 ? <p className="text-sm font-medium text-slate-500" data-testid="drawer-refunds-empty">Không có khoản hoàn nào.</p> : (
                <ul className="grid gap-2">
                  {data.refunds.map((item) => (
                    <li key={item.id} className="rounded-lg border border-slate-200 p-3 text-sm" data-testid="drawer-refund">
                      <div className="flex items-center justify-between gap-2"><span className="font-semibold text-slate-800">{formatCurrency(item.amount)}</span><span className="text-xs font-bold text-slate-600">{REFUND_STATUS_LABEL[item.status] ?? item.status}</span></div>
                      {item.reason && <p className="mt-1 text-xs font-medium text-slate-500">{item.reason}</p>}
                    </li>
                  ))}
                </ul>
              )}
            </Section>

            <Section title={`Tranh chấp (${data.disputes.length})`} testId="drawer-disputes">
              {data.disputes.length === 0 ? <p className="text-sm font-medium text-slate-500" data-testid="drawer-disputes-empty">Không có tranh chấp.</p> : (
                <ul className="grid gap-2">
                  {data.disputes.map((item) => (
                    <li key={item.id} className="rounded-lg border border-slate-200 p-3 text-sm" data-testid="drawer-dispute">
                      <div className="flex items-center justify-between gap-2"><span className="font-semibold text-slate-800">{item.category}</span><span className="text-xs font-bold text-slate-600">{DISPUTE_STATUS_LABEL[item.status] ?? item.status}</span></div>
                      <p className="mt-1 text-xs font-medium text-slate-500">{formatDateTime(item.createdAt)}{item.resolution ? ` · kết luận ${item.resolution}` : ''}</p>
                    </li>
                  ))}
                </ul>
              )}
            </Section>

            <Section title={`Lịch sử điều chỉnh (${data.history.length})`} testId="drawer-history">
              {data.history.length === 0 ? <p className="text-sm font-medium text-slate-500" data-testid="drawer-history-empty">Chưa có điều chỉnh nào.</p> : (
                <ol className="grid gap-2">
                  {data.history.map((entry) => (
                    <li key={entry.id} className="rounded-lg border border-slate-200 p-3 text-sm" data-testid="drawer-history-item">
                      <p className="font-semibold text-slate-800">{HISTORY_ACTION_LABEL[entry.action] ?? entry.action}</p>
                      <p className="text-xs font-medium text-slate-500">{entry.actorName ?? 'Hệ thống'} · {formatDateTime(entry.createdAt)}</p>
                      {describeChange(entry) && <p className="mt-1 break-words text-xs font-medium text-slate-600">{describeChange(entry)}</p>}
                    </li>
                  ))}
                </ol>
              )}
            </Section>
          </div>
        )}
      </aside>
    </div>
  )
}

export { CommissionDetailDrawer }
