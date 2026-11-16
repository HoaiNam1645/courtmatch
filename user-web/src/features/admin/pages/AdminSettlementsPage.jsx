import { useRef, useState } from 'react'
import { CheckCircle2, Plus, Search, X } from 'lucide-react'
import { fetchAdminSettlements } from '../../../api/adminSettlements'
import { SETTLEMENT_TABS, settlementStatusMeta } from '../../../api/ownerSettlements'
import { useApiResource } from '../../../hooks/useApiResource'
import { useDebounced } from '../../../hooks/useDebounced'
import { classNames, formatCurrency } from '../../../shared/utils'
import { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading } from '../../owner/components/OwnerUi'
import { formatDateTime, formatIsoDate } from '../../owner/ownerDates'
import { inputClass, primaryButtonClass, secondaryButtonClass } from '../../owner/ownerStyles'
import { AdminLayout } from '../components/AdminLayout'
import { CommissionDetailDrawer } from '../components/CommissionDetailDrawer'
import { SettlementDialog } from '../components/SettlementDialog'
import { SettlementDrawer } from '../components/SettlementDrawer'

const EMPTY_FILTERS = { status: 'all', ownerId: '', from: '', to: '', overdue: false, page: 0 }

/** 2.3.45 — danh sách kỳ công nợ hoa hồng của các chủ sân; chốt kỳ mới; mở chi tiết (2.3.46). */
function AdminSettlementsPage() {
  const [filters, setFilters] = useState(EMPTY_FILTERS)
  const [query, setQuery] = useState('')
  const q = useDebounced(query)
  const [openId, setOpenId] = useState(null)
  const [commissionId, setCommissionId] = useState(null)
  const [creating, setCreating] = useState(false)
  const [notice, setNotice] = useState('')
  const [refreshKey, setRefreshKey] = useState(0)
  const invalidRange = Boolean(filters.from && filters.to && filters.to < filters.from)
  const { status, data, error, reload } = useApiResource(
    () => (invalidRange ? Promise.resolve(null) : fetchAdminSettlements({ ...filters, q })),
    [filters.status, filters.ownerId, filters.from, filters.to, filters.overdue, filters.page, q, invalidRange],
    { isEmpty: (result) => result !== null && result.rows.length === 0 },
  )
  // Giữ danh sách chủ sân và số lượng theo trạng thái của lần tải gần nhất để giao diện không nhảy khi tải lại.
  const lastRef = useRef({ owners: [], statusCounts: {} })
  if (data) lastRef.current = { owners: data.owners, statusCounts: data.statusCounts }
  const { owners, statusCounts } = lastRef.current
  const filtered = filters.status !== 'all' || filters.ownerId || filters.from || filters.to || filters.overdue || q

  function setFilter(patch) {
    setFilters((current) => ({ ...current, page: 0, ...patch }))
  }
  const totalCount = Object.values(statusCounts).reduce((sum, value) => sum + value, 0)

  return (
    <AdminLayout
      eyebrow="2.3.45 – 2.3.46 · Công nợ hoa hồng"
      title="Kỳ công nợ hoa hồng"
      description="Công nợ hoa hồng của từng chủ sân theo kỳ: số phải thu, đã thu, trạng thái và các booking cấu thành."
      actions={<button type="button" className={primaryButtonClass} data-testid="settlement-create" onClick={() => setCreating(true)}><Plus className="size-4" />Chốt kỳ</button>}
    >
      {notice && <p role="status" className="mb-4 flex items-start gap-2 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-semibold text-emerald-800" data-testid="admin-notice"><CheckCircle2 className="mt-0.5 size-4 shrink-0" />{notice}</p>}
      <OwnerCard>
        <div className="flex flex-wrap gap-2" role="tablist" aria-label="Lọc theo trạng thái kỳ">
          {SETTLEMENT_TABS.map((tab) => {
            const count = tab === 'all' ? totalCount : statusCounts[tab] ?? 0
            const active = filters.status === tab
            return (
              <button key={tab} type="button" role="tab" aria-selected={active} data-testid={`settlement-tab-${tab}`}
                className={classNames('h-10 rounded-lg px-3 text-sm font-semibold transition', active ? 'bg-slate-800 text-white' : 'bg-slate-100 text-slate-700 hover:bg-slate-200')}
                onClick={() => setFilter({ status: tab })}>
                {tab === 'all' ? 'Tất cả' : settlementStatusMeta(tab).label} <span className="ml-1 text-xs opacity-80" data-testid={`settlement-tab-count-${tab}`}>{count}</span>
              </button>
            )
          })}
        </div>
        <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <label className="flex h-11 items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 focus-within:border-slate-700 sm:col-span-2 lg:col-span-1">
            <Search className="size-4 shrink-0 text-slate-400" />
            <input className="min-w-0 flex-1 text-sm outline-none placeholder:text-slate-400" aria-label="Tìm chủ sân" placeholder="Tên hoặc email chủ sân" value={query} maxLength={100} onChange={(event) => { setQuery(event.target.value); setFilters((current) => ({ ...current, page: 0 })) }} />
            {query && <button type="button" aria-label="Xóa tìm kiếm" onClick={() => setQuery('')}><X className="size-4 text-slate-400" /></button>}
          </label>
          <select className={inputClass} aria-label="Lọc theo chủ sân" value={filters.ownerId} onChange={(event) => setFilter({ ownerId: event.target.value })}>
            <option value="">Tất cả chủ sân</option>
            {owners.map((owner) => <option key={owner.id} value={owner.id}>{owner.fullName}</option>)}
          </select>
          <label className="block text-xs font-semibold text-slate-600">Kỳ kết thúc từ ngày<input type="date" className={`${inputClass} mt-1`} aria-label="Kỳ từ ngày" value={filters.from} onChange={(event) => setFilter({ from: event.target.value })} /></label>
          <label className="block text-xs font-semibold text-slate-600">Kỳ bắt đầu đến ngày<input type="date" className={`${inputClass} mt-1`} aria-label="Kỳ đến ngày" value={filters.to} onChange={(event) => setFilter({ to: event.target.value })} /></label>
          <label className="flex h-11 items-center gap-2 self-end rounded-lg border border-slate-300 bg-white px-3 text-sm font-semibold text-slate-700">
            <input type="checkbox" checked={filters.overdue} onChange={(event) => setFilter({ overdue: event.target.checked })} data-testid="settlement-overdue-only" />Chỉ kỳ quá hạn
          </label>
          {filtered && <button type="button" className={`${secondaryButtonClass} self-end`} data-testid="settlement-clear" onClick={() => { setFilters(EMPTY_FILTERS); setQuery('') }}>Xóa bộ lọc</button>}
        </div>
        {invalidRange && <p className="mt-2 text-sm font-medium text-red-600" role="alert" data-testid="settlement-range-error">Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.</p>}

        <div className="mt-5">
          {status === 'loading' && !data && <OwnerLoading rows={3} />}
          {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
          {status === 'empty' && <OwnerEmpty title="Không có kỳ công nợ nào" description={filtered ? 'Thử bỏ bớt bộ lọc hoặc đổi từ khóa.' : 'Dùng nút "Chốt kỳ" để gom hoa hồng chưa đối soát của một chủ sân thành kỳ.'} />}
          {data && status !== 'empty' && (
            <>
              <div className="mb-4 grid gap-3 rounded-xl bg-slate-50 p-4 sm:grid-cols-4" data-testid="settlement-totals">
                <div><p className="text-xs font-semibold text-slate-500">Số kỳ</p><p className="text-lg font-semibold text-slate-900" data-testid="settlement-total-count">{data.totals.count.toLocaleString('vi-VN')}</p></div>
                <div><p className="text-xs font-semibold text-slate-500">Phải thu</p><p className="text-lg font-semibold text-slate-900" data-testid="settlement-total-due">{formatCurrency(data.totals.amountDue)}</p></div>
                <div><p className="text-xs font-semibold text-slate-500">Đã thu</p><p className="text-lg font-semibold text-slate-900" data-testid="settlement-total-paid">{formatCurrency(data.totals.amountPaid)}</p></div>
                <div><p className="text-xs font-semibold text-slate-500">Còn lại</p><p className="text-lg font-semibold text-slate-900" data-testid="settlement-total-remaining">{formatCurrency(data.totals.remaining)}</p></div>
              </div>
              <ul className="grid gap-3" data-testid="settlement-list">
                {data.rows.map((row) => {
                  const meta = settlementStatusMeta(row.status)
                  return (
                    <li key={row.id} data-testid={`settlement-${row.id}`} className={classNames('rounded-xl border p-4', row.overdue ? 'border-red-200 bg-red-50/40' : 'border-slate-200')}>
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', meta.className)} data-testid="settlement-status">{meta.label}</span>
                          {row.overdue && <span className="rounded-full bg-red-100 px-2.5 py-1 text-xs font-bold text-red-700" data-testid="settlement-overdue">Quá hạn</span>}
                          {row.pendingReports > 0 && <span className="rounded-full bg-sky-100 px-2.5 py-1 text-xs font-bold text-sky-700" data-testid="settlement-reports">{row.pendingReports} báo cáo chờ xác nhận</span>}
                        </div>
                        <button type="button" className="h-9 rounded-lg border border-slate-300 px-3 text-xs font-semibold text-slate-700 hover:bg-slate-50" data-testid="settlement-detail" aria-label={`Xem chi tiết kỳ ${row.ownerName} ${formatIsoDate(row.periodFrom)}`} onClick={() => setOpenId(row.id)}>Chi tiết</button>
                      </div>
                      <p className="mt-2 text-sm font-semibold text-slate-900" data-testid="settlement-owner">{row.ownerName}</p>
                      <p className="text-xs font-medium text-slate-500" data-testid="settlement-period">Kỳ {formatIsoDate(row.periodFrom)} – {formatIsoDate(row.periodTo)} · {row.bookingCount} booking · chốt {formatDateTime(row.createdAt)}</p>
                      <div className="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-xs font-medium text-slate-600">
                        <span data-testid="settlement-due">Phải thu <b className="text-slate-900">{formatCurrency(row.amountDue)}</b></span>
                        <span data-testid="settlement-paid">Đã thu {formatCurrency(row.amountPaid)}</span>
                        <span data-testid="settlement-remaining">Còn lại <b className={row.remaining > 0 ? 'text-red-700' : 'text-emerald-700'}>{formatCurrency(row.remaining)}</b></span>
                      </div>
                    </li>
                  )
                })}
              </ul>
              {data.totalPages > 1 && (
                <div className="mt-5 flex items-center justify-between gap-3">
                  <button type="button" className={secondaryButtonClass} disabled={filters.page === 0} onClick={() => setFilters((current) => ({ ...current, page: current.page - 1 }))}>Trang trước</button>
                  <p className="text-sm font-semibold text-slate-600">Trang {data.page + 1}/{data.totalPages} · {data.totalElements.toLocaleString('vi-VN')} kỳ</p>
                  <button type="button" className={secondaryButtonClass} disabled={filters.page + 1 >= data.totalPages} onClick={() => setFilters((current) => ({ ...current, page: current.page + 1 }))}>Trang sau</button>
                </div>
              )}
            </>
          )}
        </div>
      </OwnerCard>
      {creating && <SettlementDialog owners={owners} onClose={() => setCreating(false)} onCreated={(created, message) => { setCreating(false); setNotice(message); setFilters(EMPTY_FILTERS); setQuery(''); reload() }} />}
      {openId && <SettlementDrawer settlementId={openId} onClose={() => setOpenId(null)} onOpenBooking={(id) => setCommissionId(id)} onChanged={reload} refreshKey={refreshKey} escapeDisabled={Boolean(commissionId)} />}
      {commissionId && <CommissionDetailDrawer commissionId={commissionId} onClose={() => setCommissionId(null)} onChanged={() => { setRefreshKey((current) => current + 1); reload() }} />}
    </AdminLayout>
  )
}

export default AdminSettlementsPage
