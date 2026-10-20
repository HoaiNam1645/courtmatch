import { ArrowLeft, UserRound } from 'lucide-react'
import { AppHeader } from '../../../app/AppLayout'
import { classNames } from '../../../shared/utils'

function ProfileSectionTitle({ icon: Icon, eyebrow, title, action, href }) {
  const actionClass = 'text-xs font-semibold text-emerald-700 hover:text-emerald-900'
  return (
    <div className="flex items-start justify-between gap-4">
      <div className="flex items-start gap-3">
        <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-emerald-50 text-emerald-700 ring-1 ring-emerald-100">
          <Icon className="size-5" />
        </span>
        <div>
          <p className="text-[11px] font-bold uppercase tracking-[0.15em] text-emerald-600">{eyebrow}</p>
          <h2 className="mt-1 text-lg font-semibold text-emerald-950">{title}</h2>
        </div>
      </div>
      {action && (href ? <a className={actionClass} href={href}>{action}</a> : <span className={actionClass}>{action}</span>)}
    </div>
  )
}

function PageBackLink({ href, label = 'Quay lại', tone = 'light' }) {
  return (
    <a
      href={href}
      className={classNames(
        'inline-flex h-10 items-center gap-2 rounded-lg px-3.5 text-sm font-semibold transition',
        tone === 'dark'
          ? 'border border-white/18 bg-white/10 text-white backdrop-blur hover:bg-white/16'
          : 'border border-emerald-100 bg-white text-emerald-800 shadow-sm hover:bg-emerald-50',
      )}
    >
      <ArrowLeft className="size-4" />
      {label}
    </a>
  )
}

function ProfileEmptyState() {
  return (
    <main className="grid min-h-screen place-items-center bg-[linear-gradient(145deg,#effaf4,#f8faf4)] px-4 text-center text-emerald-950">
      <div className="max-w-md rounded-xl border border-emerald-100 bg-white p-8 shadow-[0_20px_50px_rgba(25,70,48,0.12)]">
        <span className="mx-auto grid size-16 place-items-center rounded-full bg-emerald-50 text-emerald-700"><UserRound className="size-8" /></span>
        <h1 className="mt-5 text-2xl font-semibold">Đăng nhập để xem hồ sơ</h1>
        <p className="mt-2 text-sm font-medium leading-6 text-stone-500">Thông tin và thiết lập chơi của bạn sẽ được lưu trong tài khoản Courtly.</p>
        <a className="mt-6 flex h-12 items-center justify-center rounded-lg bg-emerald-700 text-sm font-semibold text-white" href="/login">Đăng nhập</a>
      </div>
    </main>
  )
}

function ProfileLoading({ user, onLogout }) {
  return (
    <main className="min-h-screen bg-[#f4f7f1] pb-24">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="mx-auto max-w-[1100px] px-4 py-14">
        <div className="grid gap-4">
          <div className="h-32 animate-pulse rounded-xl bg-emerald-100/70" />
          <div className="h-24 animate-pulse rounded-xl bg-emerald-100/50" />
          <div className="h-24 animate-pulse rounded-xl bg-emerald-100/40" />
        </div>
      </section>
    </main>
  )
}

function ProfileLoadError({ user, onLogout, message, onRetry }) {
  return (
    <main className="min-h-screen bg-[#f4f7f1] pb-24">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="mx-auto max-w-[680px] px-4 py-16 text-center">
        <h2 className="text-xl font-semibold text-emerald-950">Không tải được hồ sơ</h2>
        <p className="mt-3 text-sm font-medium text-stone-600">{message}</p>
        <button className="mt-6 h-11 rounded-lg bg-emerald-700 px-6 text-sm font-semibold text-white" onClick={onRetry}>Thử lại</button>
      </section>
    </main>
  )
}

export { PageBackLink, ProfileEmptyState, ProfileLoadError, ProfileLoading, ProfileSectionTitle }
