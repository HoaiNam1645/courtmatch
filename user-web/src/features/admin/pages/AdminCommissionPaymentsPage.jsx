import { useRef, useState } from 'react'
import { CheckCircle2, Plus, Search, X } from 'lucide-react'
import { fetchAdminPayments, PAYMENT_TABS } from '../../../api/adminCommissionPayments'
import { commissionPaymentStatusMeta } from '../../../api/ownerCommissionPayments'
import { useApiResource } from '../../../hooks/useApiResource'
import { useDebounced } from '../../../hooks/useDebounced'
import { classNames, formatCurrency } from '../../../shared/utils'
import { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading } from '../../owner/components/OwnerUi'
import { formatDateTime, formatIsoDate } from '../../owner/ownerDates'
import { inputClass, primaryButtonClass, secondaryButtonClass } from '../../owner/ownerStyles'
import { AdminLayout } from '../components/AdminLayout'
import { PaymentReviewDialog } from '../components/PaymentReviewDialog'
import { RecordPaymentDialog } from '../components/RecordPaymentDialog'

const EMPTY_FILTERS = { status: 'pending_review', ownerId: '', from: '', to: '', page: 0 }

/** 2.3.47 — báo cáo thanh toán công nợ của chủ sân: đối chiếu và xác nhận; cũng là lịch sử thanh toán (2.3.50). */
function AdminCommissionPaymentsPage() {
  const [filters, setFilters] = useState(EMPTY_FILTERS)
  const [query, setQuery] = useState('')
  const q = useDebounced(query)
  const [reviewing, setReviewing] = useState(null)
  const [rejecting, setRejecting] = useState(null)
  const [recording, setRecording] = useState(false)
  const [notice, setNotice] = useState('')
  const invalidRange = Boolean(filters.from && filters.to && filters.to < filters.from)
  const { status, data, error, reload } = useApiResource(
    () => (invalidRange ? Promise.resolve(null) : fetchAdminPayments({ ...filters, q })),
    [filters.status, filters.ownerId, filters.from, filters.to, filters.page, q, invalidRange],
    { isEmpty: (result) => result !== null && result.rows.length === 0 },
  )
  const lastRef = useRef({ owners: [], statusCounts: {} })
  if (data) lastRef.current = { owners: data.owners, statusCounts: data.statusCounts }
  const { owners, statusCounts } = lastRef.current
  const filtered = filters.status !== 'pending_review' || filters.ownerId || filters.from || filters.to || q

  function setFilter(patch) {
    setFilters((current) => ({ ...current, page: 0, ...patch }))
  }
  const totalCount = Object.values(statusCounts).reduce((sum, value) => sum + value, 0)
  const tabLabel = (tab) => (tab === 'all' ? 'Tất cả' : commissionPaymentStatusMeta(tab).label)

  return (
    <AdminLayout
      eyebrow="2.3.47 – 2.3.49 · Thanh toán công nợ"
      actions={<button type="button" className={primaryButtonClass} data-testid="payment-record" onClick={() => setRecording(true)}><Plus className="size-4" />Ghi nhận thanh toán</button>}
      title="Thanh toán công nợ"
      description="Báo cáo chủ sân đã chuyển hoa hồng cho Courtly: đối chiếu số tiền, mã tham chiếu, bằng chứng rồi xác nhận, hoặc từ chối kèm lý do. Khoản chủ sân trả ngoài hệ thống thì ghi nhận trực tiếp."
    >
      {notice && <p role="status" className="mb-4 flex items-start gap-2 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-semibold text-emerald-800" data-testid="admin-notice"><CheckCircle2 className="mt-0.5 size-4 shrink-0" />{notice}</p>}
      <OwnerCard>
        <div className="flex flex-wrap gap-2" role="tablist" aria-label="Lọc theo trạng thái thanh toán">
          {PAYMENT_TABS.map((tab) => {
            const count = tab === 'all' ? totalCount : statusCounts[tab] ?? 0
            const active = filters.status === tab
            return (
              <button key={tab} type="button" role="tab" aria-selected={active} data-testid={`payment-tab-${tab}`}
                className={classNames('h-10 rounded-lg px-3 text-sm font-semibold transition', active ? 'bg-slate-800 text-white' : 'bg-slate-100 text-slate-700 hover:bg-slate-200')}
                onClick={() => setFilter({ status: tab })}>
                {tabLabel(tab)} <span className="ml-1 text-xs opacity-80" data-testid={`payment-tab-count-${tab}`}>{count}</span>
              </button>
            )
          })}
        </div>
        <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <label className="flex h-11 items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 focus-within:border-slate-700 sm:col-span-2 lg:col-span-1">
            <Search className="size-4 shrink-0 text-slate-400" />
            <input className="min-w-0 flex-1 text-sm outline-none placeholder:text-slate-400" aria-label="Tìm thanh toán" placeholder="Mã tham chiếu, tên hoặc email" value={query} maxLength={100} onChange={(event) => { setQuery(event.target.value); setFilters((current) => ({ ...current, page: 0 })) }} />
            {query && <button type="button" aria-label="Xóa tìm kiếm" onClick={() => setQuery('')}><X className="size-4 text-slate-400" /></button>}
          </label>
          <select className={inputClass} aria-label="Lọc theo chủ sân" value={filters.ownerId} onChange={(event) => setFilter({ ownerId: event.target.value })}>
            <option value="">Tất cả chủ sân</option>
            {owners.map((owner) => <option key={owner.id} value={owner.id}>{owner.fullName}</option>)}
          </select>
          <label className="block text-xs font-semibold text-slate-600">Thanh toán từ ngày<input type="date" className={`${inputClass} mt-1`} aria-label="Thanh toán từ ngày" value={filters.from} onChange={(event) => setFilter({ from: event.target.value })} /></label>
          <label className="block text-xs font-semibold text-slate-600">Thanh toán đến ngày<input type="date" className={`${inputClass} mt-1`} aria-label="Thanh toán đến ngày" value={filters.to} onChange={(event) => setFilter({ to: event.target.value })} /></label>
          {filtered && <button type="button" className={`${secondaryButtonClass} self-end`} data-testid="payment-clear" onClick={() => { setFilters(EMPTY_FILTERS); setQuery('') }}>Xóa bộ lọc</button>}
        </div>
        {invalidRange && <p className="mt-2 text-sm font-medium text-red-600" role="alert" data-testid="payment-range-error">Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.</p>}

        <div className="mt-5">
          {status === 'loading' && !data && <OwnerLoading rows={3} />}
          {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
          {status === 'empty' && <OwnerEmpty title={filters.status === 'pending_review' && !filtered ? 'Không có báo cáo nào đang chờ xác nhận' : 'Không có thanh toán nào'} description={filtered ? 'Thử bỏ bớt bộ lọc hoặc đổi từ khóa.' : 'Báo cáo thanh toán của chủ sân sẽ xuất hiện ở đây.'} />}
          {data && status !== 'empty' && (
            <>
              <div className="mb-4 grid gap-3 rounded-xl bg-slate-50 p-4 sm:grid-cols-2" data-testid="payment-totals">
                <div><p className="text-xs font-semibold text-slate-500">Số báo cáo</p><p className="text-lg font-semibold text-slate-900" data-testid="payment-total-count">{data.totals.count.toLocaleString('vi-VN')}</p></div>
                <div><p className="text-xs font-semibold text-slate-500">Tổng số tiền</p><p className="text-lg font-semibold text-slate-900" data-testid="payment-total-amount">{formatCurrency(data.totals.amount)}</p></div>
              </div>
              <ul className="grid gap-3" data-testid="payment-list">
                {data.rows.map((row) => {
                  const meta = commissionPaymentStatusMeta(row.status)
                  return (
                    <li key={row.id} data-testid={`payment-${row.referenceCode}`} className="rounded-xl border border-slate-200 p-4">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', meta.className)} data-testid="payment-status">{meta.label}</span>
                          <span className="text-xs font-bold text-slate-400" data-testid="payment-ref">{row.referenceCode}</span>
                          {row.recordedByAdmin && <span className="rounded-full bg-violet-100 px-2.5 py-1 text-xs font-bold text-violet-700" data-testid="payment-recorded">Admin ghi nhận</span>}
                        </div>
                        <div className="flex items-center gap-3">
                          <p className="text-base font-semibold text-slate-900" data-testid="payment-amount">{formatCurrency(row.amount)}</p>
                          {row.status === 'pending_review' && <button type="button" className="h-9 rounded-lg border border-red-200 px-3 text-xs font-semibold text-red-700 hover:bg-red-50" data-testid="payment-reject" aria-label={`Từ chối ${row.referenceCode}`} onClick={() => setRejecting(row)}>Từ chối</button>}
                          {row.status === 'pending_review' && <button type="button" className="h-9 rounded-lg bg-slate-800 px-3 text-xs font-semibold text-white hover:bg-slate-900" data-testid="payment-confirm" aria-label={`Xác nhận ${row.referenceCode}`} onClick={() => setReviewing(row)}>Xác nhận</button>}
                        </div>
                      </div>
                      <p className="mt-2 text-sm font-semibold text-slate-900" data-testid="payment-owner">{row.ownerName}</p>
                      <p className="text-xs font-medium text-slate-500">{row.ownerEmail} · thanh toán {formatIsoDate(row.paidOn)} · gửi {formatDateTime(row.createdAt)}</p>
                      <p className="mt-1 text-xs font-medium text-slate-600" data-testid="payment-target">{row.settlement ? `Kỳ ${formatIsoDate(row.settlement.periodFrom)} – ${formatIsoDate(row.settlement.periodTo)}` : 'Công nợ chung, không gắn kỳ'}</p>
                      {row.note && <p className="mt-1 text-xs font-medium text-slate-600">Chủ sân ghi chú: {row.note}</p>}
                      {row.proofUrl && <a className="mt-1 inline-block break-all text-xs font-semibold text-sky-700 underline" href={row.proofUrl} target="_blank" rel="noreferrer noopener">Xem bằng chứng</a>}
                      {row.reviewedAt && <p className="mt-1 text-xs font-medium text-slate-500" data-testid="payment-reviewed">{row.reviewedByName ?? 'Quản trị viên'} xử lý {formatDateTime(row.reviewedAt)}{row.reviewNote ? ` · ${row.reviewNote}` : ''}{row.courtlyOffsetAmount > 0 ? ` · bù trừ ${formatCurrency(row.courtlyOffsetAmount)} Courtly nợ chủ sân` : ''}</p>}
                    </li>
                  )
                })}
              </ul>
              {data.totalPages > 1 && (
                <div className="mt-5 flex items-center justify-between gap-3">
                  <button type="button" className={secondaryButtonClass} disabled={filters.page === 0} onClick={() => setFilters((current) => ({ ...current, page: current.page - 1 }))}>Trang trước</button>
                  <p className="text-sm font-semibold text-slate-600">Trang {data.page + 1}/{data.totalPages} · {data.totalElements.toLocaleString('vi-VN')} báo cáo</p>
                  <button type="button" className={secondaryButtonClass} disabled={filters.page + 1 >= data.totalPages} onClick={() => setFilters((current) => ({ ...current, page: current.page + 1 }))}>Trang sau</button>
                </div>
              )}
            </>
          )}
        </div>
      </OwnerCard>
      {recording && <RecordPaymentDialog owners={owners} onClose={() => setRecording(false)} onDone={(result, message) => { setRecording(false); setNotice(message); reload() }} />}
      {rejecting && <PaymentReviewDialog payment={rejecting} mode="reject" onClose={() => setRejecting(null)} onDone={(result, message) => { setRejecting(null); setNotice(message); reload() }} />}
      {reviewing && <PaymentReviewDialog payment={reviewing} onClose={() => setReviewing(null)} onDone={(result, message) => { setReviewing(null); setNotice(message); reload() }} />}
    </AdminLayout>
  )
}

export default AdminCommissionPaymentsPage
