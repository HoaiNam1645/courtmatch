import { useState } from 'react'
import { ArrowUpRight, BarChart3, Bell, ChevronRight, CircleAlert, LoaderCircle, Plus, RotateCcw, Swords, TrendingUp, Trophy } from 'lucide-react'
import { AppHeader, BottomNav } from '../../../app/AppLayout'
import { sprint2Matches, sprint2Performance } from '../../../data/sprint2Preview'
import { useMatchSuggestions } from '../../../hooks/useMatchSuggestions'
import { PageBackLink, ProfileEmptyState } from '../../profile/components/ProfileUi'
import { classNames } from '../../../shared/utils'

const matchStatusMeta = {
  scheduled: { label: 'Chờ xác nhận', className: 'bg-amber-100 text-amber-800' },
  completed: { label: 'Đã xác nhận', className: 'bg-emerald-100 text-emerald-700' },
  disputed: { label: 'Đang tranh chấp', className: 'bg-red-100 text-red-700' },
  cancelled: { label: 'Đã huỷ', className: 'bg-stone-200 text-stone-600' },
}

const matchTypeLabels = {
  single: 'Đánh đơn',
  double: 'Đánh đôi',
  mixed_double: 'Đôi nam nữ',
}

function SprintTwoPreviewBanner() {
  return (
    <div className="flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-amber-900">
      <span className="mt-0.5 grid size-8 shrink-0 place-items-center rounded-lg bg-amber-100"><CircleAlert className="size-4" /></span>
      <div><p className="text-xs font-bold uppercase tracking-[0.14em]">Phạm vi đang triển khai</p><p className="mt-1 text-xs font-medium leading-5 text-amber-800">Gợi ý booking và luồng ghi/xác nhận kết quả đã dùng API thật. Lịch sử và dashboard bên dưới thuộc 2.2.9–2.2.10 nên vẫn là bản xem trước.</p></div>
    </div>
  )
}

function MatchStatusBadge({ status }) {
  const meta = matchStatusMeta[status] ?? matchStatusMeta.scheduled
  return <span className={classNames('inline-flex rounded-full px-3 py-1 text-[11px] font-bold', meta.className)}>{meta.label}</span>
}

function MatchScore({ match, compact = false }) {
  const team1Sets = match.games.filter(([a, b]) => a > b).length
  const team2Sets = match.games.filter(([a, b]) => b > a).length
  return (
    <div className={classNames('grid items-center gap-3', compact ? 'grid-cols-[1fr_auto_1fr]' : 'grid-cols-[minmax(0,1fr)_90px_minmax(0,1fr)]')}>
      <div className="min-w-0 text-right"><p className="truncate text-sm font-semibold text-emerald-950">{match.team1.map((player) => player.name).join(' · ')}</p><p className="mt-1 text-[11px] font-medium text-stone-400">Đội 1</p></div>
      <div className="grid grid-cols-3 items-center rounded-lg bg-emerald-950 px-3 py-2 text-center text-white shadow-sm"><span className="text-xl font-semibold">{team1Sets}</span><span className="text-xs font-bold text-lime-300">—</span><span className="text-xl font-semibold">{team2Sets}</span></div>
      <div className="min-w-0"><p className="truncate text-sm font-semibold text-emerald-950">{match.team2.map((player) => player.name).join(' · ')}</p><p className="mt-1 text-[11px] font-medium text-stone-400">Đội 2</p></div>
    </div>
  )
}

function MatchCard({ match }) {
  return (
    <a href={`/matches/${match.id}`} className="group block rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)] transition hover:-translate-y-0.5 hover:border-emerald-200 hover:shadow-[0_16px_36px_rgba(24,65,44,0.10)]">
      <div className="flex flex-wrap items-center justify-between gap-3"><div className="flex items-center gap-2"><MatchStatusBadge status={match.status} /><span className="text-[11px] font-bold uppercase tracking-[0.12em] text-stone-400">{matchTypeLabels[match.matchType]}</span></div><ArrowUpRight className="size-4 text-emerald-600 transition group-hover:translate-x-0.5 group-hover:-translate-y-0.5" /></div>
      <div className="mt-5"><MatchScore match={match} compact /></div>
      <div className="mt-5 flex flex-wrap items-center justify-between gap-2 border-t border-emerald-50 pt-4 text-xs font-medium text-stone-500"><span>{match.venueName}</span><span>{new Date(match.scheduledAt).toLocaleDateString('vi-VN')}</span></div>
    </a>
  )
}

function MatchesPage({ user, onLogout }) {
  const [tab, setTab] = useState('all')
  const suggestions = useMatchSuggestions()
  if (!user) return <ProfileEmptyState />

  const visibleMatches = sprint2Matches.filter((match) => {
    if (tab === 'all') return true
    if (tab === 'pending') return match.status === 'scheduled' || match.status === 'disputed'
    return match.status === tab
  })
  const pendingCount = sprint2Matches.filter((match) => match.status === 'scheduled' || match.status === 'disputed').length

  return (
    <main className="min-h-screen bg-[radial-gradient(circle_at_88%_4%,rgba(190,242,100,0.22),transparent_24%),linear-gradient(145deg,#edf8f2,#fafbf7)] pb-24 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="overflow-hidden bg-emerald-950 px-4 pb-16 pt-8 text-white md:px-6">
        <div className="mx-auto max-w-[1200px]"><PageBackLink href="/profile" label="Về hồ sơ" tone="dark" /><div className="mt-8 grid items-end gap-7 lg:grid-cols-[1fr_auto]"><div><p className="text-xs font-bold uppercase tracking-[0.18em] text-lime-300">Dữ liệu thi đấu · Sprint 2</p><h1 className="mt-3 max-w-3xl text-4xl font-semibold tracking-tight md:text-5xl">Mỗi trận đấu là một bước tiến bộ.</h1><p className="mt-4 max-w-2xl text-sm font-medium leading-7 text-emerald-100/75">Ghi kết quả, xác nhận cùng đối thủ và theo dõi lịch sử thi đấu của bạn ở một nơi.</p></div><a href="/matches/new" className="flex h-12 items-center justify-center gap-2 rounded-xl bg-lime-300 px-6 text-sm font-semibold text-emerald-950 shadow-[0_14px_30px_rgba(163,230,53,0.2)]"><Plus className="size-4" />Ghi kết quả trận đấu</a></div></div>
      </section>

      <section className="mx-auto -mt-8 max-w-[1200px] px-4 md:px-6">
        <div className="grid overflow-hidden rounded-xl border border-emerald-100 bg-white shadow-[0_18px_44px_rgba(24,65,44,0.1)] sm:grid-cols-3">
          {[[sprint2Performance.summary.totalMatches, 'Trận đã xác nhận', Trophy], [`${sprint2Performance.summary.winRate}%`, `${sprint2Performance.summary.totalWins}/${sprint2Performance.summary.totalMatches} trận thắng`, TrendingUp], [pendingCount, 'Cần bạn xử lý', Bell]].map(([value, label, Icon], index) => <div key={label} className={classNames('flex items-center gap-4 p-5', index > 0 && 'border-t border-emerald-100 sm:border-l sm:border-t-0')}><span className="grid size-11 place-items-center rounded-lg bg-emerald-50 text-emerald-700"><Icon className="size-5" /></span><div><p className="text-2xl font-semibold">{value}</p><p className="mt-1 text-xs font-medium text-stone-500">{label}</p></div></div>)}
        </div>

        <div className="mt-6"><SprintTwoPreviewBanner /></div>

        <section className="mt-8">
          <div className="flex flex-wrap items-end justify-between gap-4"><div><p className="text-xs font-bold uppercase tracking-[0.15em] text-emerald-600">2.2.1 · Gợi ý từ lịch đặt sân</p><h2 className="mt-2 text-2xl font-semibold">Bạn vừa chơi trận nào?</h2></div><span className="text-xs font-semibold text-stone-400">Chỉ hiện booking trong 30 ngày gần nhất</span></div>
          {suggestions.status === 'loading' && <div className="mt-5 flex items-center gap-3 rounded-xl border border-emerald-100 bg-white p-5 text-sm text-stone-500"><LoaderCircle className="size-5 animate-spin text-emerald-600" />Đang tải booking đã hoàn tất...</div>}
          {suggestions.status === 'empty' && <div className="mt-5 rounded-xl border border-emerald-100 bg-white p-5 text-sm text-stone-500">Bạn không có booking hoàn tất nào cần ghi kết quả trong 30 ngày gần đây.</div>}
          {suggestions.status === 'error' && <button onClick={suggestions.reload} className="mt-5 flex w-full items-center justify-center gap-2 rounded-xl border border-red-100 bg-red-50 p-5 text-sm font-semibold text-red-700"><RotateCcw className="size-4" />{suggestions.error}</button>}
          {suggestions.status === 'success' && <div className="mt-5 grid gap-4 lg:grid-cols-2">{suggestions.suggestions.map((suggestion) => <article key={suggestion.bookingId} className="relative overflow-hidden rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)]"><div className="absolute right-0 top-0 h-24 w-24 rounded-bl-full bg-lime-100/70" /><div className="relative"><div className="flex items-start gap-4"><span className="grid size-12 shrink-0 place-items-center rounded-xl bg-emerald-900 text-lime-300"><Swords className="size-6" /></span><div><p className="text-xs font-bold text-emerald-600">{suggestion.bookingCode}</p><h3 className="mt-1 text-lg font-semibold">{suggestion.venueName}</h3><p className="mt-2 text-xs font-medium text-stone-500">{suggestion.courtName} · {new Date(suggestion.playedAt).toLocaleString('vi-VN')} · {suggestion.durationMinutes} phút</p></div></div><a href={`/matches/new?booking=${suggestion.bookingId}`} className="mt-5 flex h-11 w-full items-center justify-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 text-sm font-semibold text-emerald-800">Ghi lại kết quả <ChevronRight className="size-4" /></a></div></article>)}</div>}
        </section>

        <section className="mt-10">
          <div className="flex flex-wrap items-end justify-between gap-4"><div><p className="text-xs font-bold uppercase tracking-[0.15em] text-emerald-600">2.2.9 · Lịch sử trận đấu</p><h2 className="mt-2 text-2xl font-semibold">Hành trình trên sân</h2></div><a className="flex h-10 items-center gap-2 rounded-lg border border-emerald-200 bg-white px-4 text-xs font-semibold text-emerald-700" href="/performance"><BarChart3 className="size-4" />Xem hiệu suất</a></div>
          <div className="mt-5 flex gap-2 overflow-x-auto pb-2">{[['all', 'Tất cả'], ['pending', 'Cần xử lý'], ['completed', 'Đã xác nhận'], ['disputed', 'Tranh chấp']].map(([id, label]) => <button key={id} onClick={() => setTab(id)} className={classNames('h-10 shrink-0 rounded-full px-4 text-xs font-semibold', tab === id ? 'bg-emerald-700 text-white' : 'border border-emerald-100 bg-white text-stone-500')}>{label}</button>)}</div>
          <div className="mt-4 grid gap-4 md:grid-cols-2">{visibleMatches.map((match) => <MatchCard key={match.id} match={match} />)}</div>
        </section>
      </section>
      <BottomNav activeLabel="Tài khoản" />
    </main>
  )
}

export default MatchesPage
