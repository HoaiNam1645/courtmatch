import { useState } from 'react'
import { CheckCircle2, Eye, Lock, LockOpen, Pencil, Plus, Search, X } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { fetchAdminVoucher, fetchAdminVouchers, lockAdminVoucher, unlockAdminVoucher, VOUCHER_STATE_TABS, voucherStateMeta } from '../../../api/adminVouchers'
import { useApiResource } from '../../../hooks/useApiResource'
import { useDebounced } from '../../../hooks/useDebounced'
import { classNames, formatCurrency } from '../../../shared/utils'
import { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading } from '../../owner/components/OwnerUi'
import { formatDateTime } from '../../owner/ownerDates'
import { primaryButtonClass, secondaryButtonClass } from '../../owner/ownerStyles'
import { AdminLayout } from '../components/AdminLayout'
import { VoucherDialog } from '../components/VoucherDialog'
import { VoucherDrawer } from '../components/VoucherDrawer'

const smallButton = `${secondaryButtonClass} inline-flex h-9 items-center gap-1.5 px-3 text-xs`

function conditionText(voucher) {
  const parts = []
  if (voucher.minElo != null) parts.push(`Elo ≥ ${voucher.minElo.toLocaleString('vi-VN')}`)
  if (voucher.minBookingMatches != null) parts.push(`≥ ${voucher.minBookingMatches} trận có đặt sân`)
  if (voucher.minActiveWeeks != null) parts.push(`${voucher.minActiveWeeks} tuần hoạt động liên tiếp`)
  return parts.length === 0 ? 'Không yêu cầu điều kiện' : parts.join(' · ')
}

/** B4 — quản lý voucher giảm giá: tạo, sửa, khóa và theo dõi chi phí Courtly chịu. */
function AdminVouchersPage() {
  const [tab, setTab] = useState('all')
  const [query, setQuery] = useState('')
  const [viewingId, setViewingId] = useState(null)
  const [dialog, setDialog] = useState(null)
  const [notice, setNotice] = useState('')
  const [actionError, setActionError] = useState('')
  const q = useDebounced(query)
  const { status, data, error, reload } = useApiResource(() => fetchAdminVouchers({ q }), [q], { isEmpty: (result) => result.vouchers.length === 0 && !q })
  const all = data?.vouchers ?? []
  const counts = Object.fromEntries(VOUCHER_STATE_TABS.map((state) => [state, state === 'all' ? all.length : all.filter((item) => item.state === state).length]))
  const visible = tab === 'all' ? all : all.filter((item) => item.state === tab)

  async function toggleLock(voucher) {
    setActionError('')
    try {
      if (voucher.status === 'locked') await unlockAdminVoucher(voucher.id)
      else await lockAdminVoucher(voucher.id)
      setNotice(voucher.status === 'locked' ? `Đã mở khóa voucher ${voucher.code}.` : `Đã khóa voucher ${voucher.code}. Người chơi không dùng được thêm.`)
      reload()
    } catch (failure) {
      setActionError(failure instanceof ApiError ? failure.message : 'Đã có lỗi xảy ra, vui lòng thử lại.')
      reload()
    }
  }

  async function edit(voucher) {
    setActionError('')
    try {
      setDialog(await fetchAdminVoucher(voucher.id))
    } catch (failure) {
      setActionError(failure instanceof ApiError ? failure.message : 'Không tải được voucher.')
    }
  }

  return (
    <AdminLayout
      eyebrow="B4 · Voucher"
      title="Voucher giảm giá"
      description="Voucher giảm theo phần trăm cho người chơi. Courtly chịu phần giảm: chủ sân vẫn nhận đủ giá sân và hoa hồng tính trên giá gốc."
      actions={<button type="button" className={`${primaryButtonClass} inline-flex items-center gap-2`} data-testid="voucher-create-open" onClick={() => setDialog('new')}><Plus className="size-4" />Tạo voucher</button>}
    >
      {notice && <p role="status" className="mb-5 flex items-start gap-2 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-semibold text-emerald-800" data-testid="admin-notice"><CheckCircle2 className="mt-0.5 size-4 shrink-0" />{notice}</p>}
      {actionError && <p role="alert" className="mb-5 rounded-xl border border-red-200 bg-red-50 p-4 text-sm font-semibold text-red-800" data-testid="voucher-action-error">{actionError}</p>}
      <OwnerCard>
        {data && (
          <div className="mb-4 grid gap-3 rounded-xl bg-slate-50 p-4 sm:grid-cols-3" data-testid="voucher-totals">
            <div><p className="text-xs font-semibold text-slate-500">Số voucher</p><p className="text-lg font-semibold text-slate-900" data-testid="voucher-total-count">{data.totals.voucherCount}</p></div>
            <div><p className="text-xs font-semibold text-slate-500">Lượt đang dùng</p><p className="text-lg font-semibold text-slate-900" data-testid="voucher-total-used">{data.totals.redemptions.toLocaleString('vi-VN')}</p></div>
            <div><p className="text-xs font-semibold text-slate-500">Chi phí Courtly đã chịu</p><p className="text-lg font-semibold text-amber-800" data-testid="voucher-total-cost">{formatCurrency(data.totals.totalDiscount)}</p></div>
          </div>
        )}
        <div className="flex gap-2 overflow-x-auto pb-1" role="tablist" aria-label="Trạng thái voucher">
          {VOUCHER_STATE_TABS.map((state) => (
            <button key={state} type="button" role="tab" aria-selected={tab === state} data-testid={`voucher-tab-${state}`} className={classNames('flex h-10 shrink-0 items-center gap-2 rounded-lg px-4 text-sm font-semibold', tab === state ? 'bg-slate-800 text-white' : 'bg-slate-100 text-slate-600')} onClick={() => setTab(state)}>
              {state === 'all' ? 'Tất cả' : voucherStateMeta(state).label}
              <span className={classNames('rounded-full px-2 text-xs font-bold', tab === state ? 'bg-white/20' : 'bg-slate-200')}>{counts[state]}</span>
            </button>
          ))}
        </div>
        <label className="mt-4 flex h-11 items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 focus-within:border-slate-700">
          <Search className="size-4 shrink-0 text-slate-400" />
          <input className="min-w-0 flex-1 text-sm outline-none placeholder:text-slate-400" aria-label="Tìm voucher" placeholder="Mã hoặc tên voucher" value={query} maxLength={100} onChange={(event) => setQuery(event.target.value)} />
          {query && <button type="button" aria-label="Xóa tìm kiếm" onClick={() => setQuery('')}><X className="size-4 text-slate-400" /></button>}
        </label>

        <div className="mt-5">
          {status === 'loading' && !data && <OwnerLoading rows={3} />}
          {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
          {status === 'empty' && <OwnerEmpty title="Chưa có voucher nào" description="Tạo voucher đầu tiên để tặng ưu đãi cho người chơi." />}
          {data && status !== 'empty' && visible.length === 0 && <OwnerEmpty title="Không có voucher phù hợp" description="Thử đổi tab trạng thái hoặc từ khóa." />}
          {data && visible.length > 0 && (
            <ul className="grid gap-3" data-testid="voucher-list">
              {visible.map((voucher) => {
                const meta = voucherStateMeta(voucher.state)
                return (
                  <li key={voucher.id} data-testid={`voucher-${voucher.code}`} className="rounded-xl border border-slate-200 p-4">
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', meta.className)} data-testid="voucher-state">{meta.label}</span>
                          <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-700" data-testid="voucher-code-badge">{voucher.code}</span>
                        </div>
                        <p className="mt-2 text-sm font-semibold text-slate-900">{voucher.name}</p>
                      </div>
                      <p className="text-base font-semibold text-slate-900" data-testid="voucher-discount">Giảm {voucher.discountPercent.toLocaleString('vi-VN')}%{voucher.maxDiscountAmount != null ? ` (tối đa ${formatCurrency(voucher.maxDiscountAmount)})` : ''}</p>
                    </div>
                    <p className="mt-1 text-xs font-medium text-slate-500">{formatDateTime(voucher.validFrom)} → {formatDateTime(voucher.validUntil)} · {conditionText(voucher)}</p>
                    <div className="mt-3 flex flex-wrap items-center justify-between gap-3">
                      <p className="text-xs font-semibold text-slate-600" data-testid="voucher-usage">Đã dùng {voucher.usedCount}{voucher.totalLimit != null ? `/${voucher.totalLimit}` : ''} lượt · Courtly chịu <span className="text-amber-800">{formatCurrency(voucher.totalDiscount)}</span></p>
                      <div className="flex flex-wrap gap-2">
                        <button type="button" className={smallButton} aria-label={`Xem voucher ${voucher.code}`} data-testid="voucher-view" onClick={() => setViewingId(voucher.id)}><Eye className="size-3.5" />Chi tiết</button>
                        <button type="button" className={smallButton} aria-label={`Sửa voucher ${voucher.code}`} data-testid="voucher-edit" onClick={() => edit(voucher)}><Pencil className="size-3.5" />Sửa</button>
                        <button type="button" className={smallButton} aria-label={`${voucher.status === 'locked' ? 'Mở khóa' : 'Khóa'} voucher ${voucher.code}`} data-testid="voucher-lock" onClick={() => toggleLock(voucher)}>{voucher.status === 'locked' ? <><LockOpen className="size-3.5" />Mở khóa</> : <><Lock className="size-3.5" />Khóa</>}</button>
                      </div>
                    </div>
                  </li>
                )
              })}
            </ul>
          )}
        </div>
      </OwnerCard>
      {viewingId && <VoucherDrawer voucherId={viewingId} onClose={() => setViewingId(null)} />}
      {dialog && <VoucherDialog voucher={dialog === 'new' ? null : dialog} onClose={() => setDialog(null)} onSaved={(saved, message) => { setDialog(null); setNotice(message); reload() }} />}
    </AdminLayout>
  )
}

export default AdminVouchersPage
