import { useState } from 'react'
import { Eye, EyeOff, KeyRound } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../../../api/client'
import { changeOwnerPassword } from '../../../api/ownerAccount'
import { useApp } from '../../../app/useApp'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { saveCachedUser } from '../../../shared/session'
import { OwnerLayout } from '../components/OwnerLayout'
import { OwnerCard } from '../components/OwnerUi'
import { primaryButtonClass } from '../ownerStyles'

/** Cùng quy tắc với backend: 8–72 ký tự, có chữ cái và chữ số. */
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).+$/

function validate(values) {
  const errors = {}
  if (!values.currentPassword) errors.currentPassword = 'Vui lòng nhập mật khẩu hiện tại'
  if (!values.newPassword) {
    errors.newPassword = 'Vui lòng nhập mật khẩu mới'
  } else if (values.newPassword.length < 8 || values.newPassword.length > 72) {
    errors.newPassword = 'Mật khẩu từ 8 đến 72 ký tự'
  } else if (!PASSWORD_PATTERN.test(values.newPassword)) {
    errors.newPassword = 'Mật khẩu phải có ít nhất một chữ cái và một chữ số'
  }
  if (!values.confirmPassword) {
    errors.confirmPassword = 'Vui lòng nhập lại mật khẩu mới'
  } else if (values.confirmPassword !== values.newPassword) {
    errors.confirmPassword = 'Mật khẩu nhập lại không khớp'
  }
  return errors
}

function PasswordField({ label, name, value, onChange, error, autoComplete }) {
  const [visible, setVisible] = useState(false)
  return (
    <label className="block">
      <span className="text-sm font-semibold text-emerald-950">{label}</span>
      <div className="mt-2 flex items-center gap-2 rounded-lg border border-stone-300 bg-white pr-1 focus-within:border-emerald-700">
        <input
          className="h-11 min-w-0 flex-1 rounded-lg px-3 text-sm text-stone-800 outline-none"
          name={name}
          type={visible ? 'text' : 'password'}
          value={value}
          autoComplete={autoComplete}
          onChange={(event) => onChange(name, event.target.value)}
        />
        <button
          type="button"
          className="grid size-9 place-items-center text-emerald-700"
          onClick={() => setVisible((current) => !current)}
          aria-label={visible ? `Ẩn ${label.toLowerCase()}` : `Hiện ${label.toLowerCase()}`}
        >
          {visible ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
        </button>
      </div>
      <FieldError message={error} />
    </label>
  )
}

/** 2.3.1 — đổi mật khẩu tạm do quản trị viên cấp. */
function OwnerChangePasswordPage() {
  const { user } = useApp()
  const navigate = useNavigate()
  const [values, setValues] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' })
  const [fieldErrors, setFieldErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [done, setDone] = useState(false)

  function handleChange(name, value) {
    setValues((current) => ({ ...current, [name]: value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting) return

    const errors = validate(values)
    setFieldErrors(errors)
    setFormError('')
    if (Object.keys(errors).length > 0) return

    setSubmitting(true)
    try {
      const updated = await changeOwnerPassword(values)
      saveCachedUser(updated)
      setDone(true)
      navigate('/owner', { replace: true })
    } catch (error) {
      if (error instanceof ApiError) {
        const serverErrors = { ...(error.fieldErrors ?? {}) }
        // Backend gắn lỗi "không khớp" vào trường tổng hợp passwordConfirmed.
        if (serverErrors.passwordConfirmed) serverErrors.confirmPassword = serverErrors.passwordConfirmed
        setFieldErrors(serverErrors)
        setFormError(error.message)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  const required = Boolean(user?.passwordChangeRequired)

  return (
    <OwnerLayout
      eyebrow="2.3.1 · Tài khoản chủ sân"
      title="Đổi mật khẩu"
      description={required
        ? 'Tài khoản đang dùng mật khẩu tạm do quản trị viên cấp. Hãy đặt mật khẩu mới trước khi vào trang quản lý sân.'
        : 'Đặt mật khẩu mới cho tài khoản chủ sân.'}
    >
      <OwnerCard className="mx-auto max-w-xl">
        {required && (
          <div className="mb-5 flex gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-medium text-amber-800" role="status">
            <KeyRound className="mt-0.5 size-4 shrink-0" />
            Bạn cần đổi mật khẩu tạm để tiếp tục.
          </div>
        )}
        <form className="grid gap-5" noValidate onSubmit={handleSubmit}>
          <PasswordField label="Mật khẩu hiện tại" name="currentPassword" value={values.currentPassword} onChange={handleChange} error={fieldErrors.currentPassword} autoComplete="current-password" />
          <PasswordField label="Mật khẩu mới" name="newPassword" value={values.newPassword} onChange={handleChange} error={fieldErrors.newPassword} autoComplete="new-password" />
          <PasswordField label="Nhập lại mật khẩu mới" name="confirmPassword" value={values.confirmPassword} onChange={handleChange} error={fieldErrors.confirmPassword} autoComplete="new-password" />
          <p className="text-xs font-medium text-stone-500">Mật khẩu từ 8 đến 72 ký tự, có ít nhất một chữ cái và một chữ số.</p>
          <FormAlert message={formError} />
          <button type="submit" className={primaryButtonClass} disabled={submitting || done}>
            {submitting ? 'Đang lưu...' : 'Lưu mật khẩu mới'}
          </button>
        </form>
      </OwnerCard>
    </OwnerLayout>
  )
}

export default OwnerChangePasswordPage
