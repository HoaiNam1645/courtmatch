import { useState } from 'react'
import { CalendarDays, CheckCircle2, CircleAlert, ShieldCheck, Trophy, Users } from 'lucide-react'
import { AppHeader, BottomNav } from '../../../app/AppLayout'
import { sprint2Players, sprint2Suggestions } from '../../../data/sprint2Preview'
import { PageBackLink, ProfileEmptyState, ProfileSectionTitle } from '../../profile/components/ProfileUi'
import { classNames } from '../../../shared/utils'

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

export default NewMatchPage
