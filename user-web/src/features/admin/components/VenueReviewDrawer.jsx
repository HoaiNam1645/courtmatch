import { useEffect, useState } from 'react'
import { CheckCircle2, CircleAlert, X } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { approvalMeta, approveAdminVenue, fetchAdminVenue, fetchVenueRejectReasons, rejectAdminVenue } from '../../../api/adminVenues'
import { useApiResource } from '../../../hooks/useApiResource'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { classNames, formatCurrency } from '../../../shared/utils'
import { dayLabel } from '../../owner/dayLabels'
import { OwnerError, OwnerLoading } from '../../owner/components/OwnerUi'
import { formatDateTime } from '../../owner/ownerDates'
import { dangerButtonClass, inputClass, primaryButtonClass, secondaryButtonClass, textareaClass } from '../../owner/ownerStyles'

/** Hộp từ chối sân: chọn lý do trong danh mục, ghi chú thêm (2.3.35). */
function RejectForm({ venueId, onRejected, onCancel }) {
  const reasons = useApiResource(fetchVenueRejectReasons, [])
  const [reasonCode, setReasonCode] = useState('')
  const [note, setNote] = useState('')
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [busy, setBusy] = useState(false)
  const selected = reasons.data?.find((reason) => reason.code === reasonCode)

  async function submit(event) {
    event.preventDefault()
    if (busy) return
    const next = {}
    if (!reasonCode) next.reasonCode = 'Vui lòng chọn lý do từ chối'
    if (selected?.noteRequired && note.trim().length < 3) next.note = 'Vui lòng nhập ghi chú (ít nhất 3 ký tự) khi chọn lý do khác'
    if (note.length > 500) next.note = 'Ghi chú tối đa 500 ký tự'
    setErrors(next)
    setFormError('')
    if (Object.keys(next).length > 0) return
    setBusy(true)
    try {
      onRejected(await rejectAdminVenue(venueId, { reasonCode, note }))
    } catch (failure) {
      if (failure instanceof ApiError) {
        setErrors(failure.fieldErrors ?? {})
        setFormError(failure.message)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
      setBusy(false)
    }
  }

  return (
    <form className="grid gap-3" noValidate onSubmit={submit} data-testid="venue-reject-form">
      {reasons.status === 'loading' && <OwnerLoading rows={1} />}
      {reasons.status === 'error' && <OwnerError message={reasons.error?.message} onRetry={reasons.reload} />}
      {reasons.status === 'success' && (
        <>
          <label className="block">
            <span className="text-sm font-semibold text-slate-900">Lý do từ chối <span className="text-red-600">*</span></span>
            <select className={`${inputClass} mt-2`} aria-label="Lý do từ chối sân" value={reasonCode} onChange={(event) => setReasonCode(event.target.value)} aria-invalid={Boolean(errors.reasonCode)}>
              <option value="">Chọn lý do</option>
              {reasons.data.map((reason) => <option key={reason.code} value={reason.code}>{reason.label}</option>)}
            </select>
            <FieldError message={errors.reasonCode} />
          </label>
          <label className="block">
            <span className="text-sm font-semibold text-slate-900">Ghi chú{selected?.noteRequired ? <span className="text-red-600"> *</span> : ' (không bắt buộc)'}</span>
            <textarea className={`${textareaClass} mt-2 h-24`} aria-label="Ghi chú từ chối sân" maxLength={500} value={note} onChange={(event) => setNote(event.target.value)} aria-invalid={Boolean(errors.note)} />
            <FieldError message={errors.note} />
          </label>
          <FormAlert message={formError} />
          <div className="flex justify-end gap-2">
            <button type="button" className={secondaryButtonClass} disabled={busy} onClick={onCancel}>Hủy</button>
            <button type="submit" className={dangerButtonClass} disabled={busy}>{busy ? 'Đang từ chối...' : 'Từ chối sân'}</button>
          </div>
        </>
      )}
    </form>
  )
}

/** Chi tiết sân để quản trị viên duyệt (2.3.34) hoặc từ chối (2.3.35). */
function VenueReviewDrawer({ venueId, onClose, onChanged }) {
  const { status, data, error, reload } = useApiResource(() => fetchAdminVenue(venueId), [venueId])
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState('')
  const [rejecting, setRejecting] = useState(false)

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
              {data.rejection && (
                <p className="mt-2 rounded-lg bg-red-50 px-3 py-2 text-xs font-semibold text-red-800" data-testid="venue-rejection">
                  Từ chối{data.rejection.rejectedBy ? ` bởi ${data.rejection.rejectedBy}` : ''} lúc {formatDateTime(data.rejection.rejectedAt)}. Lý do: {data.rejection.reason}
                </p>
              )}
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
              <FormAlert message={rejecting ? '' : formError} />
              {data.approvalStatus === 'pending' && !rejecting && (
                <div className="mt-2 flex flex-wrap gap-2">
                  <button type="button" className={primaryButtonClass} data-testid="venue-approve" disabled={busy || !data.canApprove} onClick={approve}>{busy ? 'Đang duyệt...' : 'Duyệt sân'}</button>
                  <button type="button" className={dangerButtonClass} data-testid="venue-reject-open" disabled={busy} onClick={() => { setRejecting(true); setFormError('') }}>Từ chối sân</button>
                </div>
              )}
              {rejecting && (
                <RejectForm
                  venueId={venueId}
                  onCancel={() => setRejecting(false)}
                  onRejected={(updated) => { setRejecting(false); reload(); onChanged(`Đã từ chối sân ${updated.name}. Chủ sân sẽ thấy lý do.`) }}
                />
              )}
              {data.approvalStatus === 'pending' && !rejecting && !data.canApprove && <p className="mt-2 text-xs font-medium text-red-700" data-testid="venue-cannot-approve">Sân chưa đủ điều kiện nên chưa thể duyệt.</p>}
            </section>
          </div>
        )}
      </aside>
    </div>
  )
}

export { VenueReviewDrawer }
