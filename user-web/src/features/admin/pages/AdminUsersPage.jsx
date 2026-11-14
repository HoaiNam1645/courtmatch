import { useState } from 'react'
import { CheckCircle2, ChevronRight, Search, X } from 'lucide-react'
import { fetchAdminUsers, ROLE_LABEL, USER_STATUS_TABS, userStatusMeta } from '../../../api/adminUsers'
import { useApp } from '../../../app/useApp'
import { useApiResource } from '../../../hooks/useApiResource'
import { useDebounced } from '../../../hooks/useDebounced'
import { classNames } from '../../../shared/utils'
import { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading } from '../../owner/components/OwnerUi'
import { formatDateTime } from '../../owner/ownerDates'
import { inputClass, secondaryButtonClass } from '../../owner/ownerStyles'
import { AdminLayout } from '../components/AdminLayout'
import { UserDetailDrawer } from '../components/UserDetailDrawer'

/** 2.3.28 — quản lý tài khoản người dùng. */
function AdminUsersPage() {
  const { user: me } = useApp()
  const [filters, setFilters] = useState({ role: 'all', status: 'all', page: 0 })
  const [query, setQuery] = useState('')
  const [selectedId, setSelectedId] = useState(null)
  const [notice, setNotice] = useState('')
  const q = useDebounced(query)
  const { status, data, error, reload } = useApiResource(
    () => fetchAdminUsers({ ...filters, q }),
    [filters.role, filters.status, filters.page, q],
    { isEmpty: (result) => result.users.length === 0 },
  )
  const counts = data?.statusCounts ?? {}
  const total = Object.values(counts).reduce((sum, value) => sum + value, 0)

  function setFilter(patch) {
    setFilters((current) => ({ ...current, page: 0, ...patch }))
  }

  return (
    <AdminLayout eyebrow="2.3.28 · Tài khoản" title="Quản lý tài khoản" description="Tìm, xem và khóa hoặc kích hoạt tài khoản người dùng.">
      {notice && <p role="status" className="mb-5 flex items-start gap-2 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-semibold text-emerald-800" data-testid="admin-notice"><CheckCircle2 className="mt-0.5 size-4 shrink-0" />{notice}</p>}
      <OwnerCard>
        <div className="flex gap-2 overflow-x-auto pb-1" role="tablist" aria-label="Trạng thái tài khoản">
          {USER_STATUS_TABS.map((tab) => (
            <button key={tab} type="button" role="tab" aria-selected={filters.status === tab} data-testid={`user-tab-${tab}`} className={classNames('flex h-10 shrink-0 items-center gap-2 rounded-lg px-4 text-sm font-semibold', filters.status === tab ? 'bg-slate-800 text-white' : 'bg-slate-100 text-slate-600')} onClick={() => setFilter({ status: tab })}>
              {tab === 'all' ? 'Tất cả' : userStatusMeta(tab).label}
              <span className={classNames('rounded-full px-2 text-xs font-bold', filters.status === tab ? 'bg-white/20' : 'bg-slate-200')}>{tab === 'all' ? total : counts[tab] ?? 0}</span>
            </button>
          ))}
        </div>
        <div className="mt-4 grid gap-3 sm:grid-cols-[minmax(0,1fr)_14rem]">
          <label className="flex h-11 items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 focus-within:border-slate-700">
            <Search className="size-4 shrink-0 text-slate-400" />
            <input className="min-w-0 flex-1 text-sm outline-none placeholder:text-slate-400" aria-label="Tìm tài khoản" placeholder="Tên, email hoặc số điện thoại" value={query} maxLength={100} onChange={(event) => { setQuery(event.target.value); setFilters((current) => ({ ...current, page: 0 })) }} />
            {query && <button type="button" aria-label="Xóa tìm kiếm" onClick={() => setQuery('')}><X className="size-4 text-slate-400" /></button>}
          </label>
          <select className={inputClass} aria-label="Lọc theo vai trò" value={filters.role} onChange={(event) => setFilter({ role: event.target.value })}>
            <option value="all">Tất cả vai trò</option>
            {Object.entries(ROLE_LABEL).map(([code, label]) => <option key={code} value={code}>{label}</option>)}
          </select>
        </div>

        <div className="mt-5">
          {status === 'loading' && <OwnerLoading rows={3} />}
          {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
          {status === 'empty' && <OwnerEmpty title="Không tìm thấy tài khoản nào" description="Thử đổi từ khóa hoặc bộ lọc." />}
          {status === 'success' && (
            <>
              <ul className="grid gap-3" data-testid="user-list">
                {data.users.map((item) => {
                  const meta = userStatusMeta(item.status)
                  return (
                    <li key={item.id} data-testid={`user-${item.email || item.phone}`} className="rounded-xl border border-slate-200 p-4">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', meta.className)} data-testid="user-status">{meta.label}</span>
                          {item.roles.map((role) => <span key={role} className="rounded-full bg-sky-100 px-2.5 py-1 text-xs font-bold text-sky-800">{ROLE_LABEL[role] ?? role}</span>)}
                        </div>
                        <button type="button" className="inline-flex h-9 items-center gap-1 rounded-lg bg-slate-800 px-3 text-xs font-semibold text-white" aria-label={`Xem chi tiết ${item.fullName}`} onClick={() => setSelectedId(item.id)}>Chi tiết<ChevronRight className="size-4" /></button>
                      </div>
                      <p className="mt-2 text-sm font-semibold text-slate-900">{item.fullName}</p>
                      <p className="break-all text-sm font-medium text-slate-600">{[item.email, item.phone].filter(Boolean).join(' · ')}</p>
                      <p className="mt-1 text-xs font-medium text-slate-500">Đăng nhập gần nhất: {item.lastLoginAt ? formatDateTime(item.lastLoginAt) : 'chưa đăng nhập'}</p>
                    </li>
                  )
                })}
              </ul>
              {data.totalPages > 1 && (
                <div className="mt-5 flex items-center justify-between gap-3">
                  <button type="button" className={secondaryButtonClass} disabled={filters.page === 0} onClick={() => setFilters((current) => ({ ...current, page: current.page - 1 }))}>Trang trước</button>
                  <p className="text-sm font-semibold text-slate-600">Trang {data.page + 1}/{data.totalPages} · {data.totalElements} tài khoản</p>
                  <button type="button" className={secondaryButtonClass} disabled={filters.page + 1 >= data.totalPages} onClick={() => setFilters((current) => ({ ...current, page: current.page + 1 }))}>Trang sau</button>
                </div>
              )}
            </>
          )}
        </div>
      </OwnerCard>
      {selectedId && <UserDetailDrawer userId={selectedId} selfId={me?.id} onClose={() => setSelectedId(null)} onChanged={(message) => { setNotice(message); reload() }} />}
    </AdminLayout>
  )
}

export default AdminUsersPage
