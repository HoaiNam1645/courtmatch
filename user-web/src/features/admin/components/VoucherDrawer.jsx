import { useEffect } from 'react'
import { X } from 'lucide-react'
import { fetchAdminVoucher, voucherStateMeta } from '../../../api/adminVouchers'
import { useApiResource } from '../../../hooks/useApiResource'
import { classNames, formatCurrency } from '../../../shared/utils'
import { OwnerError, OwnerLoading } from '../../owner/components/OwnerUi'
import { formatDateTime } from '../../owner/ownerDates'

/** Chi tiết voucher: cấu hình, chi phí Courtly đã chịu và các lượt dùng (B4). */
function VoucherDrawer({ voucherId, onClose }) {
  const { status, data, error, reload } = useApiResource(() => fetchAdminVoucher(voucherId), [voucherId])

  useEffect(() => {
    function onKey(event) {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const meta = data ? voucherStateMeta(data.state) : null
  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/40" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
      <aside role="dialog" aria-modal="true" aria-label="Chi tiết voucher" className="h-full w-full max-w-lg overflow-y-auto bg-white p-5 shadow-2xl" data-testid="voucher-drawer">
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-lg font-semibold text-slate-900">Chi tiết voucher</h2>
          <button type="button" className="grid size-9 place-items-center rounded-lg text-slate-500 hover:bg-slate-100" aria-label="Đóng" onClick={onClose}><X className="size-5" /></button>
        </div>
        {status === 'loading' && <div className="mt-4"><OwnerLoading rows={2} /></div>}
        {status === 'error' && <div className="mt-4"><OwnerError message={error?.message} onRetry={reload} /></div>}
        {status === 'success' && (
          <div className="mt-4 grid gap-5">
            <section>
              <div className="flex flex-wrap items-center gap-2">
                <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', meta.className)} data-testid="voucher-drawer-state">{meta.label}</span>
                <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-700">{data.code}</span>
              </div>
              <p className="mt-2 text-base font-semibold text-slate-900">{data.name}</p>
              {data.description && <p className="mt-1 text-sm font-medium text-slate-600">{data.description}</p>}
              <p className="mt-2 text-xs font-medium text-slate-500">Hiệu lực {formatDateTime(data.validFrom)} → {formatDateTime(data.validUntil)}{data.createdBy ? ` · tạo bởi ${data.createdBy}` : ''}</p>
            </section>
            <section className="grid gap-3 sm:grid-cols-3">
              <div className="rounded-xl bg-slate-50 p-3"><p className="text-xs font-semibold text-slate-500">Lượt đang dùng</p><p className="mt-1 text-lg font-semibold text-slate-900" data-testid="voucher-drawer-used">{data.usedCount}{data.totalLimit != null ? ` / ${data.totalLimit}` : ''}</p></div>
              <div className="rounded-xl bg-slate-50 p-3"><p className="text-xs font-semibold text-slate-500">Mức giảm</p><p className="mt-1 text-lg font-semibold text-slate-900">{data.discountPercent.toLocaleString('vi-VN')}%</p>{data.maxDiscountAmount != null && <p className="text-xs font-medium text-slate-500">tối đa {formatCurrency(data.maxDiscountAmount)}</p>}</div>
              <div className="rounded-xl bg-amber-50 p-3"><p className="text-xs font-semibold text-amber-800">Courtly đã chịu</p><p className="mt-1 text-lg font-semibold text-amber-900" data-testid="voucher-drawer-cost">{formatCurrency(data.totalDiscount)}</p></div>
            </section>
            <section>
              <h3 className="text-sm font-semibold text-slate-900">Điều kiện nhận</h3>
              <ul className="mt-2 grid gap-1 text-sm font-medium text-slate-700" data-testid="voucher-drawer-conditions">
                {data.minElo == null && data.minBookingMatches == null && data.minActiveWeeks == null && <li className="text-slate-500">Không yêu cầu điều kiện.</li>}
                {data.minElo != null && <li>Elo từ {data.minElo.toLocaleString('vi-VN')}</li>}
                {data.minBookingMatches != null && <li>Ít nhất {data.minBookingMatches} trận có đặt sân</li>}
                {data.minActiveWeeks != null && <li>Hoạt động {data.minActiveWeeks} tuần liên tiếp</li>}
              </ul>
            </section>
            <section>
              <h3 className="text-sm font-semibold text-slate-900">Lượt dùng ({data.redemptions.length})</h3>
              {data.redemptions.length === 0 ? <p className="mt-2 text-sm font-medium text-slate-500" data-testid="voucher-drawer-empty">Chưa có ai dùng voucher này.</p> : (
                <ul className="mt-2 grid gap-2" data-testid="voucher-drawer-redemptions">
                  {data.redemptions.map((item) => (
                    <li key={item.id} className="rounded-lg border border-slate-200 p-3 text-sm">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <span className="font-semibold text-slate-800">{item.userName} · {item.bookingCode}</span>
                        <span className={classNames('rounded-full px-2 py-0.5 text-xs font-bold', item.status === 'applied' ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-200 text-slate-600')} data-testid="voucher-redemption-status">{item.status === 'applied' ? 'Đang dùng' : 'Đã trả lại'}</span>
                      </div>
                      <p className="mt-1 text-xs font-medium text-slate-500">Giảm {formatCurrency(item.discountAmount)} trên {formatCurrency(item.originalAmount)} · {formatDateTime(item.createdAt)}{item.releasedAt ? ` · trả lại ${formatDateTime(item.releasedAt)}` : ''}</p>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>
        )}
      </aside>
    </div>
  )
}

export { VoucherDrawer }
