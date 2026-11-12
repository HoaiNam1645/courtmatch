import { useMemo, useState } from 'react'
import { CalendarDays, CheckCircle2, LoaderCircle, RotateCcw, ShieldCheck, Users } from 'lucide-react'
import { AppHeader, BottomNav } from '../../../app/AppLayout'
import { createMatch } from '../../../api/matches'
import { useMatchSetup } from '../../../hooks/useMatchSetup'
import { PageBackLink, ProfileEmptyState, ProfileSectionTitle } from '../../profile/components/ProfileUi'
import { classNames } from '../../../shared/utils'

const matchTypeLabels = { single: 'Đánh đơn', double: 'Đánh đôi', mixed_double: 'Đôi nam nữ' }

function NewMatchPage({ user, onLogout }) {
  const requestedBookingId = new URLSearchParams(window.location.search).get('booking')
  const setup = useMatchSetup()
  const [source, setSource] = useState(requestedBookingId ? 'booking' : 'manual')
  const [bookingId, setBookingId] = useState(requestedBookingId ?? '')
  const [venueId, setVenueId] = useState('')
  const [playedAt, setPlayedAt] = useState('')
  const [matchType, setMatchType] = useState('double')
  const [team1Partner, setTeam1Partner] = useState('')
  const [team2First, setTeam2First] = useState('')
  const [team2Second, setTeam2Second] = useState('')
  const [submitState, setSubmitState] = useState({ status: 'idle', message: '' })

  const effectiveBookingId = setup.suggestions.some((item) => item.bookingId === bookingId)
    ? bookingId : setup.suggestions[0]?.bookingId ?? ''
  const effectiveVenueId = venueId || setup.venues[0]?.id || ''
  const effectiveTeam1Partner = team1Partner || setup.players[0]?.id || ''
  const effectiveTeam2First = team2First || setup.players[1]?.id || setup.players[0]?.id || ''
  const effectiveTeam2Second = team2Second || setup.players[2]?.id || setup.players[1]?.id || setup.players[0]?.id || ''

  const selectedSuggestion = useMemo(
    () => setup.suggestions.find((item) => item.bookingId === effectiveBookingId),
    [setup.suggestions, effectiveBookingId],
  )
  const minimumOpponentCount = matchType === 'single' ? 1 : 3
  const hasEnoughPlayers = setup.players.length >= minimumOpponentCount

  if (!user) return <ProfileEmptyState />

  async function submitMatch(event) {
    event.preventDefault()
    const team1 = [user.id, ...(matchType === 'single' ? [] : [effectiveTeam1Partner])]
    const team2 = [effectiveTeam2First, ...(matchType === 'single' ? [] : [effectiveTeam2Second])]
    if (new Set([...team1, ...team2]).size !== team1.length + team2.length) {
      setSubmitState({ status: 'error', message: 'Một người chơi không thể xuất hiện ở cả hai đội.' })
      return
    }
    setSubmitState({ status: 'submitting', message: '' })
    try {
      const match = await createMatch({
        bookingId: source === 'booking' ? effectiveBookingId : null,
        venueId: source === 'manual' && effectiveVenueId ? effectiveVenueId : null,
        matchType,
        scheduledAt: source === 'manual' ? new Date(playedAt).toISOString() : null,
        team1UserIds: team1,
        team2UserIds: team2,
      })
      window.location.assign(`/matches/${match.id}`)
    } catch (error) {
      setSubmitState({ status: 'error', message: error?.message ?? 'Không thể tạo trận đấu.' })
    }
  }

  return (
    <main className="min-h-screen bg-[linear-gradient(145deg,#eef8f2,#fafbf7)] pb-24 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="mx-auto max-w-[1100px] px-4 py-7 md:px-6 md:py-10">
        <PageBackLink href="/matches" label="Về danh sách trận" />
        <div className="mt-6"><p className="text-xs font-bold uppercase tracking-[0.15em] text-emerald-600">2.2.37 · Tạo trận đấu</p><h1 className="mt-2 text-3xl font-semibold">Tạo trận đấu</h1><p className="mt-2 text-sm font-medium text-stone-500">Chọn nguồn trận, chia đội trước; tỷ số sẽ được ghi ở bước tiếp theo để các bên cùng xác nhận.</p></div>

        {setup.status === 'loading' && <StatePanel icon={LoaderCircle} spin title="Đang tải dữ liệu" description="Courtly đang lấy booking, sân và người chơi từ hệ thống." />}
        {setup.status === 'error' && <StatePanel icon={RotateCcw} title="Không tải được dữ liệu" description={setup.error} action="Thử lại" onAction={setup.reload} />}
        {setup.status === 'success' && !hasEnoughPlayers && <StatePanel icon={Users} title="Chưa đủ người chơi" description={matchType === 'single' ? 'Cần ít nhất một người chơi khác để tạo trận đơn.' : 'Cần ít nhất ba người chơi khác để tạo trận đôi.'} />}

        {setup.status === 'success' && hasEnoughPlayers && (
          <form onSubmit={submitMatch} className="mt-6 grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_350px]">
            <div className="grid gap-6">
              <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)]">
                <ProfileSectionTitle icon={CalendarDays} eyebrow="Nguồn trận đấu" title="Thời gian và địa điểm" />
                <div className="mt-5 grid grid-cols-2 gap-3">{[['booking', 'Từ booking'], ['manual', 'Nhập thủ công']].map(([id, label]) => <button type="button" key={id} onClick={() => setSource(id)} className={classNames('h-11 rounded-lg border text-sm font-semibold', source === id ? 'border-emerald-500 bg-emerald-50 text-emerald-800 ring-1 ring-emerald-400' : 'border-stone-200 text-stone-500')}>{label}</button>)}</div>
                {source === 'booking' ? (
                  setup.suggestions.length === 0 ? <p className="mt-4 rounded-lg bg-stone-50 p-4 text-sm text-stone-500">Không có booking hoàn tất nào cần ghi kết quả trong 30 ngày gần đây.</p> : <><select value={effectiveBookingId} onChange={(event) => setBookingId(event.target.value)} className="mt-4 h-12 w-full rounded-lg border border-stone-200 bg-white px-3 text-sm font-semibold">{setup.suggestions.map((item) => <option key={item.bookingId} value={item.bookingId}>{item.bookingCode} · {item.venueName}</option>)}</select>{selectedSuggestion && <div className="mt-3 rounded-xl bg-emerald-950 p-5 text-white"><p className="text-xs font-bold text-lime-300">{selectedSuggestion.bookingCode}</p><h3 className="mt-2 text-lg font-semibold">{selectedSuggestion.venueName}</h3><p className="mt-2 text-xs text-emerald-100/70">{selectedSuggestion.courtName} · {new Date(selectedSuggestion.playedAt).toLocaleString('vi-VN')}</p></div>}</>
                ) : (
                  <div className="mt-4 grid gap-4 sm:grid-cols-2"><label className="text-sm font-semibold">Sân<select value={effectiveVenueId} onChange={(event) => setVenueId(event.target.value)} className="mt-2 h-11 w-full rounded-lg border border-stone-200 bg-white px-3 text-sm"><option value="">Ngoài hệ thống / không xác định</option>{setup.venues.map((venue) => <option key={venue.id} value={venue.id}>{venue.name}</option>)}</select></label><label className="text-sm font-semibold">Thời gian<input required type="datetime-local" value={playedAt} max={new Date().toISOString().slice(0, 16)} onChange={(event) => setPlayedAt(event.target.value)} className="mt-2 h-11 w-full rounded-lg border border-stone-200 px-3 text-sm" /></label></div>
                )}
              </section>

              <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_10px_28px_rgba(34,52,40,0.06)]">
                <ProfileSectionTitle icon={Users} eyebrow="Người tham gia" title="Chia đội thi đấu" />
                <div className="mt-5 grid grid-cols-3 gap-2">{Object.entries(matchTypeLabels).map(([id, label]) => <button type="button" key={id} onClick={() => setMatchType(id)} className={classNames('min-h-11 rounded-lg border px-2 text-xs font-semibold', matchType === id ? 'border-emerald-500 bg-emerald-50 text-emerald-800 ring-1 ring-emerald-400' : 'border-stone-200 text-stone-500')}>{label}</button>)}</div>
                <div className="mt-5 grid gap-4 sm:grid-cols-2"><TeamEditor title="Đội của bạn" accent="emerald"><PlayerSelect value={user.id} disabled options={[{ id: user.id, fullName: user.name }]} />{matchType !== 'single' && <PlayerSelect value={effectiveTeam1Partner} onChange={setTeam1Partner} options={setup.players} />}</TeamEditor><TeamEditor title="Đội đối phương" accent="amber"><PlayerSelect value={effectiveTeam2First} onChange={setTeam2First} options={setup.players} />{matchType !== 'single' && <PlayerSelect value={effectiveTeam2Second} onChange={setTeam2Second} options={setup.players} />}</TeamEditor></div>
              </section>

            </div>

            <aside className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_14px_38px_rgba(24,65,44,0.10)] lg:sticky lg:top-20">
              <ProfileSectionTitle icon={ShieldCheck} eyebrow="Xác nhận dữ liệu" title="Trước khi gửi" />
              <div className="mt-5 grid gap-3 text-xs font-medium leading-5 text-stone-500"><p className="rounded-lg bg-emerald-50 p-3 text-emerald-800">Sau khi tạo, bạn sẽ chuyển sang trang chi tiết để nhập tỷ số.</p><p>Chỉ người tạo được ghi kết quả; mọi người tham gia đều có thể xem và phản hồi sau đó.</p><p>Trận chưa có kết quả không được tính vào Elo hoặc thống kê hiệu suất.</p></div>
              {submitState.message && <p className={classNames('mt-4 rounded-lg p-3 text-xs font-medium', submitState.status === 'success' ? 'bg-emerald-50 text-emerald-800' : 'bg-red-50 text-red-700')}>{submitState.message}</p>}
              <button disabled={submitState.status === 'submitting' || (source === 'booking' && !effectiveBookingId)} className="mt-5 flex h-12 w-full items-center justify-center gap-2 rounded-lg bg-lime-300 text-sm font-semibold text-emerald-950 disabled:cursor-not-allowed disabled:opacity-60">{submitState.status === 'submitting' ? <LoaderCircle className="size-4 animate-spin" /> : <CheckCircle2 className="size-4" />}Tạo trận đấu</button>
            </aside>
          </form>
        )}
      </section>
      <BottomNav activeLabel="Tài khoản" />
    </main>
  )
}

function StatePanel({ icon: Icon, spin = false, title, description, action, onAction }) {
  return <div className="mt-6 rounded-xl border border-emerald-100 bg-white p-8 text-center shadow-sm"><Icon className={classNames('mx-auto size-7 text-emerald-600', spin && 'animate-spin')} /><h2 className="mt-3 text-lg font-semibold">{title}</h2><p className="mt-2 text-sm text-stone-500">{description}</p>{action && <button onClick={onAction} className="mt-4 rounded-lg bg-emerald-700 px-4 py-2 text-sm font-semibold text-white">{action}</button>}</div>
}

function TeamEditor({ title, accent, children }) {
  return <div className={classNames('rounded-xl border p-4', accent === 'emerald' ? 'border-emerald-100 bg-emerald-50/60' : 'border-amber-100 bg-amber-50/60')}><p className={classNames('text-xs font-bold uppercase tracking-[0.12em]', accent === 'emerald' ? 'text-emerald-700' : 'text-amber-700')}>{title}</p><div className="mt-3 grid gap-3">{children}</div></div>
}

function PlayerSelect({ value, onChange, options, disabled = false }) {
  return <select required disabled={disabled} value={value} onChange={(event) => onChange?.(event.target.value)} className="h-12 w-full rounded-lg border border-white bg-white px-3 text-sm font-semibold shadow-sm disabled:opacity-100">{options.map((player) => <option key={player.id} value={player.id}>{player.fullName}</option>)}</select>
}

export default NewMatchPage
