import { useState } from 'react'
import { ArrowUpRight, Award, BarChart3, Bell, CalendarDays, CheckCircle2, ChevronRight, Clock3, CircleAlert, History, Flame, Plus, ShieldCheck, Swords, TrendingUp, Trophy, Users } from 'lucide-react'
import { AppHeader, BottomNav } from '../../app/AppLayout'
import { sprint2Matches, sprint2Performance, sprint2Players, sprint2Suggestions } from '../../data/sprint2Preview'
import { BookingEmptyState } from '../bookings/BookingPages'
import { PageBackLink, ProfileEmptyState, ProfileSectionTitle } from '../profile/ProfilePages'
import { classNames } from '../../shared/utils'

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
      <div><p className="text-xs font-bold uppercase tracking-[0.14em]">Bản xem trước Sprint 2</p><p className="mt-1 text-xs font-medium leading-5 text-amber-800">Giao diện đang dùng dữ liệu minh hoạ để duyệt UX. Cấu trúc đã bám theo API 2.2 và sẽ thay bằng dữ liệu thật khi backend hoàn tất.</p></div>
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
          <div className="mt-5 grid gap-4 lg:grid-cols-2">{sprint2Suggestions.map((suggestion) => <article key={suggestion.bookingId} className="relative overflow-hidden rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)]"><div className="absolute right-0 top-0 h-24 w-24 rounded-bl-full bg-lime-100/70" /><div className="relative"><div className="flex items-start gap-4"><span className="grid size-12 shrink-0 place-items-center rounded-xl bg-emerald-900 text-lime-300"><Swords className="size-6" /></span><div><p className="text-xs font-bold text-emerald-600">{suggestion.bookingCode}</p><h3 className="mt-1 text-lg font-semibold">{suggestion.venueName}</h3><p className="mt-2 text-xs font-medium text-stone-500">{suggestion.courtName} · {new Date(suggestion.playedAt).toLocaleString('vi-VN')} · {suggestion.durationMinutes} phút</p></div></div><a href={`/matches/new?booking=${suggestion.bookingId}`} className="mt-5 flex h-11 w-full items-center justify-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 text-sm font-semibold text-emerald-800">Ghi lại kết quả <ChevronRight className="size-4" /></a></div></article>)}</div>
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

function NewMatchPage({ user, onLogout }) {
  const bookingId = new URLSearchParams(window.location.search).get('booking')
  const initialSuggestion = sprint2Suggestions.find((item) => item.bookingId === bookingId) ?? sprint2Suggestions[0]
  const [source, setSource] = useState(bookingId ? 'booking' : 'manual')
  const [matchType, setMatchType] = useState('double')
  const [venueName, setVenueName] = useState(initialSuggestion.venueName)
  const [playedAt, setPlayedAt] = useState('2026-10-06T19:30')
  const [team1Partner, setTeam1Partner] = useState('player-2')
  const [team2First, setTeam2First] = useState('player-1')
  const [team2Second, setTeam2Second] = useState('player-4')
  const [games, setGames] = useState([[21, 18], [19, 21], [21, 16]])
  const [error, setError] = useState('')
  if (!user) return <ProfileEmptyState />

  const playerById = (id) => sprint2Players.find((player) => player.id === id)
  function updateScore(index, side, value) {
    setGames((current) => current.map((game, gameIndex) => gameIndex === index ? game.map((score, scoreIndex) => scoreIndex === side ? Math.max(0, Number(value)) : score) : game))
  }
  function submitMatch(event) {
    event.preventDefault()
    const participantIds = ['me', ...(matchType === 'single' ? [] : [team1Partner]), team2First, ...(matchType === 'single' ? [] : [team2Second])]
    if (new Set(participantIds).size !== participantIds.length) {
      setError('Một người chơi không thể xuất hiện ở cả hai đội.')
      return
    }
    const created = {
      id: 'preview-new', code: 'MT-PREVIEW-NEW', status: 'scheduled', matchType,
      venueName: source === 'booking' ? initialSuggestion.venueName : venueName,
      courtName: source === 'booking' ? initialSuggestion.courtName : 'Ngoài hệ thống',
      scheduledAt: source === 'booking' ? initialSuggestion.playedAt : new Date(playedAt).toISOString(),
      dataSource: source,
      team1: [{ ...playerById('me'), name: user.name, confirmed: true }, ...(matchType === 'single' ? [] : [{ ...playerById(team1Partner), confirmed: false }])],
      team2: [{ ...playerById(team2First), confirmed: false }, ...(matchType === 'single' ? [] : [{ ...playerById(team2Second), confirmed: false }])],
      games, winningTeam: games.filter(([a, b]) => a > b).length > games.filter(([a, b]) => b > a).length ? 1 : 2, ratingChange: null,
    }
    window.sessionStorage.setItem('courtly_sprint2_preview_match', JSON.stringify(created))
    window.location.assign('/matches/preview-new')
  }

  const playerOptions = sprint2Players.filter((player) => player.id !== 'me')
  return (
    <main className="min-h-screen bg-[linear-gradient(145deg,#eef8f2,#fafbf7)] pb-24 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="mx-auto max-w-[1100px] px-4 py-7 md:px-6 md:py-10"><PageBackLink href="/matches" label="Về danh sách trận" /><div className="mt-6"><p className="text-xs font-bold uppercase tracking-[0.15em] text-emerald-600">2.2.2 · Nhập dữ liệu trận đấu</p><h1 className="mt-2 text-3xl font-semibold">Ghi lại kết quả</h1><p className="mt-2 text-sm font-medium text-stone-500">Người ở đội đối phương cần xác nhận trước khi kết quả được tính vào điểm kỹ năng.</p></div><div className="mt-6"><SprintTwoPreviewBanner /></div>
        <form onSubmit={submitMatch} className="mt-6 grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_350px]">
          <div className="grid gap-6">
            <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)]"><ProfileSectionTitle icon={CalendarDays} eyebrow="Nguồn trận đấu" title="Thời gian và địa điểm" /><div className="mt-5 grid grid-cols-2 gap-3">{[['booking', 'Từ booking'], ['manual', 'Nhập thủ công']].map(([id, label]) => <button type="button" key={id} onClick={() => setSource(id)} className={classNames('h-11 rounded-lg border text-sm font-semibold', source === id ? 'border-emerald-500 bg-emerald-50 text-emerald-800 ring-1 ring-emerald-400' : 'border-stone-200 text-stone-500')}>{label}</button>)}</div>{source === 'booking' ? <div className="mt-4 rounded-xl bg-emerald-950 p-5 text-white"><p className="text-xs font-bold text-lime-300">{initialSuggestion.bookingCode}</p><h3 className="mt-2 text-lg font-semibold">{initialSuggestion.venueName}</h3><p className="mt-2 text-xs font-medium text-emerald-100/70">{initialSuggestion.courtName} · {new Date(initialSuggestion.playedAt).toLocaleString('vi-VN')}</p></div> : <div className="mt-4 grid gap-4 sm:grid-cols-2"><label className="text-sm font-semibold">Địa điểm<input required value={venueName} onChange={(event) => setVenueName(event.target.value)} className="mt-2 h-11 w-full rounded-lg border border-stone-200 px-3 text-sm font-medium outline-none focus:border-emerald-500" /></label><label className="text-sm font-semibold">Thời gian<input required type="datetime-local" value={playedAt} onChange={(event) => setPlayedAt(event.target.value)} className="mt-2 h-11 w-full rounded-lg border border-stone-200 px-3 text-sm font-medium outline-none focus:border-emerald-500" /></label></div>}</section>
            <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)]"><ProfileSectionTitle icon={Users} eyebrow="Người tham gia" title="Chia đội thi đấu" /><div className="mt-5 grid grid-cols-3 gap-2">{Object.entries(matchTypeLabels).map(([id, label]) => <button type="button" key={id} onClick={() => setMatchType(id)} className={classNames('min-h-11 rounded-lg border px-2 text-xs font-semibold', matchType === id ? 'border-emerald-500 bg-emerald-50 text-emerald-800 ring-1 ring-emerald-400' : 'border-stone-200 text-stone-500')}>{label}</button>)}</div><div className="mt-5 grid gap-4 sm:grid-cols-2"><TeamEditor title="Đội của bạn" accent="emerald"><PlayerSelect value="me" disabled options={[{ id: 'me', name: user.name, rating: 1372 }]} />{matchType !== 'single' && <PlayerSelect value={team1Partner} onChange={setTeam1Partner} options={playerOptions} />}</TeamEditor><TeamEditor title="Đội đối phương" accent="amber"><PlayerSelect value={team2First} onChange={setTeam2First} options={playerOptions} />{matchType !== 'single' && <PlayerSelect value={team2Second} onChange={setTeam2Second} options={playerOptions} />}</TeamEditor></div></section>
            <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)]"><ProfileSectionTitle icon={Trophy} eyebrow="Tỷ số" title="Kết quả từng set" /><div className="mt-5 grid gap-3">{games.map((game, index) => <div key={index} className="grid grid-cols-[70px_1fr_auto_1fr] items-center gap-3 rounded-xl bg-stone-50 p-3 ring-1 ring-stone-100"><span className="text-xs font-bold text-stone-400">SET {index + 1}</span><input aria-label={`Điểm đội 1 set ${index + 1}`} type="number" min="0" value={game[0]} onChange={(event) => updateScore(index, 0, event.target.value)} className="h-12 min-w-0 rounded-lg border border-emerald-200 bg-white text-center text-xl font-semibold outline-none focus:border-emerald-500" /><span className="text-sm font-bold text-stone-300">—</span><input aria-label={`Điểm đội 2 set ${index + 1}`} type="number" min="0" value={game[1]} onChange={(event) => updateScore(index, 1, event.target.value)} className="h-12 min-w-0 rounded-lg border border-amber-200 bg-white text-center text-xl font-semibold outline-none focus:border-amber-500" /></div>)}</div></section>
          </div>
          <aside className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_14px_38px_rgba(24,65,44,0.10)] lg:sticky lg:top-20"><ProfileSectionTitle icon={ShieldCheck} eyebrow="Xác nhận dữ liệu" title="Trước khi gửi" /><div className="mt-5 grid gap-3 text-xs font-medium leading-5 text-stone-500"><p className="rounded-lg bg-emerald-50 p-3 text-emerald-800">Bạn được tính là đã xác nhận vì là người tạo trận.</p><p>Ít nhất một người ở đội đối phương phải xác nhận kết quả.</p><p>Trận chưa xác nhận sẽ không ảnh hưởng đến điểm kỹ năng hay tỷ lệ thắng.</p></div>{error && <p className="mt-4 rounded-lg bg-red-50 p-3 text-xs font-medium text-red-700">{error}</p>}<button className="mt-5 flex h-12 w-full items-center justify-center gap-2 rounded-lg bg-lime-300 text-sm font-semibold text-emerald-950"><CheckCircle2 className="size-4" />Gửi kết quả xác nhận</button></aside>
        </form>
      </section><BottomNav activeLabel="Tài khoản" />
    </main>
  )
}

function TeamEditor({ title, accent, children }) {
  return <div className={classNames('rounded-xl border p-4', accent === 'emerald' ? 'border-emerald-100 bg-emerald-50/60' : 'border-amber-100 bg-amber-50/60')}><p className={classNames('text-xs font-bold uppercase tracking-[0.12em]', accent === 'emerald' ? 'text-emerald-700' : 'text-amber-700')}>{title}</p><div className="mt-3 grid gap-3">{children}</div></div>
}

function PlayerSelect({ value, onChange, options, disabled = false }) {
  return <select disabled={disabled} value={value} onChange={(event) => onChange?.(event.target.value)} className="h-12 w-full rounded-lg border border-white bg-white px-3 text-sm font-semibold text-emerald-950 shadow-sm outline-none disabled:bg-white disabled:opacity-100">{options.map((player) => <option key={player.id} value={player.id}>{player.name} · {player.rating}</option>)}</select>
}

function MatchDetailPage({ matchId, user, onLogout }) {
  let previewMatch = null
  try { previewMatch = JSON.parse(window.sessionStorage.getItem('courtly_sprint2_preview_match')) } catch { previewMatch = null }
  const sourceMatch = (previewMatch?.id === matchId ? previewMatch : null) ?? sprint2Matches.find((item) => item.id === matchId)
  const [status, setStatus] = useState(sourceMatch?.status)
  const [notice, setNotice] = useState('')
  if (!user) return <ProfileEmptyState />
  if (!sourceMatch) return <main className="min-h-screen bg-[#f4f7f1] pb-24"><AppHeader user={user} onLogout={onLogout} /><section className="mx-auto max-w-[900px] px-4 py-12"><BookingEmptyState title="Không tìm thấy trận đấu" description="Trận đấu có thể không tồn tại hoặc không thuộc về bạn." actionLabel="Về lịch sử trận" actionHref="/matches" /></section></main>
  const match = { ...sourceMatch, status }
  const canRespond = status === 'scheduled' && match.team2.some((player) => player.id === 'me' && !player.confirmed)

  return (
    <main className="min-h-screen bg-[linear-gradient(145deg,#edf8f2,#fafbf7)] pb-24 text-emerald-950"><AppHeader user={user} onLogout={onLogout} /><section className="mx-auto max-w-[1050px] px-4 py-7 md:px-6 md:py-10"><PageBackLink href="/matches" label="Về lịch sử trận" /><div className="mt-6 flex flex-wrap items-start justify-between gap-4"><div><p className="text-xs font-bold uppercase tracking-[0.15em] text-emerald-600">2.2.2 · Chi tiết và xác nhận</p><h1 className="mt-2 text-3xl font-semibold">{match.code}</h1><p className="mt-2 text-sm font-medium text-stone-500">{match.venueName} · {new Date(match.scheduledAt).toLocaleString('vi-VN')}</p></div><MatchStatusBadge status={status} /></div><div className="mt-6"><SprintTwoPreviewBanner /></div>{notice && <div className="mt-5 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-semibold text-emerald-800">{notice}</div>}
      <div className="mt-6 grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_330px]"><div className="grid gap-6"><section className="rounded-2xl border border-emerald-100 bg-white p-6 shadow-[0_16px_40px_rgba(24,65,44,0.09)]"><div className="flex flex-wrap items-center justify-between gap-3"><div><p className="text-xs font-bold uppercase tracking-[0.14em] text-stone-400">{matchTypeLabels[match.matchType]} · {match.courtName}</p><h2 className="mt-2 text-xl font-semibold">Kết quả trận đấu</h2></div>{match.ratingChange != null && <span className={classNames('rounded-full px-3 py-1 text-xs font-bold', match.ratingChange >= 0 ? 'bg-lime-100 text-emerald-800' : 'bg-red-50 text-red-600')}>{match.ratingChange >= 0 ? '+' : ''}{match.ratingChange} điểm</span>}</div><div className="mt-8"><MatchScore match={match} /></div><div className="mt-7 grid gap-3 sm:grid-cols-3">{match.games.map(([team1, team2], index) => <div key={index} className="rounded-xl bg-stone-50 p-4 text-center ring-1 ring-stone-100"><p className="text-[11px] font-bold uppercase text-stone-400">Set {index + 1}</p><p className="mt-2 text-2xl font-semibold"><span className={team1 > team2 ? 'text-emerald-700' : 'text-stone-400'}>{team1}</span><span className="mx-2 text-stone-300">—</span><span className={team2 > team1 ? 'text-emerald-700' : 'text-stone-400'}>{team2}</span></p></div>)}</div></section>
      <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)]"><ProfileSectionTitle icon={Users} eyebrow="Người tham gia" title="Trạng thái xác nhận" /><div className="mt-5 grid gap-5 sm:grid-cols-2">{[[1, match.team1], [2, match.team2]].map(([teamNo, players]) => <div key={teamNo}><p className="text-xs font-bold text-stone-400">ĐỘI {teamNo}</p><div className="mt-2 grid gap-2">{players.map((player) => <div key={player.id} className="flex items-center justify-between rounded-lg bg-stone-50 px-3 py-3"><div><p className="text-sm font-semibold">{player.id === 'me' ? user.name : player.name}</p><p className="mt-0.5 text-[11px] font-medium text-stone-400">Điểm {player.rating}</p></div>{player.confirmed || status === 'completed' ? <CheckCircle2 className="size-5 text-emerald-600" /> : <Clock3 className="size-5 text-amber-500" />}</div>)}</div></div>)}</div></section>{status === 'disputed' && <section className="rounded-xl border border-red-100 bg-red-50 p-5"><p className="text-sm font-semibold text-red-700">Kết quả đang được xem lại</p><p className="mt-2 text-xs font-medium leading-5 text-red-600">{match.disputeReason ?? 'Một người chơi đã báo kết quả chưa chính xác.'}</p></section>}</div>
      <aside className="grid gap-5 lg:sticky lg:top-20"><section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_12px_34px_rgba(24,65,44,0.08)]"><ProfileSectionTitle icon={ShieldCheck} eyebrow="Tính toàn vẹn" title="Xác nhận kết quả" />{canRespond ? <><p className="mt-4 text-xs font-medium leading-5 text-stone-500">Bạn thuộc đội đối phương. Hãy đối chiếu tỷ số trước khi xác nhận.</p><button onClick={() => { setStatus('completed'); setNotice('Bạn đã xác nhận kết quả. Trận đấu đủ điều kiện để cập nhật thống kê.') }} className="mt-5 flex h-11 w-full items-center justify-center gap-2 rounded-lg bg-emerald-700 text-sm font-semibold text-white"><CheckCircle2 className="size-4" />Kết quả chính xác</button><button onClick={() => { setStatus('disputed'); setNotice('Đã ghi nhận báo sai. Kết quả tạm thời không được tính vào hiệu suất.') }} className="mt-3 flex h-11 w-full items-center justify-center gap-2 rounded-lg border border-red-200 text-sm font-semibold text-red-600"><CircleAlert className="size-4" />Báo kết quả sai</button></> : <p className="mt-4 rounded-lg bg-stone-50 p-4 text-xs font-medium leading-5 text-stone-500">{status === 'completed' ? 'Kết quả đã được hai phía xác nhận và đã tính vào hiệu suất.' : status === 'disputed' ? 'Kết quả đang chờ người tạo chỉnh sửa.' : 'Bạn đã xác nhận. Đang chờ đội đối phương phản hồi.'}</p>}</section><a href="/performance" className="group rounded-xl bg-emerald-950 p-5 text-white shadow-[0_16px_36px_rgba(6,78,59,0.18)]"><BarChart3 className="size-6 text-lime-300" /><p className="mt-4 text-xs font-bold uppercase tracking-[0.14em] text-lime-300">Hiệu suất cá nhân</p><h3 className="mt-2 text-lg font-semibold">Xem trận này ảnh hưởng thế nào đến phong độ</h3><span className="mt-4 inline-flex items-center gap-2 text-xs font-semibold text-emerald-100">Mở dashboard <ArrowUpRight className="size-4 transition group-hover:translate-x-0.5 group-hover:-translate-y-0.5" /></span></a></aside></div></section><BottomNav activeLabel="Tài khoản" /></main>
  )
}

function RatingTrendChart({ points }) {
  const width = 640
  const height = 220
  const min = Math.min(...points.map((point) => point.rating)) - 40
  const max = Math.max(...points.map((point) => point.rating)) + 40
  const coords = points.map((point, index) => ({ ...point, x: 24 + index * ((width - 48) / (points.length - 1)), y: 20 + (max - point.rating) * ((height - 54) / (max - min)) }))
  const line = coords.map((point) => `${point.x},${point.y}`).join(' ')
  const area = `24,${height - 30} ${line} ${width - 24},${height - 30}`
  return <div><svg viewBox={`0 0 ${width} ${height}`} className="w-full overflow-visible" role="img" aria-label="Biểu đồ thay đổi điểm kỹ năng 90 ngày"><defs><linearGradient id="ratingArea" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#84cc16" stopOpacity="0.38" /><stop offset="100%" stopColor="#84cc16" stopOpacity="0" /></linearGradient></defs>{[50, 100, 150].map((y) => <line key={y} x1="24" x2={width - 24} y1={y} y2={y} stroke="#d1fae5" strokeDasharray="5 7" />)}<polygon points={area} fill="url(#ratingArea)" /><polyline points={line} fill="none" stroke="#047857" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round" />{coords.map((point) => <g key={point.date}><circle cx={point.x} cy={point.y} r="7" fill="#d9f99d" stroke="#047857" strokeWidth="4" /><text x={point.x} y={point.y - 14} textAnchor="middle" className="fill-emerald-950 text-[11px] font-bold">{point.rating}</text></g>)}</svg><div className="flex justify-between px-2 text-[10px] font-semibold text-stone-400"><span>01/07</span><span>18/08</span><span>02/10</span></div></div>
}

function PerformancePage({ user, onLogout }) {
  if (!user) return <ProfileEmptyState />
  const { summary, scoring, byMatchType, ratingTrend } = sprint2Performance
  return <main className="min-h-screen bg-[radial-gradient(circle_at_10%_4%,rgba(190,242,100,0.24),transparent_23%),linear-gradient(145deg,#edf8f2,#fafbf7)] pb-24 text-emerald-950"><AppHeader user={user} onLogout={onLogout} /><section className="overflow-hidden bg-emerald-950 px-4 pb-20 pt-8 text-white md:px-6"><div className="mx-auto max-w-[1200px]"><PageBackLink href="/profile" label="Về hồ sơ" tone="dark" /><div className="mt-8 grid gap-8 lg:grid-cols-[1fr_320px] lg:items-end"><div><p className="text-xs font-bold uppercase tracking-[0.18em] text-lime-300">2.2.10 · Hiệu suất người chơi</p><h1 className="mt-3 text-4xl font-semibold tracking-tight md:text-5xl">Phong độ của {user.name}</h1><p className="mt-4 max-w-2xl text-sm font-medium leading-7 text-emerald-100/75">Điểm số được hệ thống đo từ các trận đã xác nhận, tách biệt với trình độ bạn tự đánh giá.</p></div><div className="rounded-2xl border border-white/12 bg-white/8 p-5 backdrop-blur"><div className="flex items-end justify-between"><div><p className="text-xs font-semibold text-emerald-100/65">Điểm kỹ năng</p><p className="mt-2 text-5xl font-semibold text-lime-300">{summary.ratingScore}</p></div><Trophy className="size-10 text-lime-300" /></div><div className="mt-4 flex items-center gap-2"><span className="rounded-full bg-lime-300 px-3 py-1 text-xs font-bold text-emerald-950">Hệ thống đo: {summary.ratingLabel}</span>{summary.isProvisional && <span className="rounded-full bg-amber-200 px-3 py-1 text-xs font-bold text-amber-900">Tạm tính</span>}</div></div></div></div></section><section className="mx-auto -mt-10 max-w-[1200px] px-4 md:px-6"><div className="grid overflow-hidden rounded-xl border border-emerald-100 bg-white shadow-[0_18px_44px_rgba(24,65,44,0.1)] sm:grid-cols-2 lg:grid-cols-4">{[[`${summary.winRate}%`, `${summary.totalWins}/${summary.totalMatches} trận thắng`, TrendingUp], [summary.currentStreak, 'Chuỗi thắng hiện tại', Flame], [summary.longestWinStreak, 'Chuỗi thắng dài nhất', Trophy], [new Date(summary.lastPlayedAt).toLocaleDateString('vi-VN'), 'Trận gần nhất', CalendarDays]].map(([value, label, Icon], index) => <div key={label} className={classNames('flex items-center gap-4 p-5', index > 0 && 'border-t border-emerald-100 sm:border-l sm:border-t-0')}><span className="grid size-11 place-items-center rounded-lg bg-emerald-50 text-emerald-700"><Icon className="size-5" /></span><div><p className="text-xl font-semibold">{value}</p><p className="mt-1 text-xs font-medium text-stone-500">{label}</p></div></div>)}</div><div className="mt-6"><SprintTwoPreviewBanner /></div><div className="mt-6 grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_350px]"><div className="grid gap-6"><section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)]"><div className="flex flex-wrap items-start justify-between gap-3"><ProfileSectionTitle icon={Activity} eyebrow="90 ngày gần nhất" title="Đà tăng điểm kỹ năng" /><span className="rounded-full bg-lime-100 px-3 py-1 text-xs font-bold text-emerald-800">+372 điểm</span></div><div className="mt-6"><RatingTrendChart points={ratingTrend} /></div></section><section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)]"><ProfileSectionTitle icon={Swords} eyebrow="Theo hình thức" title="Bạn chơi tốt nhất ở đâu?" /><div className="mt-5 grid gap-4">{byMatchType.map((item) => <div key={item.type}><div className="flex items-end justify-between gap-4"><div><p className="text-sm font-semibold">{item.label}</p><p className="mt-1 text-xs font-medium text-stone-400">{item.wins}/{item.matches} trận thắng</p></div><p className="text-lg font-semibold text-emerald-700">{item.winRate}%</p></div><div className="mt-2 h-2.5 overflow-hidden rounded-full bg-emerald-50"><div className="h-full rounded-full bg-[linear-gradient(90deg,#059669,#a3e635)]" style={{ width: `${item.winRate}%` }} /></div></div>)}</div></section></div><aside className="grid gap-6 lg:sticky lg:top-20"><section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)]"><ProfileSectionTitle icon={BarChart3} eyebrow="Chất lượng thi đấu" title="Điểm số mỗi trận" /><div className="mt-5 grid grid-cols-2 gap-3">{[[scoring.avgPointsScored, 'Điểm ghi TB'], [scoring.avgPointsConceded, 'Điểm thua TB'], [`+${scoring.avgPointDiff}`, 'Chênh lệch'], [`${scoring.totalGamesWon}/${scoring.totalGamesLost}`, 'Set thắng/thua']].map(([value, label]) => <div key={label} className="rounded-xl bg-stone-50 p-4 ring-1 ring-stone-100"><p className="text-xl font-semibold text-emerald-950">{value}</p><p className="mt-1 text-[11px] font-medium text-stone-400">{label}</p></div>)}</div></section><section className="rounded-xl bg-emerald-900 p-5 text-white shadow-[0_14px_34px_rgba(6,78,59,0.18)]"><Award className="size-7 text-lime-300" /><p className="mt-4 text-xs font-bold uppercase tracking-[0.14em] text-lime-300">Hai góc nhìn</p><h3 className="mt-2 text-lg font-semibold">Tự đánh giá và hệ thống đo</h3><div className="mt-4 grid gap-2 text-xs font-medium"><p className="flex justify-between rounded-lg bg-white/8 px-3 py-2.5"><span className="text-emerald-100/70">Bạn tự đánh giá</span><span>Khá</span></p><p className="flex justify-between rounded-lg bg-lime-300 px-3 py-2.5 text-emerald-950"><span>Hệ thống đo</span><span>{summary.ratingLabel} · {summary.totalMatches} trận</span></p></div></section><a href="/matches" className="flex h-12 items-center justify-center gap-2 rounded-xl border border-emerald-200 bg-white text-sm font-semibold text-emerald-700"><History className="size-4" />Xem lịch sử trận đấu</a></aside></div></section><BottomNav activeLabel="Tài khoản" /></main>
}

export { MatchDetailPage, MatchesPage, NewMatchPage, PerformancePage }
