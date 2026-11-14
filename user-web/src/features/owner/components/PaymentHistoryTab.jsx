import { useState } from 'react'
import { Info } from 'lucide-react'
import { COMMISSION_PAYMENT_TABS, commissionPaymentStatusMeta, fetchCommissionPaymentHistory } from '../../../api/ownerCommissionPayments'
import { useApiResource } from '../../../hooks/useApiResource'
import { classNames, formatCurrency } from '../../../shared/utils'
import { formatDateTime, formatIsoDate } from '../ownerDates'
import { inputClass, secondaryButtonClass } from '../ownerStyles'
import { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading } from './OwnerUi'

/** 2.3.26 — lịch sử các lần chủ sân gửi thanh toán hoa hồng cho Courtly. */
function PaymentHistoryTab() {
  const [filters, setFilters] = useState({ status: 'all', from: '', to: '', page: 0 })
  const invalidRange = filters.from && filters.to && filters.to < filters.from
  const { status, data, error, reload } = useApiResource(
    () => (invalidRange ? Promise.resolve(null) : fetchCommissionPaymentHistory(filters)),
    [filters.status, filters.from, filters.to, filters.page],
    { isEmpty: (result) => result !== null && result.payments.length === 0 },
  )
  const counts = data?.statusCounts

  function setFilter(patch) {
    setFilters((current) => ({ ...current, page: 0, ...patch }))
  }

  return (
    <div className="grid gap-5">
      <div className="flex items-start gap-3 rounded-xl border border-emerald-100 bg-white p-4 text-sm font-medium text-stone-600">
        <Info className="mt-0.5 size-4 shrink-0 text-emerald-700" />
        <p>Mỗi dòng là một lần bạn gửi thông tin thanh toán hoa hồng. Chỉ khoản được Courtly xác nhận mới làm giảm công nợ.</p>
      </div>

      <OwnerCard>
        <div className="flex gap-2 overflow-x-auto pb-1" role="tablist" aria-label="Trạng thái thanh toán">
          {COMMISSION_PAYMENT_TABS.map((tab) => {
            const count = tab === 'all' ? null : counts?.[tab]
            return (
              <button key={tab} type="button" role="tab" aria-selected={filters.status === tab} data-testid={`payment-history-tab-${tab}`} className={classNames('h-10 shrink-0 rounded-lg px-4 text-sm font-semibold', filters.status === tab ? 'bg-emerald-700 text-white' : 'bg-stone-50 text-stone-600')} onClick={() => setFilter({ status: tab })}>
                {tab === 'all' ? 'Tất cả' : commissionPaymentStatusMeta(tab).label}{count ? ` (${count})` : ''}
              </button>
            )
          })}
        </div>
        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          <input type="date" className={inputClass} aria-label="Thanh toán từ ngày" value={filters.from} onChange={(event) => setFilter({ from: event.target.value })} />
          <input type="date" className={inputClass} aria-label="Thanh toán đến ngày" value={filters.to} onChange={(event) => setFilter({ to: event.target.value })} />
        </div>
        {invalidRange && <p className="mt-2 text-sm font-medium text-red-600" role="alert">Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.</p>}

        <div className="mt-5">
          {!invalidRange && status === 'loading' && <OwnerLoading rows={3} />}
          {!invalidRange && status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
          {!invalidRange && status === 'empty' && <OwnerEmpty title="Chưa có lần thanh toán nào" description="Các lần bạn gửi thông tin thanh toán hoa hồng sẽ hiện ở đây." />}
          {!invalidRange && status === 'success' && data && (
            <>
              <p className="mb-3 text-sm font-semibold text-stone-600">Tổng số tiền của danh sách: <span className="text-lg font-semibold text-emerald-800" data-testid="payment-history-total">{formatCurrency(data.totalAmount)}</span></p>
              <ul className="grid gap-3" data-testid="payment-history">
                {data.payments.map((item) => {
                  const meta = commissionPaymentStatusMeta(item.status)
                  return (
                    <li key={item.id} data-testid={`payment-history-${item.referenceCode}`} className="rounded-xl border border-emerald-100 p-4">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', meta.className)} data-testid="payment-history-status">{meta.label}</span>
                        <p className="text-base font-semibold text-emerald-800" data-testid="payment-history-amount">{formatCurrency(item.amount)}</p>
                      </div>
                      <p className="mt-2 text-sm font-semibold text-emerald-950">Mã tham chiếu {item.referenceCode}</p>
                      <p className="text-sm font-medium text-stone-600">Thanh toán ngày {formatIsoDate(item.paidOn)}{item.periodFrom ? ` · kỳ ${formatIsoDate(item.periodFrom)} – ${formatIsoDate(item.periodTo)}` : ' · vào công nợ chung'}</p>
                      <p className="mt-1 text-xs font-medium text-stone-500">Gửi lúc {formatDateTime(item.createdAt)}{item.reviewedAt ? ` · Courtly xử lý lúc ${formatDateTime(item.reviewedAt)}` : ''}</p>
                      {item.note && <p className="mt-1 text-xs font-medium text-stone-500">Ghi chú: {item.note}</p>}
                      {item.proofUrl && <a className="mt-1 inline-block text-xs font-semibold text-emerald-700 hover:underline" href={item.proofUrl} target="_blank" rel="noopener noreferrer">Xem chứng từ</a>}
                      {item.status === 'rejected' && item.reviewNote && <p className="mt-2 rounded-lg bg-red-50 px-3 py-2 text-xs font-semibold text-red-800" data-testid="payment-history-reason">Lý do từ chối: {item.reviewNote}</p>}
                    </li>
                  )
                })}
              </ul>
              {data.totalPages > 1 && (
                <div className="mt-5 flex items-center justify-between gap-3">
                  <button type="button" className={secondaryButtonClass} disabled={filters.page === 0} onClick={() => setFilters((current) => ({ ...current, page: current.page - 1 }))}>Trang trước</button>
                  <p className="text-sm font-semibold text-stone-600">Trang {data.page + 1}/{data.totalPages} · {data.totalElements} lần</p>
                  <button type="button" className={secondaryButtonClass} disabled={filters.page + 1 >= data.totalPages} onClick={() => setFilters((current) => ({ ...current, page: current.page + 1 }))}>Trang sau</button>
                </div>
              )}
            </>
          )}
        </div>
      </OwnerCard>
    </div>
  )
}

export { PaymentHistoryTab }
