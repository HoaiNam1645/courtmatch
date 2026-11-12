import { useState } from 'react'
import { CheckCircle2, Clock3, CircleAlert, LoaderCircle, RotateCcw, ShieldCheck, Trash2, Users } from 'lucide-react'
import { AppHeader, BottomNav } from '../../../app/AppLayout'
import { cancelMatch, confirmMatch, disputeMatch, recordMatchResult } from '../../../api/matches'
import { useMatchDetail } from '../../../hooks/useMatchDetail'
import { BookingEmptyState } from '../../bookings/components/BookingEmptyState'
import { PageBackLink, ProfileEmptyState, ProfileSectionTitle } from '../../profile/components/ProfileUi'
import PartnerReviewForm from '../../partners/components/PartnerReviewForm'
import { classNames } from '../../../shared/utils'

const statusMeta = {
  scheduled: { label: 'Chờ xác nhận', className: 'bg-amber-100 text-amber-800' },
  completed: { label: 'Đã xác nhận', className: 'bg-emerald-100 text-emerald-700' },
  disputed: { label: 'Đang tranh chấp', className: 'bg-red-100 text-red-700' },
  cancelled: { label: 'Đã huỷ', className: 'bg-stone-200 text-stone-600' },
}
const typeLabel = { single: 'Đánh đơn', double: 'Đánh đôi', mixed_double: 'Đôi nam nữ' }

function MatchDetailPage({ matchId, user, onLogout }) {
  const detail = useMatchDetail(matchId)
  const [action, setAction] = useState('idle')
  const [reason, setReason] = useState('')
  const [notice, setNotice] = useState('')
  if (!user) return <ProfileEmptyState />

  async function run(work, success) {
    setAction('submitting')
    setNotice('')
    try {
      await work()
      setNotice(success)
      detail.reload()
    } catch (error) {
      setNotice(error?.message ?? 'Không thể thực hiện thao tác.')
    } finally {
      setAction('idle')
    }
  }

  if (detail.status === 'loading') return <StatePage user={user} onLogout={onLogout} icon={LoaderCircle} spin title="Đang tải trận đấu" />
  if (detail.status === 'not-found') return <main className="min-h-screen bg-[#f4f7f1] pb-24"><AppHeader user={user} onLogout={onLogout} /><section className="mx-auto max-w-[900px] px-4 py-12"><BookingEmptyState title="Không tìm thấy trận đấu" description="Trận đấu không tồn tại hoặc bạn không phải người tham gia." actionLabel="Về dữ liệu thi đấu" actionHref="/matches" /></section></main>
  if (detail.status === 'error') return <StatePage user={user} onLogout={onLogout} icon={RotateCcw} title={detail.error} action="Thử lại" onAction={detail.reload} />

  const match = detail.match
  const reviewablePlayers = [...match.team1, ...match.team2]
    .filter((player) => player.id !== user.id && !player.reviewedByCurrentUser)
  const meta = statusMeta[match.status] ?? statusMeta.scheduled
  const team1Sets = match.games.filter((game) => game.team1Score > game.team2Score).length
  const team2Sets = match.games.filter((game) => game.team2Score > game.team1Score).length

  return (
    <main className="min-h-screen bg-[linear-gradient(145deg,#edf8f2,#fafbf7)] pb-24 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="mx-auto max-w-[1050px] px-4 py-7 md:px-6 md:py-10">
        <PageBackLink href="/matches" label="Về dữ liệu thi đấu" />
        <div className="mt-6 flex flex-wrap items-start justify-between gap-4"><div><p className="text-xs font-bold uppercase tracking-[0.15em] text-emerald-600">2.2.41 · Kết quả trận đấu</p><h1 className="mt-2 text-3xl font-semibold">Kết quả trận đấu</h1><p className="mt-2 text-sm text-stone-500">{match.venue?.name ?? 'Ngoài hệ thống'} · {new Date(match.scheduledAt).toLocaleString('vi-VN')}</p></div><span className={classNames('rounded-full px-3 py-1 text-xs font-bold', meta.className)}>{match.games.length === 0 && match.status === 'scheduled' ? 'Chờ ghi kết quả' : meta.label}</span></div>
        {notice && <p className="mt-5 rounded-xl border border-emerald-100 bg-white p-4 text-sm font-semibold text-emerald-800">{notice}</p>}

        <div className="mt-6 grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_330px]">
          <div className="grid gap-6">
            <section className="rounded-2xl border border-emerald-100 bg-white p-6 shadow-[0_16px_40px_rgba(24,65,44,0.09)]">
              <p className="text-xs font-bold uppercase text-stone-400">{typeLabel[match.matchType]}</p>
              <div className="mt-8 grid grid-cols-[1fr_90px_1fr] items-center gap-3"><TeamNames players={match.team1} align="right" /><div className="grid grid-cols-3 rounded-lg bg-emerald-950 px-3 py-2 text-center text-white"><strong>{match.games.length ? team1Sets : '–'}</strong><span className="text-lime-300">—</span><strong>{match.games.length ? team2Sets : '–'}</strong></div><TeamNames players={match.team2} /></div>
              {match.games.length > 0 ? <div className="mt-7 grid gap-3 sm:grid-cols-3">{match.games.map((game) => <div key={game.gameNo} className="rounded-xl bg-stone-50 p-4 text-center"><p className="text-[11px] font-bold uppercase text-stone-400">Set {game.gameNo}</p><p className="mt-2 text-2xl font-semibold">{game.team1Score}<span className="mx-2 text-stone-300">—</span>{game.team2Score}</p></div>)}</div> : <p className="mt-7 rounded-xl bg-amber-50 p-4 text-sm font-medium text-amber-800">Trận đấu đã được tạo và đang chờ người tạo ghi kết quả.</p>}
            </section>

            <section className="rounded-xl border border-emerald-100 bg-white p-5"><ProfileSectionTitle icon={Users} eyebrow="Người tham gia" title="Trạng thái xác nhận" /><div className="mt-5 grid gap-5 sm:grid-cols-2"><PlayerTeam title="Đội 1" players={match.team1} /><PlayerTeam title="Đội 2" players={match.team2} /></div></section>
            {match.status === 'completed' && <section><ProfileSectionTitle icon={ShieldCheck} eyebrow="2.2.43–2.2.44 · Đánh giá đối tác" title="Chia sẻ trải nghiệm sau trận" />{reviewablePlayers.length > 0 ? <div className="mt-4 grid gap-4 sm:grid-cols-2">{reviewablePlayers.map((player) => <PartnerReviewForm key={player.id} matchId={match.id} player={player} onReviewed={(name) => { setNotice(`Đã gửi đánh giá cho ${name}.`); detail.reload() }} />)}</div> : <p className="mt-4 rounded-xl border border-emerald-100 bg-white p-5 text-sm text-stone-500">Bạn đã đánh giá tất cả người chơi khác trong trận này.</p>}</section>}
            {match.currentUserCanRecordResult && <ResultEditor key={`${match.id}:${match.games.map((game) => `${game.team1Score}-${game.team2Score}`).join(',')}`} match={match} disabled={action === 'submitting'} onSubmit={(payload) => run(() => recordMatchResult(match.id, payload), 'Đã ghi kết quả; đang chờ đối thủ xác nhận.')} />}
            {match.status === 'disputed' && <div className="rounded-xl border border-red-100 bg-red-50 p-5 text-sm text-red-700"><strong>Kết quả đang bị báo sai.</strong><p className="mt-2">{match.note}</p></div>}
          </div>

          <aside className="grid gap-5 lg:sticky lg:top-20">
            <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_12px_34px_rgba(24,65,44,0.08)]"><ProfileSectionTitle icon={ShieldCheck} eyebrow="Tính toàn vẹn" title="Phản hồi kết quả" />
              {match.currentUserCanConfirm && <button disabled={action === 'submitting'} onClick={() => run(() => confirmMatch(match.id), 'Bạn đã xác nhận kết quả.')} className="mt-5 flex h-11 w-full items-center justify-center gap-2 rounded-lg bg-emerald-700 text-sm font-semibold text-white disabled:opacity-60"><CheckCircle2 className="size-4" />Kết quả chính xác</button>}
              {match.currentUserCanDispute && <><textarea value={reason} onChange={(event) => setReason(event.target.value)} maxLength={500} placeholder="Mô tả điểm chưa chính xác" className="mt-3 min-h-24 w-full rounded-lg border border-stone-200 p-3 text-sm" /><button disabled={action === 'submitting' || !reason.trim()} onClick={() => run(() => disputeMatch(match.id, reason.trim()), 'Đã báo sai; trận tạm ngừng tính thống kê.')} className="mt-2 flex h-11 w-full items-center justify-center gap-2 rounded-lg border border-red-200 text-sm font-semibold text-red-600 disabled:opacity-50"><CircleAlert className="size-4" />Báo kết quả sai</button></>}
              {!match.currentUserCanConfirm && !match.currentUserCanDispute && <p className="mt-4 rounded-lg bg-stone-50 p-4 text-xs text-stone-500">{match.status === 'completed' ? 'Kết quả đã đủ xác nhận và đã vào thống kê.' : match.status === 'cancelled' ? 'Trận đấu đã bị huỷ.' : 'Bạn đã phản hồi; đang chờ người chơi còn lại.'}</p>}
              {match.currentUserCanCancel && <button disabled={action === 'submitting'} onClick={() => run(() => cancelMatch(match.id), 'Đã huỷ trận đấu.')} className="mt-4 flex h-10 w-full items-center justify-center gap-2 text-xs font-semibold text-stone-500"><Trash2 className="size-4" />Huỷ trận</button>}
            </section>
          </aside>
        </div>
      </section>
      <BottomNav activeLabel="Tài khoản" />
    </main>
  )
}

function TeamNames({ players, align = 'left' }) {
  return <div className={align === 'right' ? 'text-right' : ''}><p className="text-sm font-semibold">{players.map((player) => player.fullName).join(' · ')}</p><p className="mt-1 text-[11px] text-stone-400">{align === 'right' ? 'Đội 1' : 'Đội 2'}</p></div>
}

function PlayerTeam({ title, players }) {
  return <div><p className="text-xs font-bold text-stone-400">{title.toUpperCase()}</p><div className="mt-2 grid gap-2">{players.map((player) => <div key={player.id} className="flex items-center justify-between rounded-lg bg-stone-50 px-3 py-3"><span className="text-sm font-semibold">{player.fullName}</span>{player.disputed ? <CircleAlert className="size-5 text-red-500" /> : player.confirmed ? <CheckCircle2 className="size-5 text-emerald-600" /> : <Clock3 className="size-5 text-amber-500" />}</div>)}</div></div>
}

function ResultEditor({ match, disabled, onSubmit }) {
  const [games, setGames] = useState(match.games.length > 0
    ? match.games.map((game) => [game.team1Score, game.team2Score])
    : [[21, 18], [21, 16]])

  function updateScore(index, side, value) {
    setGames((current) => current.map((game, gameIndex) => (
      gameIndex === index
        ? game.map((score, scoreIndex) => scoreIndex === side ? Number(value) : score)
        : game
    )))
  }

  function selectGameCount(count) {
    setGames((current) => count === 2
      ? current.slice(0, 2)
      : [...current, [21, 18]].slice(0, 3))
  }

  const submit = () => onSubmit(games.map(([team1Score, team2Score], index) => ({
    gameNo: index + 1, team1Score, team2Score,
  })))

  return <section className="rounded-xl border border-emerald-100 bg-white p-5"><div className="flex flex-wrap items-end justify-between gap-3"><ProfileSectionTitle icon={ShieldCheck} eyebrow="2.2.38 · Ghi nhận kết quả" title={match.games.length > 0 ? 'Chỉnh sửa tỷ số từng set' : 'Nhập tỷ số từng set'} /><div className="flex gap-2">{[2, 3].map((count) => <button type="button" key={count} onClick={() => selectGameCount(count)} className={classNames('rounded-full px-3 py-1 text-xs font-bold', games.length === count ? 'bg-emerald-700 text-white' : 'bg-stone-100 text-stone-500')}>{count} set</button>)}</div></div><div className="mt-5 grid gap-3">{games.map((game, index) => <div key={index} className="grid grid-cols-[64px_1fr_auto_1fr] items-center gap-3 rounded-xl bg-stone-50 p-3"><span className="text-xs font-bold text-stone-400">SET {index + 1}</span><input aria-label={`Điểm đội 1 set ${index + 1}`} type="number" min="0" max="30" value={game[0]} onChange={(event) => updateScore(index, 0, event.target.value)} className="h-11 min-w-0 rounded-lg border border-emerald-200 bg-white text-center text-lg font-semibold" /><span className="text-stone-300">—</span><input aria-label={`Điểm đội 2 set ${index + 1}`} type="number" min="0" max="30" value={game[1]} onChange={(event) => updateScore(index, 1, event.target.value)} className="h-11 min-w-0 rounded-lg border border-amber-200 bg-white text-center text-lg font-semibold" /></div>)}</div><button type="button" disabled={disabled} onClick={submit} className="mt-5 flex h-11 w-full items-center justify-center gap-2 rounded-lg bg-emerald-700 text-sm font-semibold text-white disabled:opacity-60"><CheckCircle2 className="size-4" />Lưu kết quả trận đấu</button></section>
}

function StatePage({ user, onLogout, icon: Icon, spin, title, action, onAction }) {
  return <main className="min-h-screen bg-[#f4f7f1] pb-24"><AppHeader user={user} onLogout={onLogout} /><section className="mx-auto max-w-[800px] px-4 py-16 text-center"><Icon className={classNames('mx-auto size-8 text-emerald-600', spin && 'animate-spin')} /><h1 className="mt-4 text-xl font-semibold">{title}</h1>{action && <button onClick={onAction} className="mt-5 rounded-lg bg-emerald-700 px-4 py-2 text-sm font-semibold text-white">{action}</button>}</section></main>
}

export default MatchDetailPage
