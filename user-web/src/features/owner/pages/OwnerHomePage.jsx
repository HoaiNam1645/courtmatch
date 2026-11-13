import { KeyRound, Mail, Phone } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useApp } from '../../../app/useApp'
import { OwnerLayout } from '../components/OwnerLayout'
import { OwnerCard } from '../components/OwnerUi'
import { ownerNavigation } from '../ownerNavigation'

/** Trang đầu của khu quản lý chủ sân (2.3.1). */
function OwnerHomePage() {
  const { user } = useApp()
  const shortcuts = ownerNavigation.filter((item) => item.to !== '/owner')

  return (
    <OwnerLayout
      eyebrow="Khu quản lý chủ sân"
      title={user ? `Xin chào, ${user.name}` : 'Quản lý sân'}
      description="Quản lý hồ sơ, sân, bảng giá, lịch sân và các yêu cầu đặt sân của bạn."
    >
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_340px]">
        <OwnerCard title="Chức năng quản lý">
          {shortcuts.length === 0 ? (
            <p className="text-sm font-medium text-stone-500">Các chức năng quản lý sẽ xuất hiện tại đây.</p>
          ) : (
            <div className="grid gap-3 sm:grid-cols-2">
              {shortcuts.map((item) => {
                const Icon = item.icon
                return (
                  <Link key={item.to} to={item.to} className="flex items-center gap-3 rounded-lg border border-emerald-100 bg-emerald-50/50 p-4 text-sm font-semibold text-emerald-900 hover:bg-emerald-50">
                    <span className="grid size-10 place-items-center rounded-lg bg-white text-emerald-700 ring-1 ring-emerald-100"><Icon className="size-5" /></span>
                    {item.label}
                  </Link>
                )
              })}
            </div>
          )}
        </OwnerCard>

        <OwnerCard title="Tài khoản">
          <dl className="grid gap-3 text-sm">
            <div className="flex items-center gap-3"><Mail className="size-4 shrink-0 text-emerald-700" /><dd className="min-w-0 break-all font-medium">{user?.email || 'Chưa có email'}</dd></div>
            <div className="flex items-center gap-3"><Phone className="size-4 shrink-0 text-emerald-700" /><dd className="font-medium">{user?.phone || 'Chưa có số điện thoại'}</dd></div>
          </dl>
          <Link to="/owner/change-password" className="mt-5 inline-flex h-10 items-center gap-2 rounded-lg border border-emerald-200 px-4 text-sm font-semibold text-emerald-800 hover:bg-emerald-50">
            <KeyRound className="size-4" />
            Đổi mật khẩu
          </Link>
        </OwnerCard>
      </div>
    </OwnerLayout>
  )
}

export default OwnerHomePage
