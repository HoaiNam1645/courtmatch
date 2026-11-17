import { useState } from 'react'
import { Search, X } from 'lucide-react'
import { fetchTeamHistory, PLAYED_LABEL, SOURCE_LABEL, TEAM_TYPE_LABEL } from '../../../../api/adminMatching'
import { useApiResource } from '../../../../hooks/useApiResource'
import { useDebounced } from '../../../../hooks/useDebounced'
import { OwnerEmpty, OwnerError, OwnerLoading } from '../../../owner/components/OwnerUi'
import { formatDateTime } from '../../../owner/ownerDates'
import { inputClass, secondaryButtonClass } from '../../../owner/ownerStyles'
import { ReconciliationBadge } from './matchingUi'
import { TeamHistoryDrawer } from './TeamHistoryDrawer'

/** 2.3.76 - lịch sử các lần chia đội của người chơi. */
export function TeamHistorySection({ range, source }) {
  const [query, setQuery] = useState('')
  const [type, setType] = useState('all')
  const [played, setPlayed] = useState('all')
  const [page, setPage] = useState(0)
  const [selectedId, setSelectedId] = useState(null)
  const q = useDebounced(query)
  const { status, data, error, reload } = useApiResource(
    () => fetchTeamHistory({ ...range, source, q, type, played, page }),
    [range.from, range.to, source, q, type, played, page],
    { isEmpty: (result) => result.items.length === 0 },
  )
  const resetPage = (setter) => (value) => { setter(value); setPage(0) }

  return (
    <section className="grid min-w-0 grid-cols-1 gap-4" aria-label="Lịch sử chia đội" data-testid="team-history">
      <div className="grid gap-3 sm:grid-cols-4">
        <label className="flex h-11 items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 focus-within:border-slate-700 sm:col-span-2">
          <Search className="size-4 shrink-0 text-slate-400" />
          <input className="min-w-0 flex-1 text-sm outline-none placeholder:text-slate-400" aria-label="Tìm người chơi" placeholder="Tên hoặc email người yêu cầu, tên người trong đội" value={query} maxLength={100} onChange={(event) => resetPage(setQuery)(event.target.value)} />
          {query && <button type="button" aria-label="Xóa tìm kiếm" onClick={() => resetPage(setQuery)('')}><X className="size-4 text-slate-400" /></button>}
        </label>
        <select className={inputClass} aria-label="Lọc theo loại đôi" data-testid="th-type" value={type} onChange={(event) => resetPage(setType)(event.target.value)}>
          <option value="all">Mọi loại đôi</option>
          {Object.entries(TEAM_TYPE_LABEL).map(([id, label]) => <option key={id} value={id}>{label}</option>)}
        </select>
        <select className={inputClass} aria-label="Lọc theo đối chiếu" data-testid="th-played" value={played} onChange={(event) => resetPage(setPlayed)(event.target.value)}>
          {Object.entries(PLAYED_LABEL).map(([id, label]) => <option key={id} value={id}>{label}</option>)}
        </select>
      </div>

      {status === 'loading' && <OwnerLoading rows={3} />}
      {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
      {status === 'empty' && <OwnerEmpty title="Không có lần chia đội nào" description="Chưa có lượt chia đội nào khớp bộ lọc trong khoảng này." />}
      {status === 'success' && (
        <>
          <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
            <table className="w-full min-w-[820px] text-left text-sm" data-testid="th-table">
              <thead className="bg-slate-50 text-xs font-semibold uppercase tracking-wide text-slate-500">
                <tr><th className="px-4 py-3">Thời gian</th><th className="px-4 py-3">Người yêu cầu</th><th className="px-4 py-3">Loại đôi</th><th className="px-4 py-3">Bốn người</th><th className="px-4 py-3">Khuyến nghị</th><th className="px-4 py-3">Đối chiếu</th></tr>
              </thead>
              <tbody className="divide-y divide-slate-100 font-medium text-slate-700">
                {data.items.map((item) => (
                  <tr key={item.batchId} className="cursor-pointer hover:bg-slate-50" data-testid={`th-row-${item.batchId}`} data-state={item.reconciliation.status} onClick={() => setSelectedId(item.batchId)}>
                    <td className="whitespace-nowrap px-4 py-3">{formatDateTime(item.createdAt)}</td>
                    <td className="px-4 py-3"><p className="font-semibold text-slate-900">{item.requester.fullName}</p><p className="text-xs text-slate-500">{item.requester.email}</p></td>
                    <td className="whitespace-nowrap px-4 py-3">{TEAM_TYPE_LABEL[item.matchType] ?? item.matchType}</td>
                    <td className="px-4 py-3 text-xs">{item.players.map((player) => player.fullName).join(', ')}</td>
                    <td className="px-4 py-3"><p className="font-semibold text-slate-900">{item.recommended.balanceLabel}</p><p className="text-xs text-slate-500">chênh {item.recommended.balanceScore} · {item.optionCount} phương án{item.scoringSource === 'ai' ? ` · ${SOURCE_LABEL.ai}` : ''}</p></td>
                    <td className="px-4 py-3"><ReconciliationBadge reconciliation={item.reconciliation} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="flex items-center justify-between gap-3">
            <button type="button" className={secondaryButtonClass} disabled={page === 0} onClick={() => setPage(page - 1)}>Trang trước</button>
            <p className="text-sm font-semibold text-slate-600" data-testid="th-pager">Trang {data.page + 1}/{data.totalPages} · {data.totalElements} lần chia</p>
            <button type="button" className={secondaryButtonClass} disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Trang sau</button>
          </div>
        </>
      )}
      {selectedId && <TeamHistoryDrawer batchId={selectedId} onClose={() => setSelectedId(null)} />}
    </section>
  )
}
