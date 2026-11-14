import { useEffect, useState } from 'react'
import { CheckCircle2, CircleAlert, X } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { approvalMeta, approveAdminVenue, fetchAdminVenue } from '../../../api/adminVenues'
import { useApiResource } from '../../../hooks/useApiResource'
import { FormAlert } from '../../../shared/FormFeedback'
import { classNames, formatCurrency } from '../../../shared/utils'
import { dayLabel } from '../../owner/dayLabels'
import { OwnerError, OwnerLoading } from '../../owner/components/OwnerUi'
import { formatDateTime } from '../../owner/ownerDates'
import { primaryButtonClass } from '../../owner/ownerStyles'

/** Chi tiết sân để quản trị viên duyệt (2.3.34). */
function VenueReviewDrawer({ venueId, onClose, onChanged }) {
  const { status, data, error, reload } = useApiResource(() => fetchAdminVenue(venueId), [venueId])
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState('')

  useEffect(() => {
    function onKey(event) {
      if (event.key === 'Escape' && !busy) onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose, busy])

  async function approve() {
    if (busy) return
    setBusy(true)
    setFormError('')
    try {
      const updated = await approveAdminVenue(venueId)
      reload()
      onChanged(`Đã duyệt sân ${updated.name}. Sân đã hiển thị cho người chơi.`)
    } catch (failure) {
      if (failure instanceof ApiError) {
        setFormError(failure.fieldErrors?.checks ? `${failure.message} Còn thiếu: ${failure.fieldErrors.checks}` : failure.message)
        if (failure.status === 409) reload()
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
    } finally {
      setBusy(false)
    }
  }

  const meta = data ? approvalMeta(data.approvalStatus) : null
  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/40" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose() }}>
      <aside role="dialog" aria-modal="true" aria-label="Chi tiết sân cần duyệt" className="h-full w-full max-w-lg overflow-y-auto bg-white p-5 shadow-2xl" data-testid="venue-drawer">
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-lg font-semibold text-slate-900">Chi tiết sân</h2>
          <button type="button" className="grid size-9 place-items-center rounded-lg text-slate-500 hover:bg-slate-100" aria-label="Đóng" onClick={onClose} disabled={busy}><X className="size-5" /></button>
        </div>
        {status === 'loading' && <div className="mt-4"><OwnerLoading rows={2} /></div>}
        {status === 'error' && <div className="mt-4"><OwnerError message={error?.message} onRetry={reload} /></div>}
        {status === 'success' && (
          <div className="mt-4 grid gap-5">
            <section>
              <span className={classNames('inline-flex rounded-full px-2.5 py-1 text-xs font-bold', meta.className)} data-testid="venue-drawer-status">{meta.label}</span>
              <p className="mt-2 text-base font-semibold text-slate-900">{data.name}</p>
              <p className="text-sm font-medium text-slate-600">{[data.address, data.ward, data.district].filter(Boolean).join(', ')}</p>
              {data.description && <p className="mt-2 text-sm text-slate-500">{data.description}</p>}
              <p className="mt-2 text-xs font-medium text-slate-500">Điện thoại: {data.phone || 'Chưa có'} · Tọa độ: {data.latitude}, {data.longitude}</p>
              {data.services.length > 0 && <p className="mt-1 text-xs font-medium text-slate-500">Tiện ích: {data.services.join(', ')}</p>}
              <p className="mt-2 text-sm font-medium text-slate-700">Chủ sân: <span className="font-semibold">{data.owner.fullName}</span> · {data.owner.email} · {data.owner.phone}</p>
              {data.approvedBy && <p className="mt-1 text-xs font-medium text-emerald-700" data-testid="venue-approved-by">Duyệt bởi {data.approvedBy} lúc {formatDateTime(data.approvedAt)}</p>}
            </section>

            {data.images.length > 0 && (
              <section>
                <h3 className="text-sm font-semibold text-slate-900">Hình ảnh ({data.images.length})</h3>
                <ul className="mt-2 grid grid-cols-3 gap-2">
                  {data.images.map((image) => <li key={image.url} className="aspect-video overflow-hidden rounded-lg bg-slate-100"><img src={image.url} alt={image.caption || data.name} className="size-full object-cover" /></li>)}
                </ul>
              </section>
            )}

            <section>
              <h3 className="text-sm font-semibold text-slate-900">Sân con và giá</h3>
              <ul className="mt-2 grid gap-2" data-testid="venue-courts">
                {data.courtPrices.map((court) => (
                  <li key={court.courtId} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-slate-200 p-3 text-sm">
                    <span className="font-semibold text-slate-800">{court.name} ({court.courtCode}) · {court.status === 'active' ? 'Hoạt động' : court.status === 'maintenance' ? 'Bảo trì' : 'Ngừng'}</span>
                    <span className="text-xs font-medium text-slate-500">{court.activeRules === 0 ? 'Chưa có giá' : `${court.activeRules} quy tắc · ${formatCurrency(court.minPrice)}${court.maxPrice !== court.minPrice ? ` – ${formatCurrency(court.maxPrice)}` : ''}/giờ`}</span>
                  </li>
                ))}
              </ul>
            </section>

            <section>
              <h3 className="text-sm font-semibold text-slate-900">Giờ hoạt động</h3>
              <ul className="mt-2 grid gap-1 text-sm font-medium text-slate-600" data-testid="venue-hours">
                {data.hours.map((hours) => <li key={hours.dayOfWeek}>{dayLabel(hours.dayOfWeek)}: {hours.closed ? 'Nghỉ' : `${hours.openTime?.slice(0, 5)} – ${hours.closeTime?.slice(0, 5)}`}</li>)}
                {data.hours.length === 0 && <li className="text-slate-500">Chưa khai báo giờ hoạt động.</li>}
              </ul>
            </section>

            <section data-testid="venue-checks">
              <h3 className="text-sm font-semibold text-slate-900">Điều kiện để hiển thị</h3>
              <ul className="mt-2 grid gap-1.5">
                {data.checks.map((check) => (
                  <li key={check.code} className={classNames('flex items-start gap-2 text-sm font-medium', check.ok ? 'text-emerald-700' : 'text-red-700')} data-testid={`venue-check-${check.code}`}>
                    {check.ok ? <CheckCircle2 className="mt-0.5 size-4 shrink-0" /> : <CircleAlert className="mt-0.5 size-4 shrink-0" />}{check.label}
                  </li>
                ))}
              </ul>
            </section>

            <section>
              <FormAlert message={formError} />
              {data.approvalStatus === 'pending' && (
                <div className="mt-2 flex flex-wrap gap-2">
                  <button type="button" className={primaryButtonClass} data-testid="venue-approve" disabled={busy || !data.canApprove} onClick={approve}>{busy ? 'Đang duyệt...' : 'Duyệt sân'}</button>
                </div>
              )}
              {data.approvalStatus === 'pending' && !data.canApprove && <p className="mt-2 text-xs font-medium text-red-700" data-testid="venue-cannot-approve">Sân chưa đủ điều kiện nên chưa thể duyệt.</p>}
            </section>
          </div>
        )}
      </aside>
    </div>
  )
}

export { VenueReviewDrawer }
