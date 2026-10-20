import { useState } from 'react'
import { ArrowLeft, Check, ChevronRight, Eye, EyeOff, KeyRound, LockKeyhole, ShieldCheck } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { confirmPasswordReset as confirmPasswordResetApi } from '../../../api/auth'
import { clearRecoveryRequest, readRecoveryRequest } from '../../../shared/session'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { classNames } from '../../../shared/utils'

const recoverySteps = ['Tài khoản', 'Xác minh', 'Mật khẩu']

function RecoveryShell({ title, backHref, step, children }) {
  return (
    <main className="login-bg relative min-h-screen overflow-hidden px-4 pb-10 pt-6 text-emerald-950 sm:px-6">
      <header className="relative z-10 mx-auto flex max-w-[720px] items-center justify-center">
        <a
          href={backHref}
          className="absolute left-0 top-0 grid size-9 place-items-center rounded-full bg-white/10 text-white backdrop-blur sm:left-5"
          aria-label="Quay lại"
        >
          <ArrowLeft className="size-5 stroke-[3]" />
        </a>
        <h1 className="text-[20px] font-semibold leading-9 text-white">{title}</h1>
      </header>

      <section className="relative z-10 mx-auto flex min-h-[calc(100svh-72px)] max-w-[620px] flex-col justify-center py-12">
        <div className="mb-5 grid grid-cols-3 gap-2 px-2">
          {recoverySteps.map((label, index) => {
            const number = index + 1
            const complete = step > number
            const active = step === number
            return (
              <div key={label} className="text-center">
                <div className="flex items-center">
                  <span className={classNames('h-px flex-1', index === 0 ? 'bg-transparent' : complete || active ? 'bg-lime-300' : 'bg-white/25')} />
                  <span
                    className={classNames(
                      'grid size-9 shrink-0 place-items-center rounded-full text-sm font-bold ring-1',
                      complete && 'bg-lime-300 text-emerald-950 ring-lime-300',
                      active && 'bg-white text-emerald-800 ring-white',
                      !complete && !active && 'bg-emerald-900/30 text-white/60 ring-white/25',
                    )}
                  >
                    {complete ? <Check className="size-4" /> : number}
                  </span>
                  <span className={classNames('h-px flex-1', index === recoverySteps.length - 1 ? 'bg-transparent' : complete ? 'bg-lime-300' : 'bg-white/25')} />
                </div>
                <p className={classNames('mt-2 text-xs font-semibold', active || complete ? 'text-white' : 'text-white/55')}>
                  {label}
                </p>
              </div>
            )
          })}
        </div>

        <div className="overflow-hidden rounded-xl border border-white/20 bg-white shadow-[0_24px_60px_rgba(4,72,42,0.28)]">
          {children}
        </div>
      </section>
    </main>
  )
}

function RecoveryCardHeader({ icon: Icon, eyebrow, title, description }) {
  return (
    <div className="border-b border-emerald-100 bg-[linear-gradient(135deg,#effcf5_0%,#ffffff_68%)] px-6 py-6 sm:px-8">
      <span className="grid size-12 place-items-center rounded-lg bg-emerald-700 text-white shadow-[0_10px_24px_rgba(4,121,63,0.18)]">
        <Icon className="size-6" />
      </span>
      <p className="mt-5 text-xs font-bold uppercase tracking-[0.16em] text-emerald-600">{eyebrow}</p>
      <h2 className="mt-1.5 text-2xl font-semibold tracking-tight text-emerald-950">{title}</h2>
      <p className="mt-2 max-w-lg text-sm font-medium leading-6 text-stone-500">{description}</p>
    </div>
  )
}

/** Đếm ngược còn lại của một mốc thời gian đã lưu, tính lại theo đồng hồ thật. */
function RecoveryRestartCard({ step, title, description }) {
  return (
    <RecoveryShell title="Khôi phục tài khoản" backHref="/login" step={step}>
      <RecoveryCardHeader icon={KeyRound} eyebrow="Cần bắt đầu lại" title={title} description={description} />
      <div className="px-6 py-6 sm:px-8 sm:py-7">
        <a
          className="flex h-13 items-center justify-center gap-2 rounded-lg bg-emerald-700 text-sm font-semibold text-white shadow-[0_10px_24px_rgba(4,121,63,0.2)] hover:bg-emerald-800"
          href="/forgot-password"
        >
          Yêu cầu mã mới
          <ChevronRight className="size-4" />
        </a>
      </div>
    </RecoveryShell>
  )
}

function ResetPasswordPage() {
  const request = readRecoveryRequest()
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmation, setShowConfirmation] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [sessionLost, setSessionLost] = useState(false)

  // Đúng bằng ràng buộc của backend ở PasswordResetConfirmRequest và RegisterRequest.
  const requirements = [
    { label: 'Từ 8 đến 72 ký tự', met: password.length >= 8 && password.length <= 72 },
    { label: 'Có ít nhất một chữ cái', met: /[A-Za-z]/.test(password) },
    { label: 'Có ít nhất một chữ số', met: /\d/.test(password) },
  ]
  const isValid = requirements.every((item) => item.met) && password === confirmation

  if (!request?.resetToken || sessionLost) {
    return (
      <RecoveryRestartCard
        step={2}
        title="Phiên đặt lại đã hết hạn"
        description="Vì lý do bảo mật, bước đặt mật khẩu mới chỉ mở trong ít phút sau khi xác minh. Vui lòng yêu cầu mã mới."
      />
    )
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting || !isValid) return

    setSubmitting(true)
    setFormError('')
    setFieldErrors({})

    try {
      await confirmPasswordResetApi({
        resetToken: request.resetToken,
        password,
        confirmPassword: confirmation,
      })
      clearRecoveryRequest()
      window.location.href = '/reset-password/success'
    } catch (error) {
      if (error instanceof ApiError) {
        setFieldErrors(error.fieldErrors ?? {})
        setFormError(error.message)
        if (error.code === 'RESET_TOKEN_INVALID') setSessionLost(true)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
      setSubmitting(false)
    }
  }

  return (
    <RecoveryShell title="Tạo mật khẩu mới" backHref="/forgot-password/verify" step={3}>
      <RecoveryCardHeader
        icon={LockKeyhole}
        eyebrow="Bước 3 · Mật khẩu"
        title="Bảo vệ tài khoản của bạn"
        description="Tạo mật khẩu mới khác với mật khẩu hiện tại và đủ mạnh để bảo vệ tài khoản."
      />

      <form className="grid gap-5 px-6 py-7 sm:px-8" onSubmit={handleSubmit}>
        <label>
          <span className="text-sm font-semibold text-emerald-950">Mật khẩu mới</span>
          <div className="mt-2.5 flex h-13 items-center gap-3 rounded-lg border border-stone-200 px-4 focus-within:border-emerald-500 focus-within:ring-1 focus-within:ring-emerald-400">
            <LockKeyhole className="size-5 text-emerald-700" />
            <input
              className="min-w-0 flex-1 text-base outline-none placeholder:text-stone-400"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              type={showPassword ? 'text' : 'password'}
              placeholder="Nhập mật khẩu mới"
              autoComplete="new-password"
              maxLength={72}
              required
            />
            <button type="button" className="text-emerald-700" onClick={() => setShowPassword((value) => !value)} aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}>
              {showPassword ? <EyeOff className="size-5" /> : <Eye className="size-5" />}
            </button>
          </div>
          <FieldError message={fieldErrors.password} />
        </label>

        <div className="grid gap-2 rounded-lg bg-stone-50 p-4 ring-1 ring-stone-100 sm:grid-cols-2">
          {requirements.map((item) => (
            <p key={item.label} className={classNames('flex items-center gap-2 text-xs font-semibold', item.met ? 'text-emerald-700' : 'text-stone-400')}>
              <span className={classNames('grid size-4 place-items-center rounded-full', item.met ? 'bg-emerald-600 text-white' : 'bg-stone-200 text-stone-400')}>
                <Check className="size-3" />
              </span>
              {item.label}
            </p>
          ))}
        </div>

        <label>
          <span className="text-sm font-semibold text-emerald-950">Xác nhận mật khẩu mới</span>
          <div className={classNames('mt-2.5 flex h-13 items-center gap-3 rounded-lg border px-4 focus-within:ring-1', confirmation && confirmation !== password ? 'border-red-300 focus-within:ring-red-200' : 'border-stone-200 focus-within:border-emerald-500 focus-within:ring-emerald-400')}>
            <LockKeyhole className="size-5 text-emerald-700" />
            <input
              className="min-w-0 flex-1 text-base outline-none placeholder:text-stone-400"
              value={confirmation}
              onChange={(event) => setConfirmation(event.target.value)}
              type={showConfirmation ? 'text' : 'password'}
              placeholder="Nhập lại mật khẩu mới"
              autoComplete="new-password"
              maxLength={72}
              required
            />
            <button type="button" className="text-emerald-700" onClick={() => setShowConfirmation((value) => !value)} aria-label={showConfirmation ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}>
              {showConfirmation ? <EyeOff className="size-5" /> : <Eye className="size-5" />}
            </button>
          </div>
          {confirmation && confirmation !== password && <span className="mt-2 block text-xs font-medium text-red-600">Mật khẩu xác nhận chưa trùng khớp.</span>}
        </label>

        <FormAlert message={formError} />

        <button
          className="mt-2 flex h-13 items-center justify-center gap-2 rounded-lg bg-emerald-700 text-sm font-semibold text-white shadow-[0_10px_24px_rgba(4,121,63,0.2)] disabled:cursor-not-allowed disabled:bg-stone-300 disabled:shadow-none"
          disabled={submitting || !isValid}
        >
          {submitting ? 'Đang cập nhật...' : 'Cập nhật mật khẩu'}
          {!submitting && <ShieldCheck className="size-4" />}
        </button>
      </form>
    </RecoveryShell>
  )
}

export default ResetPasswordPage
