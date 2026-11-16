import { Navigate } from 'react-router-dom'
import { AppHeader, BottomNav } from '../../app/AppLayout'
import { useApp } from '../../app/useApp'
import { isAdmin, isOwner } from '../../shared/roles'
import { AdminLayout } from '../admin/components/AdminLayout'
import { OwnerLayout } from '../owner/components/OwnerLayout'
import { NotificationsPanel } from './NotificationsPanel'

const TITLE = 'Thông báo'
const DESCRIPTION = 'Thông báo về đặt sân, thanh toán, ghép cặp và trận đấu của bạn.'

/** 2.3.61 - 2.3.62: trang thông báo dùng chung, hiển thị trong khung của vai trò hiện tại. */
function NotificationsPage() {
  const { user, logout } = useApp()
  if (!user) return <Navigate to="/login" replace />
  if (isAdmin(user)) {
    return <AdminLayout eyebrow="2.3.61 – 2.3.62" title={TITLE} description={DESCRIPTION}><NotificationsPanel /></AdminLayout>
  }
  if (isOwner(user)) {
    return <OwnerLayout eyebrow="2.3.61 – 2.3.62" title={TITLE} description={DESCRIPTION}><NotificationsPanel /></OwnerLayout>
  }
  return (
    <main className="min-h-screen bg-[linear-gradient(145deg,#edf8f2,#fbfcf8)] pb-24 text-emerald-950">
      <AppHeader user={user} onLogout={logout} />
      <section className="mx-auto max-w-[920px] px-4 py-8 md:px-6">
        <p className="text-xs font-bold uppercase tracking-[0.15em] text-emerald-700">2.3.61 – 2.3.62</p>
        <h1 className="mt-2 text-3xl font-semibold">{TITLE}</h1>
        <p className="mt-2 text-sm font-medium text-stone-500">{DESCRIPTION}</p>
        <div className="mt-6"><NotificationsPanel /></div>
      </section>
      <BottomNav activeLabel="Tài khoản" />
    </main>
  )
}

export default NotificationsPage
