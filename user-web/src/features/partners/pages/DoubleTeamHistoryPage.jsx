import { ArrowLeft, History, LoaderCircle, RefreshCw, Swords } from 'lucide-react'
import { useState } from 'react'
import { AppHeader, BottomNav } from '../../../app/AppLayout'
import { fetchDoubleTeamHistory } from '../../../api/doubleTeams'
import { useApiResource } from '../../../hooks/useApiResource'
import { classNames } from '../../../shared/utils'
import { formatDateTime } from '../../owner/ownerDates'
import { matchTypeLabels } from '../doubleTeamUi'

const labelStyles = {
  'Rất cân bằng': 'bg-emerald-100 text-emerald-800',
  'Cân bằng': 'bg-lime-100 text-lime-800',
  'Chênh lệch': 'bg-amber-100 text-amber-800',
}

/** 2.3.76: lịch sử các lần chia đội của chính người chơi. */
function DoubleTeamHistoryPage({ user, onLogout }) {
  const [page, setPage] = useState(0)
  const { status, data, error, reload } = useApiResource(
    () => fetchDoubleTeamHistory({ page }),
    [page],
    { isEmpty: (result) => result.items.length === 0 },
  )

  return (
    <main className="min-h-screen bg-[radial-gradient(circle_at_88%_4%,rgba(190,242,100,0.24),transparent_24%),linear-gradient(145deg,#edf8f2,#fbfcf8)] pb-28 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="bg-emerald-950 px-4 pb-16 pt-9 text-white md:px-6">
        <div className="mx-auto max-w-[1080px]">
          <a href="/partners/double-teams" className="inline-flex items-center gap-2 text-xs font-semibold text-emerald-100/70"><ArrowLeft className="size-4" />Về chia đội</a>
          <p className="mt-7 text-xs font-bold uppercase tracking-[0.16em] text-lime-300">2.3.76 · Lịch sử chia đội</p>
          <h1 className="mt-3 text-3xl font-semibold tracking-tight sm:text-4xl">Các lần chia đội của bạn</h1>
          <p className="mt-3 max-w-xl text-sm leading-6 text-emerald-100/70">Xem lại đề xuất đã tạo, mới nhất ở trên cùng.</p>
        </div>
      </section>

      <section className="mx-auto -mt-8 max-w-[1080px] px-4 md:px-6">
        {status === 'loading' && <div className="grid min-h-40 place-items-center rounded-2xl border border-emerald-100 bg-white"><LoaderCircle className="size-8 animate-spin text-emerald-600" /></div>}
        {status === 'error' && (
          <div className="rounded-2xl border border-red-100 bg-white p-6 text-center">
            <p className="text-sm font-semibold text-red-700">{error?.message ?? 'Không tải được lịch sử chia đội.'}</p>
            <button type="button" onClick={reload} className="mt-4 inline-flex h-10 items-center gap-2 rounded-xl bg-emerald-700 px-4 text-xs font-semibold text-white"><RefreshCw className="size-4" />Thử lại</button>
          </div>
        )}
        {status === 'empty' && (
          <div className="grid min-h-40 place-items-center rounded-2xl border border-dashed border-emerald-200 bg-white/75 p-6 text-center" data-testid="dth-empty">
            <div><History className="mx-auto size-9 text-emerald-300" /><p className="mt-3 text-sm font-semibold">Bạn chưa chia đội lần nào</p><a href="/partners/double-teams" className="mt-3 inline-flex items-center gap-1 text-xs font-semibold text-emerald-700 underline"><Swords className="size-4" />Chia đội ngay</a></div>
          </div>
        )}
        {status === 'success' && (
          <>
            <ul className="grid gap-3" data-testid="dth-list">
              {data.items.map((item) => (
                <li key={item.batchId}>
                  <a href={`/partners/double-teams/${item.batchId}`} data-testid={`dth-item-${item.batchId}`} className="block rounded-2xl border border-emerald-100 bg-white p-4 shadow-sm transition hover:border-emerald-300">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <span className="text-xs font-bold uppercase tracking-wide text-emerald-700">{matchTypeLabels[item.matchType] ?? item.matchType}</span>
                      <span className="text-xs font-medium text-stone-500">{formatDateTime(item.createdAt)}</span>
                    </div>
                    <p className="mt-2 text-sm font-semibold" data-testid="dth-players">{item.players.map((player) => player.fullName).join(' · ')}</p>
                    <div className="mt-3 flex flex-wrap items-center gap-2 text-xs font-semibold">
                      <span className={classNames('rounded-full px-2.5 py-1', labelStyles[item.balanceLabel] ?? 'bg-stone-100 text-stone-700')}>{item.balanceLabel}</span>
                      <span className="text-stone-500">Chênh {item.balanceScore} điểm · đội 1 thắng {item.team1WinProbability}%</span>
                    </div>
                  </a>
                </li>
              ))}
            </ul>
            <div className="mt-5 flex items-center justify-between gap-3">
              <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)} className="h-10 rounded-xl border border-emerald-200 bg-white px-4 text-xs font-semibold text-emerald-800 disabled:opacity-50">Trang trước</button>
              <p className="text-xs font-semibold text-stone-600" data-testid="dth-pager">Trang {data.page + 1}/{data.totalPages} · {data.totalElements} lần chia</p>
              <button type="button" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)} className="h-10 rounded-xl border border-emerald-200 bg-white px-4 text-xs font-semibold text-emerald-800 disabled:opacity-50">Trang sau</button>
            </div>
          </>
        )}
      </section>
      <BottomNav activeLabel="Đối tác" />
    </main>
  )
}

export default DoubleTeamHistoryPage
