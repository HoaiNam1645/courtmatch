import { useState } from 'react'
import { Copy, KeyRound } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { changeAdminOwnerStatus, createAdminOwnerAccount, resetAdminOwnerCredentials } from '../../../api/adminOwner'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { inputClass, dangerButtonClass, primaryButtonClass, secondaryButtonClass, textareaClass } from '../../owner/ownerStyles'

/** Hiện mật khẩu tạm đúng một lần; đóng là mất, không có cách xem lại. */
function TemporaryPassword({ password, email, onDismiss }) {
  const [copied, setCopied] = useState(false)
  async function copy() {
    try {
      await navigator.clipboard.writeText(password)
      setCopied(true)
    } catch {
      setCopied(false)
    }
  }
  return (
    <div className="rounded-xl border border-amber-300 bg-amber-50 p-4" role="alert" data-testid="temporary-password-box">
      <p className="text-sm font-semibold text-amber-900">Mật khẩu tạm của {email}</p>
      <p className="mt-2 flex items-center gap-2">
        <code className="rounded-lg bg-white px-3 py-2 text-base font-semibold tracking-wider text-slate-900" data-testid="temporary-password">{password}</code>
        <button type="button" className="grid size-9 place-items-center rounded-lg border border-amber-300 bg-white text-amber-900" aria-label="Sao chép mật khẩu" onClick={copy}><Copy className="size-4" /></button>
        {copied && <span className="text-xs font-semibold text-amber-900">Đã sao chép</span>}
      </p>
      <p className="mt-2 text-xs font-medium text-amber-900">Mật khẩu chỉ hiện một lần và hệ thống không lưu bản rõ. Hãy gửi cho chủ sân qua kênh an toàn; chủ sân phải đổi mật khẩu ở lần đăng nhập đầu.</p>
      <button type="button" className={`${secondaryButtonClass} mt-3`} data-testid="temporary-password-dismiss" onClick={onDismiss}>Tôi đã ghi lại, ẩn mật khẩu</button>
    </div>
  )
}

/** Form cấp tài khoản chủ sân khi hệ thống chưa có. */
function CreateOwnerForm({ onCreated }) {
  const [form, setForm] = useState({ fullName: '', email: '', phone: '' })
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [busy, setBusy] = useState(false)

  function update(field, value) {
    setForm((current) => ({ ...current, [field]: value }))
  }

  async function submit(event) {
    event.preventDefault()
    if (busy) return
    const next = {}
    if (form.fullName.trim().length < 2) next.fullName = 'Vui lòng nhập họ tên (ít nhất 2 ký tự)'
    if (!/^\S+@\S+\.\S+$/.test(form.email.trim())) next.email = 'Email không đúng định dạng'
    if (!/^0[35789]\d{8}$/.test(form.phone.replace(/[\s.-]/g, ''))) next.phone = 'Số điện thoại di động Việt Nam gồm 10 số, bắt đầu bằng 03, 05, 07, 08 hoặc 09'
    setErrors(next)
    setFormError('')
    if (Object.keys(next).length > 0) return
    setBusy(true)
    try {
      onCreated(await createAdminOwnerAccount(form))
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
    <form className="grid max-w-lg gap-4" noValidate onSubmit={submit} data-testid="owner-create-form">
      <label className="block"><span className="text-sm font-semibold text-slate-900">Họ tên <span className="text-red-600">*</span></span>
        <input className={`${inputClass} mt-2`} aria-label="Họ tên chủ sân" value={form.fullName} maxLength={150} onChange={(event) => update('fullName', event.target.value)} aria-invalid={Boolean(errors.fullName)} /><FieldError message={errors.fullName} /></label>
      <label className="block"><span className="text-sm font-semibold text-slate-900">Email đăng nhập <span className="text-red-600">*</span></span>
        <input className={`${inputClass} mt-2`} type="email" aria-label="Email chủ sân" value={form.email} maxLength={255} onChange={(event) => update('email', event.target.value)} aria-invalid={Boolean(errors.email)} /><FieldError message={errors.email} /></label>
      <label className="block"><span className="text-sm font-semibold text-slate-900">Số điện thoại <span className="text-red-600">*</span></span>
        <input className={`${inputClass} mt-2`} aria-label="Số điện thoại chủ sân" value={form.phone} maxLength={20} onChange={(event) => update('phone', event.target.value)} aria-invalid={Boolean(errors.phone)} /><FieldError message={errors.phone} /></label>
      <FormAlert message={formError} />
      <div><button type="submit" className={primaryButtonClass} disabled={busy}>{busy ? 'Đang tạo...' : 'Cấp tài khoản chủ sân'}</button></div>
    </form>
  )
}

const STATUS_ACTIONS = {
  banned: { label: 'Khóa tài khoản', needsReason: true, className: dangerButtonClass },
  inactive: { label: 'Tạm ngừng', needsReason: true, className: secondaryButtonClass },
  active: { label: 'Kích hoạt lại', needsReason: false, className: primaryButtonClass },
}

/** Khóa, kích hoạt và đặt lại thông tin đăng nhập của chủ sân (2.3.33). */
function OwnerAccountActions({ owner, onChanged, onCredentials }) {
  const [pending, setPending] = useState(null)
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState('')
  const [formError, setFormError] = useState('')
  const [busy, setBusy] = useState(false)
  const [confirmReset, setConfirmReset] = useState(false)
  const options = ['banned', 'inactive', 'active'].filter((value) => value !== owner.status && !(owner.status === 'pending' && value === 'active'))

  async function guarded(action) {
    if (busy) return
    setBusy(true)
    setFormError('')
    try {
      await action()
    } catch (failure) {
      if (failure instanceof ApiError) {
        setReasonError(failure.fieldErrors?.reason ?? '')
        setFormError(failure.message)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
    } finally {
      setBusy(false)
    }
  }

  function submitStatus(event) {
    event.preventDefault()
    const trimmed = reason.trim()
    if (STATUS_ACTIONS[pending].needsReason && trimmed.length < 3) return setReasonError('Vui lòng nhập lý do (ít nhất 3 ký tự)')
    setReasonError('')
    return guarded(async () => {
      const result = await changeAdminOwnerStatus({ status: pending, reason: trimmed })
      const label = STATUS_ACTIONS[pending].label
      setPending(null)
      setReason('')
      onChanged(result, `${label}: đã cập nhật tài khoản chủ sân.`)
    })
  }

  return (
    <div className="mt-4 border-t border-slate-100 pt-4" data-testid="owner-account-actions">
      <FormAlert message={pending ? '' : formError} />
      {!pending && (
        <div className="mt-2 flex flex-wrap gap-2">
          {options.map((value) => <button key={value} type="button" className={STATUS_ACTIONS[value].className} data-testid={`owner-status-${value}`} onClick={() => { setPending(value); setReason(''); setReasonError(''); setFormError('') }}>{STATUS_ACTIONS[value].label}</button>)}
          {!confirmReset && <button type="button" className={secondaryButtonClass} data-testid="owner-reset-open" onClick={() => setConfirmReset(true)}><KeyRound className="size-4" />Đặt lại mật khẩu</button>}
        </div>
      )}
      {confirmReset && !pending && (
        <div className="mt-3 rounded-xl border border-slate-300 bg-white p-4" role="alertdialog" aria-label="Xác nhận đặt lại mật khẩu" data-testid="owner-reset-confirm">
          <p className="text-sm font-semibold text-slate-900">Đặt lại mật khẩu cho chủ sân?</p>
          <p className="mt-1 text-xs font-medium text-slate-500">Mật khẩu cũ và phiên đăng nhập hiện tại sẽ hết hiệu lực. Chủ sân phải đổi mật khẩu tạm ở lần đăng nhập đầu tiên.</p>
          <div className="mt-3 flex justify-end gap-2">
            <button type="button" className={secondaryButtonClass} disabled={busy} onClick={() => setConfirmReset(false)}>Hủy</button>
            <button type="button" className={primaryButtonClass} data-testid="owner-reset-confirm-button" disabled={busy}
              onClick={() => guarded(async () => { const result = await resetAdminOwnerCredentials(); setConfirmReset(false); onCredentials(result) })}>{busy ? 'Đang đặt lại...' : 'Xác nhận đặt lại'}</button>
          </div>
        </div>
      )}
      {pending && (
        <form className="grid gap-3" noValidate onSubmit={submitStatus} data-testid="owner-status-form">
          <p className="text-sm font-semibold text-slate-900">{STATUS_ACTIONS[pending].label}</p>
          {pending !== 'active' && <p className="text-xs font-medium text-slate-500">Booking đã xác nhận không bị hủy, nhưng chủ sân không đăng nhập được.</p>}
          <label className="block">
            <span className="text-sm font-semibold text-slate-900">Lý do{STATUS_ACTIONS[pending].needsReason ? <span className="text-red-600"> *</span> : ' (không bắt buộc)'}</span>
            <textarea className={`${textareaClass} mt-2 h-24`} aria-label="Lý do đổi trạng thái chủ sân" maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} aria-invalid={Boolean(reasonError)} />
            <FieldError message={reasonError} />
          </label>
          <FormAlert message={formError} />
          <div className="flex justify-end gap-2">
            <button type="button" className={secondaryButtonClass} disabled={busy} onClick={() => setPending(null)}>Hủy</button>
            <button type="submit" className={STATUS_ACTIONS[pending].className} disabled={busy}>{busy ? 'Đang lưu...' : 'Xác nhận'}</button>
          </div>
        </form>
      )}
    </div>
  )
}

export { CreateOwnerForm, OwnerAccountActions, TemporaryPassword }
