import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { fetchAdminPlayer, lockAdminPlayer, SKILL_LABEL, unlockAdminPlayer } from '../../../api/adminPlayers'
import { userStatusMeta } from '../../../api/adminUsers'
import { bookingStatusMeta } from '../../../api/ownerBookingRequests'
import { useApiResource } from '../../../hooks/useApiResource'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { classNames, formatCurrency } from '../../../shared/utils'
import { OwnerError, OwnerLoading } from '../../owner/components/OwnerUi'
import { formatDateTime, formatIsoDate } from '../../owner/ownerDates'
import { dangerButtonClass, primaryButtonClass, secondaryButtonClass, textareaClass } from '../../owner/ownerStyles'

/** Chi tiết người chơi: hồ sơ, booking, báo cáo; khóa hoặc mở khóa (2.3.31). */
function PlayerDetailDrawer({ playerId, onClose, onChanged }) {
  const { status, data, error, reload } = useApiResource(() => fetchAdminPlayer(playerId), [playerId])
  const [locking, setLocking] = useState(false)
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState('')
  const [formError, setFormError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    function onKey(event) {
      if (event.key === 'Escape' && !busy) onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose, busy])

  async function run(action, message) {
    if (busy) return
    setBusy(true)
    setFormError('')
    try {
      await action()
      setLocking(false)
      setReason('')
      reload()
      onChanged(message)
    } catch (failure) {
      if (failure instanceof ApiError) {
        setReasonError(failure.fieldErrors?.reason ?? '')
        setFormError(failure.message)
        if (failure.status === 409) reload()
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
    } finally {
      setBusy(false)
    }
  }

  function submitLock(event) {
    event.preventDefault()
    const trimmed = reason.trim()
    if (trimmed.length < 3) return setReasonError('Vui lòng nhập lý do (ít nhất 3 ký tự)')
    if (trimmed.length > 500) return setReasonError('Lý do tối đa 500 ký tự')
    setReasonError('')
    return run(() => lockAdminPlayer(playerId, trimmed), `Đã khóa tài khoản ${data.fullName}.`)
  }

  const meta = data ? userStatusMeta(data.status) : null
  const canLock = data && ['active', 'inactive'].includes(data.status)
  const canUnlock = data && ['banned', 'inactive'].includes(data.status)
  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/40" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose() }}>
      <aside role="dialog" aria-modal="true" aria-label="Chi tiết người chơi" className="h-full w-full max-w-md overflow-y-auto bg-white p-5 shadow-2xl" data-testid="player-drawer">
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-lg font-semibold text-slate-900">Chi tiết người chơi</h2>
          <button type="button" className="grid size-9 place-items-center rounded-lg text-slate-500 hover:bg-slate-100" aria-label="Đóng" onClick={onClose} disabled={busy}><X className="size-5" /></button>
        </div>
        {status === 'loading' && <div className="mt-4"><OwnerLoading rows={2} /></div>}
        {status === 'error' && <div className="mt-4"><OwnerError message={error?.message} onRetry={reload} /></div>}
        {status === 'success' && (
          <div className="mt-4 grid gap-5">
            <section>
              <span className={classNames('inline-flex rounded-full px-2.5 py-1 text-xs font-bold', meta.className)} data-testid="player-drawer-status">{meta.label}</span>
              <p className="mt-2 text-base font-semibold text-slate-900">{data.fullName}</p>
              <dl className="mt-2 grid gap-1 text-sm font-medium text-slate-600">
                <div className="break-all">Email: {data.email || 'Chưa có'}</div>
                <div>Số điện thoại: {data.phone || 'Chưa có'}</div>
                <div>Trình độ: {data.skillLevel ? SKILL_LABEL[data.skillLevel] : 'Chưa khai báo'}{data.skillScore !== null ? ` · ${data.skillScore} điểm` : ''}</div>
                {data.dateOfBirth && <div>Ngày sinh: {formatIsoDate(data.dateOfBirth)}</div>}
                <div>Đăng ký: {formatDateTime(data.createdAt)} · đăng nhập gần nhất: {data.lastLoginAt ? formatDateTime(data.lastLoginAt) : 'chưa đăng nhập'}</div>
              </dl>
              {data.lastStatusChange && (
                <p className="mt-3 rounded-lg bg-slate-50 px-3 py-2 text-xs font-medium text-slate-600" data-testid="player-drawer-last-change">
                  Lần đổi trạng thái gần nhất: {userStatusMeta(data.lastStatusChange.status).label} · {formatDateTime(data.lastStatusChange.at)}{data.lastStatusChange.actor ? ` · ${data.lastStatusChange.actor}` : ''}{data.lastStatusChange.reason ? ` · Lý do: ${data.lastStatusChange.reason}` : ''}
                </p>
              )}
            </section>

            <section className="grid grid-cols-3 gap-2 text-center">
              <div className="rounded-lg border border-slate-200 p-3"><p className="text-lg font-semibold text-slate-900" data-testid="player-booking-count">{data.bookingCount}</p><p className="text-xs font-medium text-slate-500">Booking</p></div>
              <div className="rounded-lg border border-slate-200 p-3"><p className="text-lg font-semibold text-slate-900" data-testid="player-match-count">{data.matchCount}</p><p className="text-xs font-medium text-slate-500">Trận đã chơi</p></div>
              <div className={classNames('rounded-lg border p-3', data.reportCount > 0 ? 'border-red-200 bg-red-50' : 'border-slate-200')}><p className="text-lg font-semibold text-slate-900" data-testid="player-report-count">{data.reportCount}</p><p className="text-xs font-medium text-slate-500">Bị báo cáo</p></div>
            </section>

            <section>
              <h3 className="text-sm font-semibold text-slate-900">Booking gần đây</h3>
              {data.recentBookings.length === 0
                ? <p className="mt-2 text-sm font-medium text-slate-500" data-testid="player-no-bookings">Người chơi chưa đặt sân nào.</p>
                : (
                  <ul className="mt-2 grid gap-2" data-testid="player-bookings">
                    {data.recentBookings.map((item) => (
                      <li key={item.id} className="rounded-lg border border-slate-200 p-3 text-sm">
                        <div className="flex items-center justify-between gap-2"><span className="text-xs font-bold text-slate-400">{item.bookingCode}</span><span className="font-semibold text-slate-800">{formatCurrency(item.totalAmount)}</span></div>
                        <p className="mt-1 font-semibold text-slate-900">{item.venueName}</p>
                        <p className="text-xs font-medium text-slate-500">{bookingStatusMeta(item.status).label} · {formatDateTime(item.createdAt)}</p>
                      </li>
                    ))}
                  </ul>
                )}
            </section>

            <section>
              <FormAlert message={locking ? '' : formError} />
              {!locking && (
                <div className="mt-2 flex flex-wrap gap-2">
                  {canLock && <button type="button" className={dangerButtonClass} data-testid="player-lock" onClick={() => { setLocking(true); setReason(''); setReasonError(''); setFormError('') }}>Khóa tài khoản</button>}
                  {canUnlock && <button type="button" className={primaryButtonClass} data-testid="player-unlock" disabled={busy} onClick={() => run(() => unlockAdminPlayer(playerId), `Đã mở khóa tài khoản ${data.fullName}.`)}>{busy ? 'Đang xử lý...' : 'Mở khóa'}</button>}
                </div>
              )}
              {locking && (
                <form className="grid gap-3" noValidate onSubmit={submitLock} data-testid="player-lock-form">
                  <p className="text-xs font-medium text-slate-500">Người chơi bị khóa không đăng nhập hay đặt sân mới được. Booking đã xác nhận không bị tự động hủy.</p>
                  <label className="block">
                    <span className="text-sm font-semibold text-slate-900">Lý do khóa <span className="text-red-600">*</span></span>
                    <textarea className={`${textareaClass} mt-2 h-24`} aria-label="Lý do khóa" maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} aria-invalid={Boolean(reasonError)} />
                    <FieldError message={reasonError} />
                  </label>
                  <FormAlert message={formError} />
                  <div className="flex justify-end gap-2">
                    <button type="button" className={secondaryButtonClass} disabled={busy} onClick={() => setLocking(false)}>Hủy</button>
                    <button type="submit" className={dangerButtonClass} disabled={busy}>{busy ? 'Đang khóa...' : 'Xác nhận khóa'}</button>
                  </div>
                </form>
              )}
            </section>
          </div>
        )}
      </aside>
    </div>
  )
}

export { PlayerDetailDrawer }
