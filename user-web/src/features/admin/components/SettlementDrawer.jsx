import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import { fetchAdminSettlementDetail } from '../../../api/adminSettlements'
import { commissionStatusMeta } from '../../../api/ownerCommissions'
import { commissionPaymentStatusMeta } from '../../../api/ownerCommissionPayments'
import { settlementStatusMeta } from '../../../api/ownerSettlements'
import { useApiResource } from '../../../hooks/useApiResource'
import { classNames, formatCurrency } from '../../../shared/utils'
import { OwnerError, OwnerLoading } from '../../owner/components/OwnerUi'
import { formatDateTime, formatIsoDate } from '../../owner/ownerDates'
import { secondaryButtonClass } from '../../owner/ownerStyles'
import { HISTORY_ACTION_LABEL } from '../../../api/adminCommissionTransactions'
import { PaymentReviewDialog } from './PaymentReviewDialog'
import { RecordPaymentDialog } from './RecordPaymentDialog'

const RESTRICTION_LABEL = { 0: 'Không bị hạn chế', 1: 'Mức 1 · chặn nhận booking mới', 2: 'Mức 2 · tạm ẩn sân' }

function Line({ label, value, testId, strong }) {
  return (
    <div className="flex items-start justify-between gap-3 py-1.5 text-sm">
      <dt className="font-medium text-slate-500">{label}</dt>
      <dd className={classNames('text-right', strong ? 'font-semibold text-slate-900' : 'font-medium text-slate-700')} data-testid={testId}>{value}</dd>
    </div>
  )
}

/** 2.3.46 — chi tiết công nợ của một kỳ và các booking cấu thành. */
function SettlementDrawer({ settlementId, onClose, onOpenBooking, onChanged, escapeDisabled = false }) {
  const [page, setPage] = useState(0)
  const [reviewing, setReviewing] = useState(null)
  const [rejecting, setRejecting] = useState(null)
  const [recording, setRecording] = useState(false)
  const [notice, setNotice] = useState('')
  const { status, data, error, reload } = useApiResource(() => fetchAdminSettlementDetail(settlementId, { page }), [settlementId, page])

  useEffect(() => {
    // Khi một ngăn khác mở chồng lên, Escape chỉ đóng ngăn trên cùng.
    function onKey(event) {
      if (event.key === 'Escape' && !escapeDisabled && !reviewing && !rejecting && !recording) onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose, escapeDisabled, reviewing, rejecting, recording])

  const settlement = data?.settlement
  const meta = settlement ? settlementStatusMeta(settlement.status) : null
  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/40" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
      <aside role="dialog" aria-modal="true" aria-label="Chi tiết kỳ công nợ" className="h-full w-full max-w-xl overflow-y-auto bg-white p-5 shadow-2xl" data-testid="settlement-drawer">
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-lg font-semibold text-slate-900">Chi tiết kỳ công nợ</h2>
          <button type="button" className="grid size-9 place-items-center rounded-lg text-slate-500 hover:bg-slate-100" aria-label="Đóng" onClick={onClose}><X className="size-5" /></button>
        </div>
        {status === 'loading' && !data && <div className="mt-4"><OwnerLoading rows={3} /></div>}
        {status === 'error' && <div className="mt-4"><OwnerError message={error?.message} onRetry={reload} /></div>}
        {notice && <p role="status" className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm font-semibold text-emerald-800" data-testid="sd-notice">{notice}</p>}
        {data && (
          <div className="mt-4 grid gap-6">
            <section>
              <div className="flex flex-wrap items-center gap-2">
                <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', meta.className)} data-testid="sd-status">{meta.label}</span>
                {settlement.overdue && <span className="rounded-full bg-red-100 px-2.5 py-1 text-xs font-bold text-red-700" data-testid="sd-overdue">Quá hạn</span>}
                {settlement.pendingReports > 0 && <span className="rounded-full bg-sky-100 px-2.5 py-1 text-xs font-bold text-sky-700" data-testid="sd-pending-reports">{settlement.pendingReports} báo cáo chờ xác nhận</span>}
              </div>
              <p className="mt-2 text-base font-semibold text-slate-900" data-testid="sd-period">Kỳ {formatIsoDate(settlement.periodFrom)} – {formatIsoDate(settlement.periodTo)}</p>
              <p className="text-xs font-medium text-slate-500">Chủ sân {settlement.ownerName} · {settlement.ownerEmail}{data.ownerPhone ? ` · ${data.ownerPhone}` : ''}</p>
            </section>

            <section data-testid="sd-amounts">
              <h3 className="text-sm font-semibold text-slate-900">Công nợ của kỳ</h3>
              <dl className="mt-2 divide-y divide-slate-100 rounded-xl border border-slate-200 px-3">
                <Line label="Hoa hồng các booking" value={formatCurrency(settlement.commissionTotal)} testId="sd-commission" />
                {settlement.adjustmentTotal !== 0 && <Line label="Điều chỉnh" value={formatCurrency(settlement.adjustmentTotal)} testId="sd-adjustment" />}
                <Line label="Phải thu" value={formatCurrency(settlement.amountDue)} testId="sd-due" strong />
                <Line label="Đã thu" value={formatCurrency(settlement.amountPaid)} testId="sd-paid" />
                <Line label="Còn lại" value={formatCurrency(settlement.remaining)} testId="sd-remaining" strong />
                {settlement.paidAt && <Line label="Thanh toán xong" value={formatDateTime(settlement.paidAt)} />}
              </dl>
            </section>

            <section data-testid="sd-owner">
              <h3 className="text-sm font-semibold text-slate-900">Tình trạng chủ sân</h3>
              <dl className="mt-2 divide-y divide-slate-100 rounded-xl border border-slate-200 px-3">
                <Line label="Hạn chế do công nợ" value={RESTRICTION_LABEL[data.restrictionLevel] ?? `Mức ${data.restrictionLevel}`} testId="sd-restriction" />
                <Line label="Courtly còn nợ chủ sân" value={formatCurrency(data.owedByCourtly)} testId="sd-owed" />
              </dl>
            </section>

            <section data-testid="sd-reports">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h3 className="text-sm font-semibold text-slate-900">Báo cáo thanh toán ({data.reports.length})</h3>
                {settlement.remaining > 0 && settlement.status !== 'disputed' && <button type="button" className="h-9 rounded-lg border border-slate-300 px-3 text-xs font-semibold text-slate-700 hover:bg-slate-50" data-testid="sd-record" onClick={() => setRecording(true)}>Ghi nhận thanh toán</button>}
              </div>
              {data.reports.length === 0 ? <p className="mt-2 text-sm font-medium text-slate-500" data-testid="sd-reports-empty">Chủ sân chưa gửi báo cáo thanh toán cho kỳ này.</p> : (
                <ul className="mt-2 grid gap-2">
                  {data.reports.map((report) => {
                    const reportMeta = commissionPaymentStatusMeta(report.status)
                    return (
                      <li key={report.id} className="rounded-lg border border-slate-200 p-3 text-sm" data-testid="sd-report">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <span className="font-semibold text-slate-800">{formatCurrency(report.amount)} · {report.referenceCode}</span>
                          <span className={classNames('rounded-full px-2 py-0.5 text-xs font-bold', reportMeta.className)}>{reportMeta.label}</span>
                        </div>
                        <p className="mt-1 text-xs font-medium text-slate-500">Thanh toán {formatIsoDate(report.paidOn)} · gửi {formatDateTime(report.createdAt)}</p>
                        {report.note && <p className="mt-1 text-xs font-medium text-slate-600">{report.note}</p>}
                        {report.proofUrl && <a className="mt-1 inline-block break-all text-xs font-semibold text-sky-700 underline" href={report.proofUrl} target="_blank" rel="noreferrer noopener">Xem bằng chứng</a>}
                        {report.status === 'pending_review' && (
                          <div className="mt-2 flex gap-2">
                            <button type="button" className="h-9 rounded-lg bg-slate-800 px-3 text-xs font-semibold text-white hover:bg-slate-900" data-testid="sd-report-confirm" onClick={() => setReviewing({ ...report, ownerName: settlement.ownerName, settlement: { periodFrom: settlement.periodFrom, periodTo: settlement.periodTo } })}>Xác nhận</button>
                            <button type="button" className="h-9 rounded-lg border border-red-200 px-3 text-xs font-semibold text-red-700 hover:bg-red-50" data-testid="sd-report-reject" onClick={() => setRejecting({ ...report, ownerName: settlement.ownerName, settlement: { periodFrom: settlement.periodFrom, periodTo: settlement.periodTo } })}>Từ chối</button>
                          </div>
                        )}
                        {report.reviewedAt && <p className="mt-1 text-xs font-medium text-slate-500">{report.reviewedByName ?? 'Quản trị viên'} xử lý {formatDateTime(report.reviewedAt)}{report.reviewNote ? ` · ${report.reviewNote}` : ''}</p>}
                      </li>
                    )
                  })}
                </ul>
              )}
            </section>

            <section data-testid="sd-bookings">
              <h3 className="text-sm font-semibold text-slate-900">Booking cấu thành ({data.bookings.totalElements})</h3>
              {data.bookings.rows.length === 0 ? <p className="mt-2 text-sm font-medium text-slate-500" data-testid="sd-bookings-empty">Kỳ này chưa có booking nào.</p> : (
                <ul className="mt-2 grid gap-2">
                  {data.bookings.rows.map((item) => {
                    const itemMeta = commissionStatusMeta(item.status)
                    return (
                      <li key={item.commissionId} className={classNames('rounded-lg border p-3 text-sm', item.overdue ? 'border-red-200 bg-red-50/40' : 'border-slate-200')} data-testid={`sd-booking-${item.bookingCode}`}>
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="font-semibold text-slate-800">{item.bookingCode}</span>
                            <span className={classNames('rounded-full px-2 py-0.5 text-xs font-bold', itemMeta.className)}>{itemMeta.label}</span>
                            {item.overdue && <span className="rounded-full bg-red-100 px-2 py-0.5 text-xs font-bold text-red-700">Quá hạn</span>}
                            {item.unconfirmedPayment && <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-bold text-amber-800" data-testid="sd-booking-unconfirmed">Chưa xác nhận thu tiền</span>}
                          </div>
                          <span className="font-semibold text-slate-900">{formatCurrency(item.commissionAmount)}</span>
                        </div>
                        <p className="mt-1 text-xs font-medium text-slate-500">{item.venueName} · doanh thu gộp {formatCurrency(item.grossAmount)} · hạn {formatDateTime(item.dueAt)}</p>
                        {onOpenBooking && <button type="button" className="mt-2 text-xs font-semibold text-slate-700 underline" onClick={() => onOpenBooking(item.commissionId)}>Xem đối soát booking</button>}
                      </li>
                    )
                  })}
                </ul>
              )}
              {data.bookings.totalPages > 1 && (
                <div className="mt-3 flex items-center justify-between gap-2">
                  <button type="button" className={secondaryButtonClass} disabled={page === 0} onClick={() => setPage((current) => current - 1)}>Trang trước</button>
                  <span className="text-xs font-semibold text-slate-600" data-testid="sd-bookings-page">Trang {data.bookings.page + 1}/{data.bookings.totalPages}</span>
                  <button type="button" className={secondaryButtonClass} disabled={page + 1 >= data.bookings.totalPages} onClick={() => setPage((current) => current + 1)}>Trang sau</button>
                </div>
              )}
            </section>

            <section data-testid="sd-history">
              <h3 className="text-sm font-semibold text-slate-900">Lịch sử ({data.history.length})</h3>
              {data.history.length === 0 ? <p className="mt-2 text-sm font-medium text-slate-500" data-testid="sd-history-empty">Chưa có thao tác nào được ghi lại.</p> : (
                <ol className="mt-2 grid gap-2">
                  {data.history.map((entry) => (
                    <li key={entry.id} className="rounded-lg border border-slate-200 p-3 text-sm" data-testid="sd-history-item">
                      <p className="font-semibold text-slate-800">{HISTORY_ACTION_LABEL[entry.action] ?? entry.action}</p>
                      <p className="text-xs font-medium text-slate-500">{entry.actorName ?? 'Hệ thống'} · {formatDateTime(entry.createdAt)}</p>
                    </li>
                  ))}
                </ol>
              )}
            </section>
          </div>
        )}
      </aside>
      {recording && <RecordPaymentDialog owners={[{ id: settlement.ownerId, fullName: settlement.ownerName, email: settlement.ownerEmail }]} fixed={{ ownerId: settlement.ownerId, settlementId: settlement.id, remaining: settlement.remaining, label: `Kỳ ${formatIsoDate(settlement.periodFrom)} – ${formatIsoDate(settlement.periodTo)} · còn ${formatCurrency(settlement.remaining)}` }} onClose={() => setRecording(false)} onDone={(result, message) => { setRecording(false); setNotice(message); reload(); onChanged?.() }} />}
      {rejecting && <PaymentReviewDialog payment={rejecting} mode="reject" onClose={() => setRejecting(null)} onDone={(result, message) => { setRejecting(null); setNotice(message); reload(); onChanged?.() }} />}
      {reviewing && <PaymentReviewDialog payment={reviewing} onClose={() => setReviewing(null)} onDone={(result, message) => { setReviewing(null); setNotice(message); reload(); onChanged?.() }} />}
    </div>
  )
}

export { SettlementDrawer }
