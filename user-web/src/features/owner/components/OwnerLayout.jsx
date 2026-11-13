import { LogOut, ShieldAlert } from 'lucide-react'
import { Navigate, NavLink, useLocation } from 'react-router-dom'
import { BrandMark } from '../../../app/AppLayout'
import { useApp } from '../../../app/useApp'
import { isOwner } from '../../../shared/roles'
import { classNames } from '../../../shared/utils'
import { ownerNavigation } from '../ownerNavigation'

const CHANGE_PASSWORD_PATH = '/owner/change-password'

function OwnerGate({ title, description, action }) {
  return (
    <main className="grid min-h-screen place-items-center bg-[linear-gradient(145deg,#effaf4,#f8faf4)] px-4 text-center text-emerald-950">
      <div className="w-full max-w-md rounded-xl border border-emerald-100 bg-white p-8 shadow-[0_20px_50px_rgba(25,70,48,0.12)]">
        <span className="mx-auto grid size-16 place-items-center rounded-full bg-emerald-50 text-emerald-700"><ShieldAlert className="size-8" /></span>
        <h1 className="mt-5 text-2xl font-semibold">{title}</h1>
        <p className="mt-2 text-sm font-medium leading-6 text-stone-500">{description}</p>
        {action}
      </div>
    </main>
  )
}

/**
 * Khung chung của khu quản lý chủ sân (2.3.x).
 *
 * Ẩn nội dung với người không phải chủ sân chỉ để giao diện rõ ràng; quyền thật được
 * backend kiểm tra ở mọi API `/api/v1/owner/**`.
 */
function OwnerLayout({ eyebrow, title, description, actions, children }) {
  const { user, logout } = useApp()
  const location = useLocation()
  const onPasswordPage = location.pathname === CHANGE_PASSWORD_PATH

  if (!user) {
    return (
      <OwnerGate
        title="Đăng nhập để quản lý sân"
        description="Khu quản lý dành cho chủ sân đăng nhập bằng tài khoản do quản trị viên Courtly cấp."
        action={<a className="mt-6 flex h-12 items-center justify-center rounded-lg bg-emerald-700 text-sm font-semibold text-white" href="/login">Đăng nhập</a>}
      />
    )
  }

  if (!isOwner(user)) {
    return (
      <OwnerGate
        title="Bạn không có quyền truy cập"
        description="Trang này chỉ dành cho tài khoản chủ sân. Tài khoản người chơi vẫn đặt sân bình thường ở trang chủ."
        action={<a className="mt-6 flex h-12 items-center justify-center rounded-lg bg-emerald-700 text-sm font-semibold text-white" href="/">Về trang chủ</a>}
      />
    )
  }

  if (user.passwordChangeRequired && !onPasswordPage) {
    return <Navigate to={CHANGE_PASSWORD_PATH} replace />
  }

  const showNavigation = !user.passwordChangeRequired

  return (
    <main className="min-h-screen bg-[linear-gradient(145deg,#eef8f2,#f9faf6)] pb-16 text-emerald-950">
      <header className="sticky top-0 z-40 border-b border-white/10 bg-emerald-950 text-white shadow-[0_8px_22px_rgba(4,47,32,0.14)]">
        <div className="mx-auto flex max-w-[1200px] items-center justify-between gap-3 px-4 py-2.5 md:px-6">
          <NavLink to="/owner" className="flex min-w-0 items-center gap-3 text-white">
            <BrandMark mini />
            <span className="min-w-0 leading-tight">
              <span className="block text-lg font-semibold">Courtly</span>
              <span className="block text-[11px] font-semibold uppercase tracking-[0.14em] text-lime-200">Quản lý sân</span>
            </span>
          </NavLink>
          <div className="flex min-w-0 items-center gap-2">
            <span className="hidden min-w-0 text-right leading-tight sm:block">
              <span className="block truncate text-[13px] font-semibold" data-testid="owner-name">{user.name}</span>
              <span className="block text-[11px] font-medium text-lime-200">Chủ sân</span>
            </span>
            <button
              type="button"
              className="grid size-10 shrink-0 place-items-center rounded-lg border border-white/22 text-white hover:bg-white/8"
              onClick={logout}
              aria-label="Đăng xuất"
              title="Đăng xuất"
            >
              <LogOut className="size-[18px]" />
            </button>
          </div>
        </div>
        {showNavigation && (
          <nav className="mx-auto max-w-[1200px] px-4 md:px-6" aria-label="Điều hướng quản lý sân">
            <div className="-mb-px flex gap-1 overflow-x-auto">
              {ownerNavigation.map((item) => {
                const Icon = item.icon
                return (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.end}
                    className={({ isActive }) => classNames(
                      'flex h-11 shrink-0 items-center gap-2 border-b-2 px-3 text-sm font-semibold transition',
                      isActive ? 'border-lime-300 text-white' : 'border-transparent text-white/65 hover:text-white',
                    )}
                  >
                    <Icon className="size-4" />
                    {item.label}
                  </NavLink>
                )
              })}
            </div>
          </nav>
        )}
      </header>

      <section className="mx-auto max-w-[1200px] px-4 py-7 md:px-6 md:py-9">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div className="min-w-0">
            {eyebrow && <p className="text-xs font-bold uppercase tracking-[0.15em] text-emerald-600">{eyebrow}</p>}
            <h1 className="mt-2 text-2xl font-semibold md:text-3xl">{title}</h1>
            {description && <p className="mt-2 max-w-2xl text-sm font-medium text-stone-500">{description}</p>}
          </div>
          {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
        </div>
        <div className="mt-7">{children}</div>
      </section>
    </main>
  )
}

export { OwnerLayout }
