import { ArrowLeft, LoaderCircle, RefreshCw, Trophy } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { AppHeader, BottomNav } from '../../../app/AppLayout'
import { getDoubleTeamSuggestion } from '../../../api/doubleTeams'
import { classNames } from '../../../shared/utils'
import { formatPoints, genderLabels, matchTypeLabels } from '../doubleTeamUi'

const labelClass = {
  'Rất cân bằng': 'bg-emerald-100 text-emerald-800',
  'Cân bằng': 'bg-lime-100 text-lime-800',
  'Chênh lệch': 'bg-amber-100 text-amber-800',
}

function TeamColumn({ title, team, nameById, winPercent }) {
  return (
    <div className="min-w-0 flex-1 rounded-xl bg-[#f5f8f2] p-4">
      <p className="text-xs font-bold uppercase tracking-[0.1em] text-emerald-700">{title}</p>
      <p className="mt-2 font-semibold">{team.playerIds.map((id) => nameById[id]).join(' & ')}</p>
      <p className="mt-3 text-2xl font-semibold" data-testid="team-strength">{formatPoints(team.strength)}<span className="ml-1 text-xs font-medium text-stone-500">điểm sức mạnh</span></p>
      <p className="mt-1 text-xs text-stone-500">Tương thích cặp {formatPoints(team.compatibility)}/100 · Khả năng thắng {formatPoints(winPercent)}%</p>
      <div className="mt-3 h-2 overflow-hidden rounded-full bg-white"><div className="h-full rounded-full bg-emerald-600" style={{ width: `${Math.max(0, Math.min(100, winPercent))}%` }} /></div>
    </div>
  )
}

function OptionCard({ option, nameById }) {
  return (
    <article data-testid={`dt-option-${option.rank}`} className={classNames('rounded-2xl border bg-white p-5 shadow-[0_10px_30px_rgba(30,70,48,0.06)]', option.recommended ? 'border-emerald-500 ring-2 ring-emerald-100' : 'border-emerald-100')}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm font-semibold">Phương án {option.rank}{option.recommended && <span data-testid="dt-recommended" className="ml-2 inline-flex items-center gap-1 rounded-full bg-emerald-700 px-2.5 py-0.5 text-[11px] font-bold text-white"><Trophy className="size-3" />Đề xuất tối ưu</span>}</p>
        <span data-testid="dt-balance-label" className={classNames('rounded-full px-3 py-1 text-[11px] font-bold', labelClass[option.balanceLabel] ?? 'bg-stone-100 text-stone-600')}>{option.balanceLabel} · lệch {formatPoints(option.balanceScore)} điểm</span>
      </div>
      <div className="mt-4 flex flex-col gap-3 sm:flex-row">
        <TeamColumn title="Đội 1" team={option.team1} nameById={nameById} winPercent={option.team1WinProbability} />
        <TeamColumn title="Đội 2" team={option.team2} nameById={nameById} winPercent={100 - option.team1WinProbability} />
      </div>
      <p className="mt-4 text-sm leading-6 text-stone-600">{option.reason}</p>
    </article>
  )
}

/** 2.3.64-2.3.69: sức mạnh từng người, tương thích từng cặp và các phương án chia đội. */
function DoubleTeamResultPage({ batchId, user, onLogout }) {
  const [state, setState] = useState({ status: 'loading', data: null, error: '' })
  const load = useCallback(async () => {
    setState({ status: 'loading', data: null, error: '' })
    try {
      setState({ status: 'success', data: await getDoubleTeamSuggestion(batchId), error: '' })
    } catch (error) {
      setState({ status: 'error', data: null, error: error?.status === 404 ? 'Không tìm thấy đề xuất chia đội này.' : (error?.message ?? 'Không tải được đề xuất chia đội.') })
    }
  }, [batchId])
  useEffect(() => {
    // Effect đồng bộ màn hình với đề xuất lưu ở backend.
    // oxlint-disable-next-line react/set-state-in-effect
    load()
  }, [load])

  const data = state.data
  const nameById = data ? Object.fromEntries(data.players.map((player) => [player.id, player.fullName])) : {}

  return (
    <main className="min-h-screen bg-[radial-gradient(circle_at_88%_4%,rgba(190,242,100,0.24),transparent_24%),linear-gradient(145deg,#edf8f2,#fbfcf8)] pb-24 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="bg-emerald-950 px-4 pb-16 pt-9 text-white md:px-6">
        <div className="mx-auto max-w-[1080px]">
          <a href="/partners/double-teams" className="inline-flex items-center gap-2 text-xs font-semibold text-emerald-100/70"><ArrowLeft className="size-4" />Chọn lại người chơi</a>
          <p className="mt-7 text-xs font-bold uppercase tracking-[0.16em] text-lime-300">2.3.69 · Đề xuất đội đôi tối ưu</p>
          <h1 className="mt-3 text-3xl font-semibold tracking-tight sm:text-4xl">{data ? matchTypeLabels[data.matchType] : 'Kết quả chia đội'}</h1>
          {data && <p className="mt-3 text-sm text-emerald-100/70" data-testid="dt-source">Tính bằng công thức cơ sở (baseline) · {data.modelVersion}</p>}
        </div>
      </section>

      <section className="mx-auto -mt-8 max-w-[1080px] px-4 md:px-6">
        {state.status === 'loading' && <div className="grid min-h-56 place-items-center rounded-2xl border border-emerald-100 bg-white"><LoaderCircle className="size-8 animate-spin text-emerald-600" /></div>}
        {state.status === 'error' && (
          <div className="rounded-2xl border border-red-100 bg-white p-6 text-center">
            <p className="text-sm font-semibold text-red-700">{state.error}</p>
            <button type="button" onClick={load} className="mt-4 inline-flex h-10 items-center gap-2 rounded-xl bg-emerald-700 px-4 text-xs font-semibold text-white"><RefreshCw className="size-4" />Thử lại</button>
          </div>
        )}
        {data && (
          <div className="grid gap-6">
            <div className="grid gap-4" data-testid="dt-options">{data.options.map((option) => <OptionCard key={option.rank} option={option} nameById={nameById} />)}</div>

            <div className="rounded-2xl border border-emerald-100 bg-white p-5">
              <h2 className="text-lg font-semibold">Sức mạnh từng người</h2>
              <p className="mt-1 text-xs text-stone-500">Điểm sức mạnh lấy từ Elo đo được; người dưới 5 trận được kéo về mức khởi điểm 1000 nên chỉ mang tính tạm tính.</p>
              <ul className="mt-4 divide-y divide-emerald-50" data-testid="dt-strengths">
                {data.players.map((player) => (
                  <li key={player.id} data-testid={`dt-strength-${player.id}`} className="flex flex-wrap items-center justify-between gap-2 py-3">
                    <div className="min-w-0">
                      <p className="font-semibold">{player.fullName}<span className="ml-2 text-xs font-medium text-stone-400">{genderLabels[player.gender] ?? ''}</span></p>
                      <p className="mt-0.5 text-xs text-stone-500">Elo {formatPoints(player.ratingScore)} · tỷ lệ thắng {formatPoints(player.winRate)}% ({player.totalMatches} trận) · độ tin cậy {Math.round(player.confidence * 100)}%</p>
                    </div>
                    <div className="text-right">
                      <p className="text-xl font-semibold">{formatPoints(player.strength)}</p>
                      {player.provisional && <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[11px] font-bold text-amber-800">Tạm tính</span>}
                    </div>
                  </li>
                ))}
              </ul>
            </div>

            <div className="rounded-2xl border border-emerald-100 bg-white p-5">
              <h2 className="text-lg font-semibold">Tương thích từng cặp</h2>
              <p className="mt-1 text-xs text-stone-500">Kỹ năng, phong cách, lịch rảnh và vị trí; mục chưa có dữ liệu được bỏ qua khi tính điểm tổng.</p>
              <ul className="mt-4 grid gap-3 sm:grid-cols-2" data-testid="dt-pairs">
                {data.pairs.map((pair) => (
                  <li key={pair.playerIds.join('-')} className="rounded-xl bg-[#f5f8f2] p-4">
                    <p className="text-sm font-semibold">{pair.playerIds.map((id) => nameById[id]).join(' & ')}</p>
                    <p className="mt-1 text-2xl font-semibold">{formatPoints(pair.totalScore)}<span className="ml-1 text-xs font-medium text-stone-500">/100</span></p>
                    <p className="mt-1 text-xs text-stone-500">Kỹ năng {formatPoints(pair.skillScore)} · Phong cách {formatPoints(pair.styleScore)} · Lịch rảnh {formatPoints(pair.timeScore)} · Vị trí {formatPoints(pair.locationScore)}</p>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        )}
      </section>
      <BottomNav activeLabel="Đối tác" />
    </main>
  )
}

export default DoubleTeamResultPage
