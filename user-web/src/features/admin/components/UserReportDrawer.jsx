import { useEffect, useState } from 'react'
import { CheckCircle2, X } from 'lucide-react'
import { ApiError } from '../../../api/client'
import {
  ACCOUNT_STATUS_LABEL,
  fetchUserReport,
  RESOLVE_ACTIONS,
  rejectUserReport,
  reportStatusMeta,
  resolveActionLabel,
  resolveUserReport,
  startUserReport,
} from '../../../api/adminReports'
import { useApiResource } from '../../../hooks/useApiResource'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { classNames } from '../../../shared/utils'
import { OwnerError, OwnerLoading } from '../../owner/components/OwnerUi'
import { formatDateTime } from '../../owner/ownerDates'
import { dangerButtonClass, primaryButtonClass, secondaryButtonClass, textareaClass } from '../../owner/ownerStyles'

const ROLE_LABEL = { player: 'Người chơi', owner: 'Chủ sân', admin: 'Quản trị viên', staff: 'Nhân viên' }

/** Form kết luận: chấp nhận (chọn hướng xử lý) hoặc bác bỏ; ghi chú luôn bắt buộc. */
function ConcludeForm({ report, mode, onDone, onCancel }) {
  const [action, setAction] = useState('')
  const [note, setNote] = useState('')
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [busy, setBusy] = useState(false)
  const resolving = mode === 'resolve'

  async function submit(event) {
    event.preventDefault()
    if (busy) return
    const next = {}
    if (resolving && !action) next.action = 'Vui lòng chọn hướng xử lý'
    if (note.trim().length < 3) next.note = 'Vui lòng nhập ghi chú (ít nhất 3 ký tự)'
    if (note.length > 1000) next.note = 'Ghi chú tối đa 1000 ký tự'
    setErrors(next)
    setFormError('')
    if (Object.keys(next).length > 0) return
    setBusy(true)
    try {
      const updated = resolving ? await resolveUserReport(report.id, { action, note }) : await rejectUserReport(report.id, { note })
      onDone(updated, resolving ? `Đã xử lý báo cáo: ${resolveActionLabel(action)}.` : 'Đã bác bỏ báo cáo.')
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
    <form className="grid gap-3" noValidate onSubmit={submit} data-testid={resolving ? 'report-resolve-form' : 'report-reject-form'}>
      {resolving && (
        <fieldset>
          <legend className="text-sm font-semibold text-slate-900">Hướng xử lý <span className="text-red-600">*</span></legend>
          <div className="mt-2 grid gap-2">
            {RESOLVE_ACTIONS.map((item) => {
              const disabled = item.locks && !report.canLockAccount
              return (
                <label key={item.value} className={classNames('flex items-start gap-3 rounded-lg border p-3', disabled ? 'cursor-not-allowed border-slate-100 bg-slate-50 opacity-60' : action === item.value ? 'border-slate-800 bg-slate-50' : 'cursor-pointer border-slate-200')}>
                  <input type="radio" name="report-action" className="mt-1" value={item.value} disabled={disabled} checked={action === item.value} onChange={() => setAction(item.value)} data-testid={`report-action-${item.value}`} />
                  <span>
                    <span className="block text-sm font-semibold text-slate-900">{item.label}</span>
                    <span className="block text-xs font-medium text-slate-500">{disabled ? lockDisabledReason(report) : item.hint}</span>
                  </span>
                </label>
              )
            })}
          </div>
          <FieldError message={errors.action} />
        </fieldset>
      )}
      <label className="block">
        <span className="text-sm font-semibold text-slate-900">Ghi chú <span className="text-red-600">*</span></span>
        <textarea className={`${textareaClass} mt-2 h-24`} aria-label="Ghi chú kết luận" maxLength={1000} value={note} onChange={(event) => setNote(event.target.value)} aria-invalid={Boolean(errors.note)} data-testid="report-note" />
        <FieldError message={errors.note} />
      </label>
      <FormAlert message={formError} />
      <div className="flex justify-end gap-2">
        <button type="button" className={secondaryButtonClass} disabled={busy} onClick={onCancel}>Hủy</button>
        <button type="submit" className={resolving ? primaryButtonClass : dangerButtonClass} disabled={busy} data-testid="report-conclude-submit">
          {busy ? 'Đang lưu...' : resolving ? 'Xác nhận xử lý' : 'Bác bỏ báo cáo'}
        </button>
      </div>
    </form>
  )
}

function lockDisabledReason(report) {
  if (report.selfTarget) return 'Không thể tự khóa chính mình.'
  if (!report.target.exists) return 'Tài khoản không còn tồn tại.'
  if (report.target.status !== 'active') return `Tài khoản đang ở trạng thái "${ACCOUNT_STATUS_LABEL[report.target.status] ?? report.target.status}".`
  return 'Bạn chưa có quyền quản lý tài khoản.'
}

/** Chi tiết báo cáo người dùng và các bước xử lý (2.3.36). */
function UserReportDrawer({ reportId, onClose, onChanged }) {
  const { status, data, error, reload } = useApiResource(() => fetchUserReport(reportId), [reportId])
  const [mode, setMode] = useState(null)
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState('')

  useEffect(() => {
    function onKey(event) {
      if (event.key === 'Escape' && !busy) onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose, busy])

  async function start() {
    if (busy) return
    setBusy(true)
    setFormError('')
    try {
      await startUserReport(reportId)
      reload()
      onChanged('Đã nhận xử lý báo cáo.')
    } catch (failure) {
      setFormError(failure instanceof ApiError ? failure.message : 'Đã có lỗi xảy ra, vui lòng thử lại.')
      if (failure instanceof ApiError && failure.status === 409) reload()
    } finally {
      setBusy(false)
    }
  }

  const meta = data ? reportStatusMeta(data.status) : null
  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/40" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose() }}>
      <aside role="dialog" aria-modal="true" aria-label="Chi tiết báo cáo người dùng" className="h-full w-full max-w-lg overflow-y-auto bg-white p-5 shadow-2xl" data-testid="report-drawer">
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-lg font-semibold text-slate-900">Chi tiết báo cáo</h2>
          <button type="button" className="grid size-9 place-items-center rounded-lg text-slate-500 hover:bg-slate-100" aria-label="Đóng" onClick={onClose} disabled={busy}><X className="size-5" /></button>
        </div>
        {status === 'loading' && <div className="mt-4"><OwnerLoading rows={2} /></div>}
        {status === 'error' && <div className="mt-4"><OwnerError message={error?.message} onRetry={reload} /></div>}
        {status === 'success' && (
          <div className="mt-4 grid gap-5">
            <section>
              <div className="flex flex-wrap items-center gap-2">
                <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', meta.className)} data-testid="report-drawer-status">{meta.label}</span>
                <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-700" data-testid="report-drawer-reason">{data.reason}</span>
              </div>
              <p className="mt-3 whitespace-pre-line text-sm font-medium text-slate-700" data-testid="report-drawer-description">{data.description || 'Người báo cáo không nhập mô tả.'}</p>
              <p className="mt-2 text-xs font-medium text-slate-500">Gửi lúc {formatDateTime(data.createdAt)}</p>
            </section>

            <section className="grid gap-3 sm:grid-cols-2">
              <div className="rounded-xl border border-slate-200 p-3">
                <p className="text-xs font-bold uppercase tracking-wide text-slate-400">Người báo cáo</p>
                <p className="mt-1 text-sm font-semibold text-slate-900">{data.reporter.fullName}</p>
                <p className="break-all text-xs font-medium text-slate-500">{data.reporter.email}</p>
              </div>
              <div className="rounded-xl border border-red-100 bg-red-50/40 p-3" data-testid="report-drawer-target">
                <p className="text-xs font-bold uppercase tracking-wide text-red-400">Người bị báo cáo</p>
                {data.target.exists ? (
                  <>
                    <p className="mt-1 text-sm font-semibold text-slate-900">{data.target.fullName}</p>
                    <p className="break-all text-xs font-medium text-slate-500">{data.target.email}</p>
                    <p className="mt-1 text-xs font-semibold text-slate-700" data-testid="report-target-status">{ACCOUNT_STATUS_LABEL[data.target.status] ?? data.target.status}{data.roles.length > 0 ? ` · ${data.roles.map((role) => ROLE_LABEL[role] ?? role).join(', ')}` : ''}</p>
                  </>
                ) : <p className="mt-1 text-sm font-medium text-slate-500" data-testid="report-target-missing">Tài khoản không còn tồn tại.</p>}
                <p className="mt-1 text-xs font-semibold text-red-700" data-testid="report-total-received">Đã nhận {data.totalReceived} báo cáo</p>
              </div>
            </section>

            {data.related.length > 0 && (
              <section>
                <h3 className="text-sm font-semibold text-slate-900">Báo cáo khác về tài khoản này</h3>
                <ul className="mt-2 grid gap-2" data-testid="report-related">
                  {data.related.map((item) => (
                    <li key={item.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-slate-200 p-3 text-sm">
                      <span className="font-semibold text-slate-800">{item.reason} <span className="font-medium text-slate-500">· {item.reporterName}</span></span>
                      <span className="flex items-center gap-2 text-xs font-medium text-slate-500">{formatDateTime(item.createdAt)}<span className={classNames('rounded-full px-2 py-0.5 font-bold', reportStatusMeta(item.status).className)}>{reportStatusMeta(item.status).label}</span></span>
                    </li>
                  ))}
                </ul>
              </section>
            )}

            {data.handling && (
              <section className="rounded-xl border border-slate-200 bg-slate-50 p-4" data-testid="report-handling">
                <h3 className="flex items-center gap-2 text-sm font-semibold text-slate-900"><CheckCircle2 className="size-4 text-emerald-600" />Kết luận</h3>
                {data.handling.action && <p className="mt-2 text-sm font-semibold text-slate-800" data-testid="report-handling-action">Hướng xử lý: {resolveActionLabel(data.handling.action)}</p>}
                <p className="mt-1 whitespace-pre-line text-sm font-medium text-slate-700" data-testid="report-handling-note">{data.handling.note}</p>
                <p className="mt-2 text-xs font-medium text-slate-500">{data.handling.handledBy} · {formatDateTime(data.handling.handledAt)}</p>
              </section>
            )}

            <section>
              <FormAlert message={mode ? '' : formError} />
              {data.selfTarget && <p className="rounded-lg bg-amber-50 px-3 py-2 text-xs font-semibold text-amber-900" data-testid="report-self">Báo cáo này nhắm vào chính bạn nên bạn không thể xử lý. Hãy nhờ quản trị viên khác.</p>}
              {data.canStart && (
                <button type="button" className={`${primaryButtonClass} mt-2`} data-testid="report-start" disabled={busy} onClick={start}>{busy ? 'Đang nhận...' : 'Nhận xử lý'}</button>
              )}
              {data.canConclude && !mode && (
                <div className="mt-2 flex flex-wrap gap-2">
                  <button type="button" className={primaryButtonClass} data-testid="report-resolve-open" onClick={() => { setMode('resolve'); setFormError('') }}>Chấp nhận và xử lý</button>
                  <button type="button" className={dangerButtonClass} data-testid="report-reject-open" onClick={() => { setMode('reject'); setFormError('') }}>Bác bỏ báo cáo</button>
                </div>
              )}
              {data.canConclude && mode && (
                <ConcludeForm
                  report={data}
                  mode={mode}
                  onCancel={() => setMode(null)}
                  onDone={(updated, message) => { setMode(null); reload(); onChanged(message) }}
                />
              )}
            </section>
          </div>
        )}
      </aside>
    </div>
  )
}

export { UserReportDrawer }
