import { LogOut, ShieldAlert } from 'lucide-react'
import { NavLink } from 'react-router-dom'
import { BrandMark } from '../../../app/AppLayout'
import { useApp } from '../../../app/useApp'
import { isAdmin } from '../../../shared/roles'
import { classNames } from '../../../shared/utils'
import { adminNavigation, adminNavigationGroups } from '../adminNavigation'

function AdminGate({ title, description, href, action }) {
  return (
    <main className="grid min-h-screen place-items-center bg-[linear-gradient(145deg,#eef2fa,#f8f9fb)] px-4 text-center text-slate-900">
      <div className="w-full max-w-md rounded-xl border border-slate-200 bg-white p-8 shadow-[0_20px_50px_rgba(30,41,59,0.12)]">
        <span className="mx-auto grid size-16 place-items-center rounded-full bg-slate-100 text-slate-700"><ShieldAlert className="size-8" /></span>
        <h1 className="mt-5 text-2xl font-semibold">{title}</h1>
        <p className="mt-2 text-sm font-medium leading-6 text-slate-500">{description}</p>
        <a className="mt-6 flex h-12 items-center justify-center rounded-lg bg-slate-800 text-sm font-semibold text-white" href={href}>{action}</a>
      </div>
    </main>
  )
}

/**
 * Khung chung của khu quản trị (2.3.28 trở đi).
 *
 * Màn hình rộng dùng thanh bên có nhóm; màn hình nhỏ dùng thanh cuộn ngang trong tiêu đề (mờ dần ở
 * mép phải để gợi ý còn mục). Chỉ một trong hai hiện cùng lúc nên tên điều hướng không bị trùng.
 *
 * Ẩn nội dung với người không phải quản trị viên chỉ để giao diện rõ ràng; quyền thật được
 * backend kiểm tra ở mọi API `/api/v1/admin/**`.
 */
function AdminLayout({ eyebrow, title, description, actions, children }) {
  const { user, logout } = useApp()

  if (!user) {
    return <AdminGate title="Đăng nhập để quản trị" description="Khu quản trị dành cho quản trị viên Courtly." href="/login" action="Đăng nhập" />
  }
  if (!isAdmin(user)) {
    return <AdminGate title="Bạn không có quyền truy cập" description="Trang này chỉ dành cho tài khoản quản trị viên." href="/" action="Về trang chủ" />
  }

  return (
    <main className="min-h-screen bg-[linear-gradient(145deg,#eef2fa,#f8f9fb)] pb-16 text-slate-900">
      <header className="sticky top-0 z-40 border-b border-white/10 bg-slate-900 text-white shadow-[0_8px_22px_rgba(15,23,42,0.18)]">
        <div className="mx-auto flex max-w-[1320px] items-center justify-between gap-3 px-4 py-2.5 md:px-6">
          <NavLink to="/admin" className="flex min-w-0 items-center gap-3 text-white">
            <BrandMark mini />
            <span className="min-w-0 leading-tight">
              <span className="block text-lg font-semibold">Courtly</span>
              <span className="block text-[11px] font-semibold uppercase tracking-[0.14em] text-sky-200">Quản trị</span>
            </span>
          </NavLink>
          <div className="flex min-w-0 items-center gap-2">
            <span className="hidden min-w-0 text-right leading-tight sm:block">
              <span className="block truncate text-[13px] font-semibold" data-testid="admin-name">{user.name}</span>
              <span className="block text-[11px] font-medium text-sky-200">Quản trị viên</span>
            </span>
            <button type="button" className="grid size-10 shrink-0 place-items-center rounded-lg border border-white/22 text-white hover:bg-white/8" onClick={logout} aria-label="Đăng xuất" title="Đăng xuất">
              <LogOut className="size-[18px]" />
            </button>
          </div>
        </div>
        <nav className="mx-auto max-w-[1320px] px-4 md:px-6 lg:hidden" aria-label="Điều hướng quản trị">
          <div className="-mb-px flex gap-1 overflow-x-auto [mask-image:linear-gradient(to_right,black_calc(100%-28px),transparent)]">
            {adminNavigation.map((item) => {
              const Icon = item.icon
              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) => classNames(
                    'flex h-11 shrink-0 items-center gap-2 border-b-2 px-3 text-sm font-semibold transition',
                    isActive ? 'border-sky-300 text-white' : 'border-transparent text-white/65 hover:text-white',
                  )}
                >
                  <Icon className="size-4" />
                  {item.label}
                </NavLink>
              )
            })}
          </div>
        </nav>
      </header>

      <div className="mx-auto max-w-[1320px] px-4 md:px-6 lg:grid lg:grid-cols-[13.5rem_minmax(0,1fr)] lg:gap-8">
        <nav className="hidden lg:block" aria-label="Điều hướng quản trị">
          <div className="sticky top-24 grid gap-5 py-9">
            {adminNavigationGroups.map((group) => (
              <div key={group.label}>
                <p className="px-3 text-[11px] font-bold uppercase tracking-[0.14em] text-slate-400">{group.label}</p>
                <div className="mt-1.5 grid gap-0.5">
                  {group.items.map((item) => {
                    const Icon = item.icon
                    return (
                      <NavLink
                        key={item.to}
                        to={item.to}
                        end={item.end}
                        className={({ isActive }) => classNames(
                          'flex h-10 items-center gap-2.5 rounded-lg px-3 text-sm font-semibold transition',
                          isActive ? 'bg-slate-800 text-white shadow-sm' : 'text-slate-600 hover:bg-white hover:text-slate-900',
                        )}
                      >
                        <Icon className="size-4 shrink-0" />
                        {item.label}
                      </NavLink>
                    )
                  })}
                </div>
              </div>
            ))}
          </div>
        </nav>

        <section className="min-w-0 py-7 md:py-9">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div className="min-w-0">
              {eyebrow && <p className="text-xs font-bold uppercase tracking-[0.15em] text-sky-700">{eyebrow}</p>}
              <h1 className="mt-2 text-2xl font-semibold md:text-3xl">{title}</h1>
              {description && <p className="mt-2 max-w-2xl text-sm font-medium text-slate-500">{description}</p>}
            </div>
            {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
          </div>
          <div className="mt-7">{children}</div>
        </section>
      </div>
    </main>
  )
}

export { AdminLayout }
