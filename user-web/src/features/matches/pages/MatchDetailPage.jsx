import { useState } from 'react'
import { ArrowUpRight, BarChart3, CheckCircle2, Clock3, CircleAlert, ShieldCheck, Users } from 'lucide-react'
import { AppHeader, BottomNav } from '../../../app/AppLayout'
import { sprint2Matches } from '../../../data/sprint2Preview'
import { BookingEmptyState } from '../../bookings/components/BookingEmptyState'
import { PageBackLink, ProfileEmptyState, ProfileSectionTitle } from '../../profile/components/ProfileUi'
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

export default MatchDetailPage
