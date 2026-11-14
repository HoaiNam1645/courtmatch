import { useState } from 'react'
import { Info } from 'lucide-react'
import { fetchSettlements, SETTLEMENT_TABS, settlementStatusMeta } from '../../../api/ownerSettlements'
import { useApiResource } from '../../../hooks/useApiResource'
import { classNames, formatCurrency } from '../../../shared/utils'
import { formatIsoDate } from '../ownerDates'
import { secondaryButtonClass } from '../ownerStyles'
import { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading } from './OwnerUi'
import { SettlementDrawer } from './SettlementDrawer'

/** 2.3.23 — các kỳ đối soát hoa hồng với Courtly. */
function SettlementsTab() {
  const [filters, setFilters] = useState({ status: 'all', page: 0 })
  const [openId, setOpenId] = useState(null)
  const { status, data, error, reload } = useApiResource(
    () => fetchSettlements(filters),
    [filters.status, filters.page],
    { isEmpty: (result) => result.settlements.length === 0 },
  )

  return (
    <div className="grid gap-5">
      <div className="flex items-start gap-3 rounded-xl border border-emerald-100 bg-white p-4 text-sm font-medium text-stone-600">
        <Info className="mt-0.5 size-4 shrink-0 text-emerald-700" />
        <p>Courtly chốt hoa hồng của các booking thành từng kỳ đối soát. Mỗi kỳ cho biết số phải thanh toán, số đã thanh toán và số còn lại.</p>
      </div>

      <OwnerCard>
        <div className="flex gap-2 overflow-x-auto pb-1" role="tablist" aria-label="Trạng thái kỳ đối soát">
          {SETTLEMENT_TABS.map((tab) => {
            const count = tab === 'all' ? null : data?.statusCounts?.[tab]
            return (
              <button key={tab} type="button" role="tab" aria-selected={filters.status === tab} data-testid={`settlement-tab-${tab}`} className={classNames('h-10 shrink-0 rounded-lg px-4 text-sm font-semibold', filters.status === tab ? 'bg-emerald-700 text-white' : 'bg-stone-50 text-stone-600')} onClick={() => setFilters({ status: tab, page: 0 })}>
                {tab === 'all' ? 'Tất cả' : settlementStatusMeta(tab).label}{count ? ` (${count})` : ''}
              </button>
            )
          })}
        </div>

        <div className="mt-5">
          {status === 'loading' && <OwnerLoading rows={3} />}
          {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
          {status === 'empty' && <OwnerEmpty title="Chưa có kỳ đối soát nào" description="Kỳ đối soát xuất hiện sau khi Courtly chốt hoa hồng của các booking đã hoàn thành." />}
          {status === 'success' && (
            <>
              <ul className="grid gap-3" data-testid="settlement-list">
                {data.settlements.map((item) => {
                  const meta = settlementStatusMeta(item.status)
                  return (
                    <li key={item.id} data-testid={`settlement-${item.periodFrom}`} className="rounded-xl border border-emerald-100 p-4">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', meta.className)} data-testid="settlement-status">{meta.label}</span>
                        <p className="text-base font-semibold text-emerald-800" data-testid="settlement-remaining">Còn {formatCurrency(item.remainingAmount)}</p>
                      </div>
                      <p className="mt-2 text-sm font-semibold text-emerald-950">Kỳ {formatIsoDate(item.periodFrom)} – {formatIsoDate(item.periodTo)}</p>
                      <p className="text-sm font-medium text-stone-600">Phải trả {formatCurrency(item.amountDue)} · đã trả {formatCurrency(item.amountPaid)} · {item.bookingCount} booking</p>
                      <button type="button" className="mt-2 text-sm font-semibold text-emerald-700 hover:underline" aria-label={`Xem chi tiết kỳ ${formatIsoDate(item.periodFrom)}`} onClick={() => setOpenId(item.id)}>Xem chi tiết</button>
                    </li>
                  )
                })}
              </ul>
              {data.totalPages > 1 && (
                <div className="mt-5 flex items-center justify-between gap-3">
                  <button type="button" className={secondaryButtonClass} disabled={filters.page === 0} onClick={() => setFilters((current) => ({ ...current, page: current.page - 1 }))}>Trang trước</button>
                  <p className="text-sm font-semibold text-stone-600">Trang {data.page + 1}/{data.totalPages} · {data.totalElements} kỳ</p>
                  <button type="button" className={secondaryButtonClass} disabled={filters.page + 1 >= data.totalPages} onClick={() => setFilters((current) => ({ ...current, page: current.page + 1 }))}>Trang sau</button>
                </div>
              )}
            </>
          )}
        </div>
      </OwnerCard>
      {openId && <SettlementDrawer settlementId={openId} onClose={() => setOpenId(null)} />}
    </div>
  )
}

export { SettlementsTab }
