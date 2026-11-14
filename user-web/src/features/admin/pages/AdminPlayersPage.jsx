import { useState } from 'react'
import { CheckCircle2, ChevronRight, Search, X } from 'lucide-react'
import { fetchAdminPlayers, PLAYER_SKILLS, SKILL_LABEL } from '../../../api/adminPlayers'
import { userStatusMeta } from '../../../api/adminUsers'
import { useApiResource } from '../../../hooks/useApiResource'
import { useDebounced } from '../../../hooks/useDebounced'
import { classNames } from '../../../shared/utils'
import { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading } from '../../owner/components/OwnerUi'
import { formatDateTime } from '../../owner/ownerDates'
import { inputClass, secondaryButtonClass } from '../../owner/ownerStyles'
import { AdminLayout } from '../components/AdminLayout'
import { PlayerDetailDrawer } from '../components/PlayerDetailDrawer'

/** 2.3.31 — quản lý tài khoản người chơi. */
function AdminPlayersPage() {
  const [filters, setFilters] = useState({ status: 'all', skill: 'all', from: '', to: '', page: 0 })
  const [query, setQuery] = useState('')
  const [selectedId, setSelectedId] = useState(null)
  const [notice, setNotice] = useState('')
  const q = useDebounced(query)
  const invalidRange = filters.from && filters.to && filters.to < filters.from
  const { status, data, error, reload } = useApiResource(
    () => (invalidRange ? Promise.resolve(null) : fetchAdminPlayers({ ...filters, q })),
    [filters.status, filters.skill, filters.from, filters.to, filters.page, q],
    { isEmpty: (result) => result !== null && result.players.length === 0 },
  )

  function setFilter(patch) {
    setFilters((current) => ({ ...current, page: 0, ...patch }))
  }

  return (
    <AdminLayout eyebrow="2.3.31 · Người chơi" title="Tài khoản người chơi" description="Tìm, xem hồ sơ và khóa hoặc mở khóa tài khoản người chơi.">
      {notice && <p role="status" className="mb-5 flex items-start gap-2 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-semibold text-emerald-800" data-testid="admin-notice"><CheckCircle2 className="mt-0.5 size-4 shrink-0" />{notice}</p>}
      <OwnerCard>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <label className="flex h-11 items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 focus-within:border-slate-700 sm:col-span-2">
            <Search className="size-4 shrink-0 text-slate-400" />
            <input className="min-w-0 flex-1 text-sm outline-none placeholder:text-slate-400" aria-label="Tìm người chơi" placeholder="Tên, email hoặc số điện thoại" value={query} maxLength={100} onChange={(event) => { setQuery(event.target.value); setFilters((current) => ({ ...current, page: 0 })) }} />
            {query && <button type="button" aria-label="Xóa tìm kiếm" onClick={() => setQuery('')}><X className="size-4 text-slate-400" /></button>}
          </label>
          <select className={inputClass} aria-label="Lọc theo trạng thái" value={filters.status} onChange={(event) => setFilter({ status: event.target.value })}>
            <option value="all">Mọi trạng thái</option>
            {['active', 'inactive', 'banned', 'pending'].map((value) => <option key={value} value={value}>{userStatusMeta(value).label}</option>)}
          </select>
          <select className={inputClass} aria-label="Lọc theo trình độ" value={filters.skill} onChange={(event) => setFilter({ skill: event.target.value })}>
            {PLAYER_SKILLS.map((value) => <option key={value} value={value}>{value === 'all' ? 'Mọi trình độ' : SKILL_LABEL[value]}</option>)}
          </select>
          <input type="date" className={inputClass} aria-label="Đăng ký từ ngày" value={filters.from} onChange={(event) => setFilter({ from: event.target.value })} />
          <input type="date" className={inputClass} aria-label="Đăng ký đến ngày" value={filters.to} onChange={(event) => setFilter({ to: event.target.value })} />
        </div>
        {invalidRange && <p className="mt-2 text-sm font-medium text-red-600" role="alert">Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.</p>}

        <div className="mt-5">
          {!invalidRange && status === 'loading' && <OwnerLoading rows={3} />}
          {!invalidRange && status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
          {!invalidRange && status === 'empty' && <OwnerEmpty title="Không tìm thấy người chơi nào" description="Thử đổi từ khóa hoặc bộ lọc." />}
          {!invalidRange && status === 'success' && data && (
            <>
              <ul className="grid gap-3" data-testid="player-list">
                {data.players.map((item) => {
                  const meta = userStatusMeta(item.status)
                  return (
                    <li key={item.id} data-testid={`player-${item.email || item.phone}`} className="rounded-xl border border-slate-200 p-4">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', meta.className)} data-testid="player-status">{meta.label}</span>
                          {item.skillLevel && <span className="rounded-full bg-sky-100 px-2.5 py-1 text-xs font-bold text-sky-800">{SKILL_LABEL[item.skillLevel]}</span>}
                        </div>
                        <button type="button" className="inline-flex h-9 items-center gap-1 rounded-lg bg-slate-800 px-3 text-xs font-semibold text-white" aria-label={`Xem chi tiết ${item.fullName}`} onClick={() => setSelectedId(item.id)}>Chi tiết<ChevronRight className="size-4" /></button>
                      </div>
                      <p className="mt-2 text-sm font-semibold text-slate-900">{item.fullName}</p>
                      <p className="break-all text-sm font-medium text-slate-600">{[item.email, item.phone].filter(Boolean).join(' · ')}</p>
                      <p className="mt-1 text-xs font-medium text-slate-500">{item.bookingCount} booking · đăng ký {formatDateTime(item.createdAt)}</p>
                    </li>
                  )
                })}
              </ul>
              {data.totalPages > 1 && (
                <div className="mt-5 flex items-center justify-between gap-3">
                  <button type="button" className={secondaryButtonClass} disabled={filters.page === 0} onClick={() => setFilters((current) => ({ ...current, page: current.page - 1 }))}>Trang trước</button>
                  <p className="text-sm font-semibold text-slate-600">Trang {data.page + 1}/{data.totalPages} · {data.totalElements} người chơi</p>
                  <button type="button" className={secondaryButtonClass} disabled={filters.page + 1 >= data.totalPages} onClick={() => setFilters((current) => ({ ...current, page: current.page + 1 }))}>Trang sau</button>
                </div>
              )}
            </>
          )}
        </div>
      </OwnerCard>
      {selectedId && <PlayerDetailDrawer playerId={selectedId} onClose={() => setSelectedId(null)} onChanged={(message) => { setNotice(message); reload() }} />}
    </AdminLayout>
  )
}

export default AdminPlayersPage
