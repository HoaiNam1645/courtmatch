import { Link } from 'react-router-dom'
import { useApp } from '../../../app/useApp'
import { OwnerCard } from '../../owner/components/OwnerUi'
import { AdminLayout } from '../components/AdminLayout'
import { adminNavigation } from '../adminNavigation'

/** Trang đầu của khu quản trị. */
function AdminHomePage() {
  const { user } = useApp()
  const shortcuts = adminNavigation.filter((item) => item.to !== '/admin')
  return (
    <AdminLayout eyebrow="Khu quản trị" title={user ? `Xin chào, ${user.name}` : 'Quản trị'} description="Quản lý tài khoản, vai trò, quyền, chủ sân và duyệt sân cầu lông.">
      <OwnerCard title="Chức năng quản trị">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {shortcuts.map((item) => {
            const Icon = item.icon
            return (
              <Link key={item.to} to={item.to} className="flex items-center gap-3 rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm font-semibold text-slate-800 hover:bg-slate-100">
                <span className="grid size-10 place-items-center rounded-lg bg-white text-slate-700 ring-1 ring-slate-200"><Icon className="size-5" /></span>
                {item.label}
              </Link>
            )
          })}
        </div>
      </OwnerCard>
    </AdminLayout>
  )
}

export default AdminHomePage
