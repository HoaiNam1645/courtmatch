import { useState } from 'react'
import { Search, X } from 'lucide-react'
import { fetchPartnerHistory, OUTCOME_LABEL, SOURCE_LABEL } from '../../../../api/adminMatching'
import { useApiResource } from '../../../../hooks/useApiResource'
import { useDebounced } from '../../../../hooks/useDebounced'
import { classNames } from '../../../../shared/utils'
import { OwnerEmpty, OwnerError, OwnerLoading } from '../../../owner/components/OwnerUi'
import { formatDateTime } from '../../../owner/ownerDates'
import { inputClass, secondaryButtonClass } from '../../../owner/ownerStyles'
import { PartnerHistoryDrawer } from './PartnerHistoryDrawer'

const OUTCOME_STYLE = {
  none: 'bg-slate-100 text-slate-600',
  pending: 'bg-amber-50 text-amber-800',
  accepted: 'bg-emerald-50 text-emerald-700',
  rejected: 'bg-red-50 text-red-700',
  expired: 'bg-orange-50 text-orange-700',
  cancelled: 'bg-slate-100 text-slate-600',
}

export function OutcomeBadge({ outcome }) {
  return <span className={classNames('inline-flex rounded-full px-2.5 py-1 text-xs font-semibold', OUTCOME_STYLE[outcome])} data-outcome={outcome}>{OUTCOME_LABEL[outcome] ?? outcome}</span>
}

/** 2.3.75 - lịch sử gợi ý đối tác và kết quả yêu cầu ghép cặp. */
export function PartnerHistorySection({ range, source }) {
  const [query, setQuery] = useState('')
  const [outcome, setOutcome] = useState('all')
  const [page, setPage] = useState(0)
  const [selectedId, setSelectedId] = useState(null)
  const q = useDebounced(query)
  const { status, data, error, reload } = useApiResource(
    () => fetchPartnerHistory({ ...range, source, q, outcome, page }),
    [range.from, range.to, source, q, outcome, page],
    { isEmpty: (result) => result.items.length === 0 },
  )
  const resetPage = (setter) => (value) => { setter(value); setPage(0) }

  return (
    <section className="grid min-w-0 grid-cols-1 gap-4" aria-label="Lịch sử ghép cặp người chơi" data-testid="partner-history">
      <div className="grid gap-3 sm:grid-cols-3">
        <label className="flex h-11 items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 focus-within:border-slate-700 sm:col-span-2">
          <Search className="size-4 shrink-0 text-slate-400" />
          <input className="min-w-0 flex-1 text-sm outline-none placeholder:text-slate-400" aria-label="Tìm người chơi" placeholder="Tên hoặc email của người xem / người được gợi ý" value={query} maxLength={100} onChange={(event) => resetPage(setQuery)(event.target.value)} />
          {query && <button type="button" aria-label="Xóa tìm kiếm" onClick={() => resetPage(setQuery)('')}><X className="size-4 text-slate-400" /></button>}
        </label>
        <select className={inputClass} aria-label="Lọc theo kết quả" data-testid="ph-outcome" value={outcome} onChange={(event) => resetPage(setOutcome)(event.target.value)}>
          <option value="all">Mọi kết quả</option>
          {Object.entries(OUTCOME_LABEL).map(([id, label]) => <option key={id} value={id}>{label}</option>)}
        </select>
      </div>

      {status === 'loading' && <OwnerLoading rows={3} />}
      {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
      {status === 'empty' && <OwnerEmpty title="Không có gợi ý nào" description="Chưa có lượt gợi ý đối tác nào khớp bộ lọc trong khoảng này." />}
      {status === 'success' && (
        <>
          <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
            <table className="w-full min-w-[760px] text-left text-sm" data-testid="ph-table">
              <thead className="bg-slate-50 text-xs font-semibold uppercase tracking-wide text-slate-500">
                <tr><th className="px-4 py-3">Thời gian</th><th className="px-4 py-3">Người xem</th><th className="px-4 py-3">Được gợi ý</th><th className="px-4 py-3 text-right">Hạng</th><th className="px-4 py-3 text-right">Điểm</th><th className="px-4 py-3">Kết quả</th></tr>
              </thead>
              <tbody className="divide-y divide-slate-100 font-medium text-slate-700">
                {data.items.map((item) => (
                  <tr key={item.id} className="cursor-pointer hover:bg-slate-50" data-testid={`ph-row-${item.id}`} data-outcome={item.outcome} onClick={() => setSelectedId(item.id)}>
                    <td className="whitespace-nowrap px-4 py-3">{formatDateTime(item.createdAt)}</td>
                    <td className="px-4 py-3"><p className="font-semibold text-slate-900">{item.viewer.fullName}</p><p className="text-xs text-slate-500">{item.viewer.email}</p></td>
                    <td className="px-4 py-3"><p className="font-semibold text-slate-900">{item.suggested.fullName}</p><p className="text-xs text-slate-500">{item.suggested.email}</p></td>
                    <td className="px-4 py-3 text-right">{item.rankNo ?? '—'}</td>
                    <td className="px-4 py-3 text-right font-semibold text-slate-900">{item.totalScore.toLocaleString('vi-VN', { maximumFractionDigits: 1 })}</td>
                    <td className="px-4 py-3"><OutcomeBadge outcome={item.outcome} /> {item.scoringSource === 'ai' && <span className="ml-1 text-xs text-slate-500">{SOURCE_LABEL.ai}</span>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="flex items-center justify-between gap-3">
            <button type="button" className={secondaryButtonClass} disabled={page === 0} onClick={() => setPage(page - 1)}>Trang trước</button>
            <p className="text-sm font-semibold text-slate-600" data-testid="ph-pager">Trang {data.page + 1}/{data.totalPages} · {data.totalElements} gợi ý</p>
            <button type="button" className={secondaryButtonClass} disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Trang sau</button>
          </div>
        </>
      )}
      {selectedId && <PartnerHistoryDrawer suggestionId={selectedId} onClose={() => setSelectedId(null)} />}
    </section>
  )
}
