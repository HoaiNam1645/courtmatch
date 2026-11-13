import { useState } from 'react'
import { ExternalLink, Info } from 'lucide-react'
import { PAYMENT_METHOD_LABEL, paymentStatusMeta } from '../../../api/ownerBookings'
import { fetchOwnerPayments, PAYMENT_TABS, safeProofUrl } from '../../../api/ownerPayments'
import { fetchOwnerVenues } from '../../../api/ownerVenues'
import { useApiResource } from '../../../hooks/useApiResource'
import { classNames, formatCurrency } from '../../../shared/utils'
import { OwnerLayout } from '../components/OwnerLayout'
import { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading } from '../components/OwnerUi'
import { formatDateTime } from '../ownerDates'
import { inputClass, secondaryButtonClass } from '../ownerStyles'

function PaymentCard({ item }) {
  const meta = paymentStatusMeta(item.status)
  const proof = item.proofUrl ? safeProofUrl(item.proofUrl) : null
  return (
    <li data-testid={`payment-${item.bookingCode}`} className="rounded-xl border border-emerald-100 bg-white p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', meta.className)} data-testid="payment-status">{meta.label}</span>
          <span className="text-xs font-bold text-stone-400">{item.bookingCode}</span>
        </div>
        <p className="text-base font-semibold text-emerald-700">{formatCurrency(item.amount)}</p>
      </div>
      <p className="mt-2 text-sm font-semibold text-emerald-950">{item.venueName}</p>
      <p className="text-sm font-medium text-stone-600">{item.playerName}{item.playerPhone ? ` · ${item.playerPhone}` : ''}</p>
      <dl className="mt-3 grid gap-1 text-xs font-medium text-stone-500">
        <div>{PAYMENT_METHOD_LABEL[item.method] ?? item.method}</div>
        {item.method === 'owner_bank_transfer' && item.bankAccountNo && (
          <div>Tài khoản nhận: {item.bankName} · {item.bankAccountNo} · {item.bankAccountName} · nội dung <span className="font-semibold text-stone-700">{item.referenceContent}</span></div>
        )}
        {item.status === 'awaiting_payment' && item.expiredAt && <div>Hạn thanh toán: {formatDateTime(item.expiredAt)}</div>}
        {item.payerReportedAt && <div>Người chơi báo đã trả: {formatDateTime(item.payerReportedAt)}</div>}
        {item.confirmedAt && <div>Chủ sân xác nhận thu: {formatDateTime(item.confirmedAt)}</div>}
        {item.rejectionReason && <div className="text-red-700">Chưa nhận được tiền: {item.rejectionReason}</div>}
        {proof && <div><a className="inline-flex items-center gap-1 font-semibold text-emerald-700 underline" href={proof} target="_blank" rel="noreferrer noopener" data-testid="payment-proof">Xem bằng chứng chuyển khoản<ExternalLink className="size-3" /></a></div>}
      </dl>
    </li>
  )
}

/** 2.3.16 — theo dõi thanh toán trực tiếp của người chơi cho các sân của chủ sân. */
function OwnerPaymentsPage() {
  const [filters, setFilters] = useState({ venueId: '', status: 'all', page: 0 })
  const venues = useApiResource(() => fetchOwnerVenues(), [])
  const { status, data, error, reload } = useApiResource(
    () => fetchOwnerPayments(filters),
    [filters.venueId, filters.status, filters.page],
    { isEmpty: (result) => result.payments.length === 0 },
  )

  function setFilter(patch) {
    setFilters((current) => ({ ...current, page: 0, ...patch }))
  }

  const counts = data?.statusCounts ?? {}
  const total = Object.values(counts).reduce((sum, value) => sum + value, 0)

  return (
    <OwnerLayout eyebrow="2.3.16 · Thanh toán trực tiếp" title="Thanh toán trực tiếp" description="Theo dõi các khoản người chơi thanh toán trực tiếp cho sân của bạn.">
      <div className="mb-5 flex items-start gap-3 rounded-xl border border-emerald-100 bg-white p-4 text-sm font-medium text-stone-600">
        <Info className="mt-0.5 size-4 shrink-0 text-emerald-700" />
        Courtly không nhận, giữ hay chi trả tiền sân nên không có số dư ở đây. Đây là trạng thái và bằng chứng thanh toán giữa người chơi và bạn.
      </div>

      <OwnerCard>
        <div className="flex gap-2 overflow-x-auto pb-1" role="tablist" aria-label="Trạng thái thanh toán">
          {PAYMENT_TABS.map((tab) => {
            const label = tab === 'all' ? 'Tất cả' : paymentStatusMeta(tab).label
            const count = tab === 'all' ? total : counts[tab] ?? 0
            return (
              <button key={tab} type="button" role="tab" aria-selected={filters.status === tab} data-testid={`payment-tab-${tab}`} className={classNames('flex h-10 shrink-0 items-center gap-2 rounded-lg px-4 text-sm font-semibold', filters.status === tab ? 'bg-emerald-700 text-white' : 'bg-stone-50 text-stone-600')} onClick={() => setFilter({ status: tab })}>
                {label}<span className={classNames('rounded-full px-2 text-xs font-bold', filters.status === tab ? 'bg-white/20' : 'bg-stone-200')}>{count}</span>
              </button>
            )
          })}
        </div>
        <label className="mt-4 block sm:w-72">
          <span className="text-xs font-semibold text-stone-600">Sân</span>
          <select className={`${inputClass} mt-1`} aria-label="Lọc theo sân" value={filters.venueId} onChange={(event) => setFilter({ venueId: event.target.value })}>
            <option value="">Tất cả sân</option>
            {(venues.data?.venues ?? []).map((venue) => <option key={venue.id} value={venue.id}>{venue.name}</option>)}
          </select>
        </label>

        <div className="mt-5">
          {status === 'loading' && <OwnerLoading rows={3} />}
          {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
          {status === 'empty' && <OwnerEmpty title="Không có thanh toán nào" description={filters.status === 'all' && !filters.venueId ? 'Thanh toán xuất hiện sau khi bạn chấp nhận một yêu cầu đặt sân.' : 'Không có thanh toán ở trạng thái hoặc sân đã chọn.'} />}
          {status === 'success' && (
            <>
              <ul className="grid gap-3 lg:grid-cols-2" data-testid="payment-list">
                {data.payments.map((item) => <PaymentCard key={item.paymentId} item={item} />)}
              </ul>
              {data.totalPages > 1 && (
                <div className="mt-5 flex items-center justify-between gap-3">
                  <button type="button" className={secondaryButtonClass} disabled={filters.page === 0} onClick={() => setFilters((current) => ({ ...current, page: current.page - 1 }))}>Trang trước</button>
                  <p className="text-sm font-semibold text-stone-600">Trang {data.page + 1}/{data.totalPages} · {data.totalElements} thanh toán</p>
                  <button type="button" className={secondaryButtonClass} disabled={filters.page + 1 >= data.totalPages} onClick={() => setFilters((current) => ({ ...current, page: current.page + 1 }))}>Trang sau</button>
                </div>
              )}
            </>
          )}
        </div>
      </OwnerCard>
    </OwnerLayout>
  )
}

export default OwnerPaymentsPage
