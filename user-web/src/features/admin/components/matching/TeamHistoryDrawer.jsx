import { useEffect } from 'react'
import { X } from 'lucide-react'
import { fetchTeamHistoryDetail, SOURCE_LABEL, TEAM_TYPE_LABEL } from '../../../../api/adminMatching'
import { useApiResource } from '../../../../hooks/useApiResource'
import { classNames } from '../../../../shared/utils'
import { OwnerError, OwnerLoading } from '../../../owner/components/OwnerUi'
import { formatDateTime } from '../../../owner/ownerDates'
import { ReconciliationBadge } from './matchingUi'

const num = (value) => (value === null || value === undefined ? '—' : value.toLocaleString('vi-VN', { maximumFractionDigits: 1 }))

/** Chi tiết một lần chia đội: đủ các phương án, sức mạnh từng người và tương thích từng cặp (2.3.76). */
export function TeamHistoryDrawer({ batchId, onClose }) {
  const { status, data, error, reload } = useApiResource(() => fetchTeamHistoryDetail(batchId), [batchId])
  useEffect(() => {
    function onKey(event) {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const nameOf = (id) => data?.players.find((player) => player.id === id)?.fullName ?? 'Người chơi'

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/40" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
      <aside role="dialog" aria-modal="true" aria-label="Chi tiết lần chia đội" className="h-full w-full max-w-lg overflow-y-auto bg-white p-5 shadow-2xl" data-testid="th-drawer">
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-lg font-semibold text-slate-900">Chi tiết lần chia đội</h2>
          <button type="button" className="grid size-9 place-items-center rounded-lg text-slate-500 hover:bg-slate-100" aria-label="Đóng" onClick={onClose}><X className="size-5" /></button>
        </div>
        {status === 'loading' && <div className="mt-4"><OwnerLoading rows={2} /></div>}
        {status === 'error' && <div className="mt-4"><OwnerError message={error?.message} onRetry={reload} /></div>}
        {status === 'success' && (
          <div className="mt-4 grid gap-5 text-sm font-medium text-slate-700">
            <section>
              <p><span className="text-slate-500">Người yêu cầu:</span> <span className="font-semibold text-slate-900">{data.item.requester.fullName}</span></p>
              <p className="mt-1 text-xs text-slate-500">{TEAM_TYPE_LABEL[data.item.matchType]} · {formatDateTime(data.item.createdAt)} · {SOURCE_LABEL[data.item.scoringSource] ?? data.item.scoringSource}{data.item.modelVersion ? ` · ${data.item.modelVersion}` : ''}</p>
              <p className="mt-2" data-testid="th-reconciliation"><ReconciliationBadge reconciliation={data.item.reconciliation} /></p>
            </section>

            <section>
              <h3 className="text-sm font-semibold text-slate-900">Sức mạnh từng người</h3>
              <ul className="mt-2 grid gap-1" data-testid="th-players">
                {data.players.map((player) => (
                  <li key={player.id} className="flex justify-between border-b border-slate-100 py-1" data-testid={`th-player-${player.id}`}>
                    <span>{player.fullName}{player.provisional ? ' (tạm tính)' : ''}</span>
                    <span className="font-semibold text-slate-900">{num(player.strength)}</span>
                  </li>
                ))}
              </ul>
            </section>

            <section>
              <h3 className="text-sm font-semibold text-slate-900">Các phương án</h3>
              <ol className="mt-2 grid gap-3" data-testid="th-options">
                {data.options.map((option) => (
                  <li key={option.rank} className={classNames('rounded-lg border p-3', option.recommended ? 'border-emerald-300 bg-emerald-50/50' : 'border-slate-200')} data-testid={`th-option-${option.rank}`}>
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-semibold text-slate-900">Phương án {option.rank}{option.recommended ? ' · khuyến nghị' : ''}</span>
                      <span className="text-xs font-semibold text-slate-600">{option.balanceLabel} · chênh {num(option.balanceScore)}</span>
                    </div>
                    <p className="mt-2">Đội 1: {option.team1.playerIds.map(nameOf).join(' + ')} <span className="text-slate-500">({num(option.team1.strength)})</span></p>
                    <p>Đội 2: {option.team2.playerIds.map(nameOf).join(' + ')} <span className="text-slate-500">({num(option.team2.strength)})</span></p>
                    <p className="mt-1 text-xs text-slate-500">Đội 1 thắng {num(option.team1WinProbability)}% · {option.reason}</p>
                  </li>
                ))}
              </ol>
            </section>

            <section>
              <h3 className="text-sm font-semibold text-slate-900">Tương thích từng cặp</h3>
              <ul className="mt-2 grid gap-1" data-testid="th-pairs">
                {data.pairs.map((pair) => (
                  <li key={pair.playerIds.join('-')} className="flex justify-between border-b border-slate-100 py-1">
                    <span>{pair.playerIds.map(nameOf).join(' + ')}</span>
                    <span className="font-semibold text-slate-900">{num(pair.totalScore)}</span>
                  </li>
                ))}
              </ul>
            </section>
          </div>
        )}
      </aside>
    </div>
  )
}
