import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { assignAdminRole, changeAdminUserStatus, fetchAdminRoles, fetchAdminUser, removeAdminRole, ROLE_LABEL, userStatusMeta } from '../../../api/adminUsers'
import { useApiResource } from '../../../hooks/useApiResource'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { classNames } from '../../../shared/utils'
import { OwnerError, OwnerLoading } from '../../owner/components/OwnerUi'
import { formatDateTime } from '../../owner/ownerDates'
import { dangerButtonClass, inputClass, primaryButtonClass, secondaryButtonClass, textareaClass } from '../../owner/ownerStyles'

const ACTIONS = {
  banned: { label: 'Khóa tài khoản', needsReason: true, className: dangerButtonClass },
  inactive: { label: 'Tạm ngừng', needsReason: true, className: secondaryButtonClass },
  active: { label: 'Kích hoạt lại', needsReason: false, className: primaryButtonClass },
}

/** Gán hoặc gỡ vai trò của tài khoản (2.3.29). */
function RolesSection({ user, onChanged }) {
  const roles = useApiResource(fetchAdminRoles, [])
  const [selected, setSelected] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function run(action, message) {
    if (busy) return
    setBusy(true)
    setError('')
    try {
      await action()
      setSelected('')
      onChanged(message)
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : 'Đã có lỗi xảy ra, vui lòng thử lại.')
    } finally {
      setBusy(false)
    }
  }

  const available = (roles.data ?? []).filter((role) => !user.roles.includes(role.code))
  return (
    <section data-testid="user-roles">
      <h3 className="text-sm font-semibold text-slate-900">Vai trò</h3>
      <ul className="mt-2 flex flex-wrap gap-2">
        {user.roles.map((code) => (
          <li key={code} className="flex items-center gap-1 rounded-full bg-sky-100 py-1 pl-3 pr-1 text-xs font-bold text-sky-800" data-testid={`role-chip-${code}`}>
            {ROLE_LABEL[code] ?? code}
            <button type="button" className="grid size-6 place-items-center rounded-full hover:bg-sky-200 disabled:opacity-40" aria-label={`Gỡ vai trò ${ROLE_LABEL[code] ?? code}`} disabled={busy || user.roles.length <= 1}
              onClick={() => run(() => removeAdminRole(user.id, code), `Đã gỡ vai trò ${ROLE_LABEL[code] ?? code} của ${user.fullName}.`)}><X className="size-3.5" /></button>
          </li>
        ))}
      </ul>
      {roles.status === 'error' && <p className="mt-2 text-xs font-medium text-red-600">Không tải được danh mục vai trò.</p>}
      {available.length > 0 && (
        <div className="mt-3 flex gap-2">
          <select className={inputClass} aria-label="Chọn vai trò để gán" value={selected} onChange={(event) => setSelected(event.target.value)}>
            <option value="">Chọn vai trò để gán</option>
            {available.map((role) => <option key={role.code} value={role.code}>{ROLE_LABEL[role.code] ?? role.name}</option>)}
          </select>
          <button type="button" className={secondaryButtonClass} data-testid="role-assign" disabled={!selected || busy}
            onClick={() => run(() => assignAdminRole(user.id, selected), `Đã gán vai trò ${ROLE_LABEL[selected] ?? selected} cho ${user.fullName}.`)}>Gán</button>
        </div>
      )}
      <div className="mt-2"><FormAlert message={error} /></div>
      <p className="mt-2 text-xs font-medium text-slate-500">Vai trò mới có hiệu lực từ lần đăng nhập kế tiếp của người dùng.</p>
    </section>
  )
}

/** Chi tiết tài khoản, đổi trạng thái (2.3.28) và phân vai trò (2.3.29). */
function UserDetailDrawer({ userId, selfId, onClose, onChanged }) {
  const { status, data, error, reload } = useApiResource(() => fetchAdminUser(userId), [userId])
  const [pending, setPending] = useState(null)
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

  async function submit(event) {
    event.preventDefault()
    if (busy) return
    const trimmed = reason.trim()
    if (ACTIONS[pending].needsReason && trimmed.length < 3) return setReasonError('Vui lòng nhập lý do (ít nhất 3 ký tự)')
    if (trimmed.length > 500) return setReasonError('Lý do tối đa 500 ký tự')
    setReasonError('')
    setFormError('')
    setBusy(true)
    try {
      const updated = await changeAdminUserStatus(userId, { status: pending, reason: trimmed })
      setPending(null)
      setReason('')
      reload()
      onChanged(`Đã chuyển ${updated.fullName} sang “${userStatusMeta(updated.status).label}”.`)
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

  const meta = data ? userStatusMeta(data.status) : null
  const options = data ? ['banned', 'inactive', 'active'].filter((target) => target !== data.status && !(data.status === 'pending' && target === 'active')) : []
  const isSelf = data && data.id === selfId

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/40" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose() }}>
      <aside role="dialog" aria-modal="true" aria-label="Chi tiết tài khoản" className="h-full w-full max-w-md overflow-y-auto bg-white p-5 shadow-2xl" data-testid="user-drawer">
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-lg font-semibold text-slate-900">Chi tiết tài khoản</h2>
          <button type="button" className="grid size-9 place-items-center rounded-lg text-slate-500 hover:bg-slate-100" aria-label="Đóng" onClick={onClose} disabled={busy}><X className="size-5" /></button>
        </div>
        {status === 'loading' && <div className="mt-4"><OwnerLoading rows={2} /></div>}
        {status === 'error' && <div className="mt-4"><OwnerError message={error?.message} onRetry={reload} /></div>}
        {status === 'success' && (
          <div className="mt-4 grid gap-5">
            <section>
              <div className="flex flex-wrap items-center gap-2">
                <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', meta.className)} data-testid="user-drawer-status">{meta.label}</span>
                {data.roles.map((role) => <span key={role} className="rounded-full bg-sky-100 px-2.5 py-1 text-xs font-bold text-sky-800">{ROLE_LABEL[role] ?? role}</span>)}
              </div>
              <p className="mt-2 text-base font-semibold text-slate-900">{data.fullName}</p>
              <dl className="mt-2 grid gap-1 text-sm font-medium text-slate-600">
                <div className="break-all">Email: {data.email || 'Chưa có'} {data.email && <span className="text-xs text-slate-400">({data.emailVerified ? 'đã xác minh' : 'chưa xác minh'})</span>}</div>
                <div>Số điện thoại: {data.phone || 'Chưa có'} {data.phone && <span className="text-xs text-slate-400">({data.phoneVerified ? 'đã xác minh' : 'chưa xác minh'})</span>}</div>
                <div>Đăng nhập gần nhất: {data.lastLoginAt ? formatDateTime(data.lastLoginAt) : 'Chưa đăng nhập'}</div>
                <div>Tạo lúc: {formatDateTime(data.createdAt)}</div>
                {data.passwordChangeRequired && <div className="text-amber-700">Đang dùng mật khẩu tạm, chưa đổi.</div>}
              </dl>
              {data.lastStatusChange && (
                <p className="mt-3 rounded-lg bg-slate-50 px-3 py-2 text-xs font-medium text-slate-600" data-testid="user-drawer-last-change">
                  Lần đổi trạng thái gần nhất: {userStatusMeta(data.lastStatusChange.status).label} · {formatDateTime(data.lastStatusChange.at)}{data.lastStatusChange.actor ? ` · ${data.lastStatusChange.actor}` : ''}
                  {data.lastStatusChange.reason ? ` · Lý do: ${data.lastStatusChange.reason}` : ''}
                </p>
              )}
            </section>

            <RolesSection user={data} onChanged={(message) => { reload(); onChanged(message) }} />

            {isSelf ? <p className="rounded-lg bg-amber-50 px-3 py-2 text-xs font-semibold text-amber-900" data-testid="user-drawer-self">Đây là tài khoản của bạn nên không thể tự khóa hay ngừng.</p> : (
              <section>
                <FormAlert message={pending ? '' : formError} />
                {!pending && (
                  <div className="mt-2 flex flex-wrap gap-2">
                    {options.map((target) => (
                      <button key={target} type="button" className={ACTIONS[target].className} data-testid={`user-action-${target}`} onClick={() => { setPending(target); setReason(''); setReasonError(''); setFormError('') }}>{ACTIONS[target].label}</button>
                    ))}
                  </div>
                )}
                {pending && (
                  <form className="grid gap-3" noValidate onSubmit={submit} data-testid="user-status-form">
                    <p className="text-sm font-semibold text-slate-900">{ACTIONS[pending].label}: {data.fullName}</p>
                    <label className="block">
                      <span className="text-sm font-semibold text-slate-900">Lý do{ACTIONS[pending].needsReason ? <span className="text-red-600"> *</span> : ' (không bắt buộc)'}</span>
                      <textarea className={`${textareaClass} mt-2 h-24`} aria-label="Lý do đổi trạng thái" maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} aria-invalid={Boolean(reasonError)} />
                      <FieldError message={reasonError} />
                    </label>
                    <FormAlert message={formError} />
                    <div className="flex justify-end gap-2">
                      <button type="button" className={secondaryButtonClass} disabled={busy} onClick={() => setPending(null)}>Hủy</button>
                      <button type="submit" className={ACTIONS[pending].className} disabled={busy}>{busy ? 'Đang lưu...' : `Xác nhận: ${ACTIONS[pending].label}`}</button>
                    </div>
                  </form>
                )}
              </section>
            )}
          </div>
        )}
      </aside>
    </div>
  )
}

export { UserDetailDrawer }
