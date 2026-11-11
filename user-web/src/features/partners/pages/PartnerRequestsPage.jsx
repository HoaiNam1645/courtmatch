import { ArrowLeft, ArrowRight, Check, Inbox, LoaderCircle, RefreshCw, Search, UserRound, X } from 'lucide-react'
import { useState } from 'react'
import { AppHeader, BottomNav } from '../../../app/AppLayout'
import { acceptPartnerRequest, cancelPartnerRequest, rejectPartnerRequest } from '../../../api/partnerRequests'
import { usePartnerRequests } from '../../../hooks/useReceivedPartnerRequests'
import { classNames } from '../../../shared/utils'
import { formatPartnerRequestTime, statusUi } from '../partnerRequestUi'

const filters = [
  { value: 'pending', label: 'Đang chờ xử lý' },
  { value: 'all', label: 'Tất cả yêu cầu' },
]

function Avatar({ player }) {
  if (player.avatarUrl) return <img src={player.avatarUrl} alt="" className="size-12 rounded-2xl object-cover" />
  return <span className="grid size-12 shrink-0 place-items-center rounded-2xl bg-emerald-950 font-semibold text-lime-300">{player.fullName.slice(0, 1).toUpperCase()}</span>
}

function PartnerRequestsPage({ user, onLogout }) {
  const [filter, setFilter] = useState('pending')
  const [direction, setDirection] = useState('received')
  const [action, setAction] = useState({ requestId: null, kind: null, error: '' })
  const requests = usePartnerRequests(direction, filter)
  const respond = async (requestId, kind) => {
    setAction({ requestId, kind, error: '' })
    try {
      const actionCall = kind === 'accept'
        ? acceptPartnerRequest
        : kind === 'reject' ? rejectPartnerRequest : cancelPartnerRequest
      await actionCall(requestId)
      await requests.load(requests.page)
      setAction({ requestId: null, kind: null, error: '' })
    } catch (error) {
      setAction({ requestId: null, kind: null, error: error?.message ?? 'Không thể xử lý yêu cầu.' })
    }
  }

  return (
    <main className="min-h-screen bg-[radial-gradient(circle_at_10%_4%,rgba(190,242,100,0.22),transparent_23%),linear-gradient(145deg,#edf8f2,#fbfcf8)] pb-24 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="bg-emerald-950 px-4 pb-20 pt-9 text-white md:px-6">
        <div className="mx-auto max-w-[1080px]">
          <a href="/partners" className="inline-flex items-center gap-2 text-xs font-semibold text-emerald-100/70"><ArrowLeft className="size-4" />Về tìm đối tác</a>
          <div className="mt-7 flex flex-wrap items-end justify-between gap-5">
            <div><p className="text-xs font-bold uppercase tracking-[0.16em] text-lime-300">2.2.32–2.2.35 · Yêu cầu ghép cặp</p><h1 className="mt-3 text-4xl font-semibold tracking-tight">Quản lý lời mời ghép cặp</h1><p className="mt-3 max-w-xl text-sm leading-6 text-emerald-100/70">Theo dõi lời mời đã nhận, lời mời đã gửi và xử lý đúng trạng thái ở một nơi.</p></div>
            <div className="flex gap-2"><a href="/partners/connections" className="inline-flex h-11 items-center rounded-xl border border-white/20 px-4 text-sm font-semibold text-white">Đối tác đã ghép</a><a href="/partners" className="inline-flex h-11 items-center gap-2 rounded-xl bg-lime-300 px-5 text-sm font-semibold text-emerald-950"><Search className="size-4" />Tìm thêm đối tác</a></div>
          </div>
        </div>
      </section>

      <section className="mx-auto -mt-10 max-w-[1080px] px-4 md:px-6">
        <div className="rounded-2xl border border-emerald-100 bg-white p-4 shadow-[0_18px_45px_rgba(20,70,48,0.12)]">
          <div className="mb-4 grid grid-cols-2 gap-2 rounded-xl bg-stone-100 p-1"><button type="button" onClick={() => { setDirection('received'); setFilter('pending') }} className={classNames('h-10 rounded-lg text-xs font-semibold', direction === 'received' ? 'bg-white text-emerald-800 shadow-sm' : 'text-stone-500')}>Yêu cầu đã nhận</button><button type="button" onClick={() => { setDirection('sent'); setFilter('pending') }} className={classNames('h-10 rounded-lg text-xs font-semibold', direction === 'sent' ? 'bg-white text-emerald-800 shadow-sm' : 'text-stone-500')}>Yêu cầu đã gửi</button></div>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex gap-2">{filters.map((item) => <button key={item.value} type="button" onClick={() => setFilter(item.value)} className={classNames('h-10 rounded-xl px-4 text-xs font-semibold', filter === item.value ? 'bg-emerald-700 text-white' : 'bg-emerald-50 text-emerald-700')}>{item.label}</button>)}</div>
            <span className="text-xs font-semibold text-stone-400">{requests.totalElements} yêu cầu {direction === 'sent' ? 'đã gửi' : 'đã nhận'}</span>
          </div>
          {action.error && <p className="mt-3 rounded-xl bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">{action.error}</p>}
        </div>

        {requests.status === 'loading' && <div className="mt-5 grid min-h-56 place-items-center rounded-2xl border border-emerald-100 bg-white"><div className="text-center"><LoaderCircle className="mx-auto size-8 animate-spin text-emerald-600" /><p className="mt-3 text-sm font-semibold">Đang tải lời mời...</p></div></div>}
        {requests.status === 'error' && <div className="mt-5 grid min-h-56 place-items-center rounded-2xl border border-red-100 bg-white p-6 text-center"><div><p className="text-sm font-semibold text-red-700">{requests.error}</p><button type="button" onClick={() => requests.load(requests.page)} className="mt-4 inline-flex h-10 items-center gap-2 rounded-xl bg-emerald-700 px-4 text-xs font-semibold text-white"><RefreshCw className="size-4" />Thử lại</button></div></div>}
        {requests.status === 'empty' && <div className="mt-5 grid min-h-56 place-items-center rounded-2xl border border-dashed border-emerald-200 bg-white/75 p-6 text-center"><div><Inbox className="mx-auto size-9 text-emerald-300" /><p className="mt-3 text-sm font-semibold">{filter === 'pending' ? 'Không có yêu cầu nào đang chờ' : direction === 'sent' ? 'Bạn chưa gửi yêu cầu ghép cặp' : 'Bạn chưa nhận yêu cầu ghép cặp'}</p><p className="mt-1 text-xs text-stone-500">Các lời mời mới sẽ xuất hiện tại đây.</p></div></div>}
        {requests.status === 'success' && <div className="mt-5 grid gap-4">{requests.data.map((request) => {
          const badge = statusUi(request.status)
          const counterpart = direction === 'sent' ? request.receiver : request.requester
          return <article key={request.id} className="rounded-2xl border border-emerald-100 bg-white p-5 shadow-[0_10px_30px_rgba(30,70,48,0.06)]"><div className="flex items-start gap-4"><Avatar player={counterpart} /><div className="min-w-0 flex-1"><div className="flex flex-wrap items-center justify-between gap-2"><div><h2 className="font-semibold">{counterpart.fullName}</h2><p className="mt-1 text-xs font-medium text-stone-400">{direction === 'sent' ? 'Bạn gửi lúc' : 'Gửi lúc'} {formatPartnerRequestTime(request.createdAt)}</p></div><span className={classNames('rounded-full px-3 py-1 text-[11px] font-bold', badge.className)}>{badge.label}</span></div><div className="mt-4 rounded-xl bg-[#f5f8f2] px-4 py-3"><p className="text-sm leading-6 text-stone-600">“{request.message}”</p></div>{request.status === 'pending' && direction === 'received' && <div className="mt-4 flex flex-wrap items-center justify-between gap-3"><p className="flex items-center gap-2 text-xs font-semibold text-emerald-700"><UserRound className="size-4" />Đang chờ bạn phản hồi</p><div className="flex gap-2"><button type="button" disabled={action.requestId != null} onClick={() => respond(request.id, 'reject')} className="inline-flex h-10 items-center gap-2 rounded-xl border border-red-200 bg-white px-4 text-xs font-semibold text-red-700 disabled:opacity-50">{action.requestId === request.id && action.kind === 'reject' ? <LoaderCircle className="size-4 animate-spin" /> : <X className="size-4" />}{action.requestId === request.id && action.kind === 'reject' ? 'Đang từ chối...' : 'Từ chối'}</button><button type="button" disabled={action.requestId != null} onClick={() => respond(request.id, 'accept')} className="inline-flex h-10 items-center gap-2 rounded-xl bg-emerald-700 px-4 text-xs font-semibold text-white disabled:opacity-50">{action.requestId === request.id && action.kind === 'accept' ? <LoaderCircle className="size-4 animate-spin" /> : <Check className="size-4" />}{action.requestId === request.id && action.kind === 'accept' ? 'Đang chấp nhận...' : 'Chấp nhận'}</button></div></div>}{request.status === 'pending' && direction === 'sent' && <div className="mt-4 flex flex-wrap items-center justify-between gap-3"><p className="text-xs font-semibold text-amber-700">Đang chờ đối tác phản hồi</p><button type="button" disabled={action.requestId != null} onClick={() => respond(request.id, 'cancel')} className="inline-flex h-10 items-center gap-2 rounded-xl border border-stone-200 px-4 text-xs font-semibold text-stone-600 disabled:opacity-50">{action.requestId === request.id && action.kind === 'cancel' ? <LoaderCircle className="size-4 animate-spin" /> : <X className="size-4" />}{action.requestId === request.id && action.kind === 'cancel' ? 'Đang hủy...' : 'Hủy yêu cầu'}</button></div>}</div></div></article>
        })}</div>}

        {requests.totalPages > 1 && <div className="mt-6 flex items-center justify-center gap-3"><button type="button" aria-label="Trang trước" disabled={requests.page === 0 || requests.status === 'loading'} onClick={() => requests.load(requests.page - 1)} className="grid size-11 place-items-center rounded-xl border border-emerald-200 bg-white text-emerald-700 disabled:opacity-40"><ArrowLeft className="size-4" /></button><span className="min-w-24 text-center text-xs font-semibold text-stone-500">Trang {requests.page + 1}/{requests.totalPages}</span><button type="button" aria-label="Trang sau" disabled={requests.page + 1 >= requests.totalPages || requests.status === 'loading'} onClick={() => requests.load(requests.page + 1)} className="grid size-11 place-items-center rounded-xl border border-emerald-200 bg-white text-emerald-700 disabled:opacity-40"><ArrowRight className="size-4" /></button></div>}
      </section>
      <BottomNav activeLabel="Đối tác" />
    </main>
  )
}

export default PartnerRequestsPage
