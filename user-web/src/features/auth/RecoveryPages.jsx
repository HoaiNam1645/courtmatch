import { useEffect, useRef, useState } from 'react'
import { ArrowLeft, Check, CheckCircle2, ChevronRight, Eye, EyeOff, KeyRound, LockKeyhole, Mail, Phone, ShieldCheck } from 'lucide-react'
import { ApiError } from '../../api/client'
import { confirmPasswordReset as confirmPasswordResetApi, requestPasswordReset as requestPasswordResetApi, verifyPasswordResetCode as verifyPasswordResetCodeApi } from '../../api/auth'
import { clearRecoveryRequest, maskRecoveryDestination, readRecoveryRequest, saveRecoveryRequest } from '../../shared/session'
import { FieldError, FormAlert } from '../../shared/FormFeedback'
import { formatError } from '../../shared/formatError'
import { classNames } from '../../shared/utils'

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
function secondsUntil(timestamp) {
  if (!timestamp) return 0
  return Math.max(0, Math.ceil((timestamp - Date.now()) / 1000))
}

function formatCountdown(totalSeconds) {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

/**
 * Hiện khi phiên khôi phục không còn dùng được: chưa xin mã, mã bị huỷ vì nhập sai
 * quá nhiều, hoặc token đặt mật khẩu đã hết hạn. Luôn đưa người dùng về bước 1.
 */
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

function ForgotPasswordPage() {
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting) return

    const formData = new FormData(event.currentTarget)
    const destination = formData.get('courtly_forgot_email')?.toString().trim() ?? ''

    // Kiểm tra sớm cho đỡ một vòng gọi mạng; backend vẫn kiểm tra lại y hệt.
    if (!destination) {
      setFieldErrors({ destination: 'Vui lòng nhập email đã đăng ký' })
      setFormError('')
      return
    }

    setSubmitting(true)
    setFormError('')
    setFieldErrors({})

    try {
      const result = await requestPasswordResetApi({ channel: 'email', destination })
      saveRecoveryRequest({
        method: 'email',
        destination,
        sentAt: Date.now(),
        resendAfterSeconds: result.resendAfterSeconds,
        codeExpiresAt: Date.now() + result.expiresInSeconds * 1000,
      })
      window.location.href = '/forgot-password/verify'
    } catch (error) {
      if (error instanceof ApiError) {
        setFieldErrors(error.fieldErrors ?? {})
        setFormError(error.message)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
      setSubmitting(false)
    }
  }

  return (
    <RecoveryShell title="Khôi phục tài khoản" backHref="/login" step={1}>
      <RecoveryCardHeader
        icon={KeyRound}
        eyebrow="Bước 1 · Tìm tài khoản"
        title="Bạn quên mật khẩu?"
        description="Nhập email đã đăng ký để nhận mã xác minh. Vì lý do bảo mật, chúng tôi luôn hiển thị cùng một kết quả dù tài khoản có tồn tại hay không."
      />

      <form className="grid gap-6 px-6 py-6 sm:px-8 sm:py-7" autoComplete="off" onSubmit={handleSubmit}>
        <div>
          <p className="text-sm font-semibold text-emerald-950">Nhận mã xác minh qua</p>
          <div className="mt-3 grid gap-3 sm:grid-cols-2">
            <div className="flex h-14 items-center gap-3 rounded-lg border border-emerald-500 bg-emerald-50 px-4 text-sm font-semibold text-emerald-800 ring-1 ring-emerald-400">
              <span className="grid size-8 place-items-center rounded-md bg-emerald-700 text-white">
                <Mail className="size-4" />
              </span>
              Email
              <span className="ml-auto grid size-5 place-items-center rounded-full border-2 border-emerald-500">
                <span className="size-2.5 rounded-full bg-emerald-500" />
              </span>
            </div>

            {/* Gửi mã qua SMS chưa triển khai. Hiện rõ thay vì để người dùng chọn rồi mới báo lỗi. */}
            <div
              className="flex h-14 items-center gap-3 rounded-lg border border-stone-200 bg-stone-50 px-4 text-sm font-semibold text-stone-400"
              title="Chức năng đang được phát triển"
            >
              <span className="grid size-8 place-items-center rounded-md bg-stone-100 text-stone-400">
                <Phone className="size-4" />
              </span>
              Số điện thoại
              <span className="ml-auto rounded-full bg-stone-200 px-2 py-0.5 text-[11px] font-bold uppercase tracking-wide text-stone-500">
                Sắp có
              </span>
            </div>
          </div>
        </div>

        <label className="block">
          <span className="text-sm font-semibold text-emerald-950">Email đã đăng ký</span>
          <div className="mt-2.5 flex h-13 items-center gap-3 rounded-lg border border-stone-200 bg-white px-4 focus-within:border-emerald-500 focus-within:ring-1 focus-within:ring-emerald-400">
            <Mail className="size-5 text-emerald-700" />
            <input
              className="min-w-0 flex-1 text-base text-stone-800 outline-none placeholder:text-stone-400"
              placeholder="name@email.com"
              name="courtly_forgot_email"
              type="email"
              autoComplete="email"
              maxLength={255}
            />
          </div>
          <FieldError message={fieldErrors.destination} />
        </label>

        <div className="rounded-lg bg-amber-50 px-4 py-3 text-xs font-medium leading-5 text-amber-800 ring-1 ring-amber-100">
          Mã xác minh có hiệu lực trong 10 phút. Không chia sẻ mã này với bất kỳ ai.
        </div>

        <FormAlert message={formError} />

        <button
          type="submit"
          disabled={submitting}
          className="flex h-13 items-center justify-center gap-2 rounded-lg bg-emerald-700 text-sm font-semibold text-white shadow-[0_10px_24px_rgba(4,121,63,0.2)] hover:bg-emerald-800 disabled:cursor-not-allowed disabled:bg-stone-300 disabled:shadow-none"
        >
          {submitting ? 'Đang gửi mã...' : 'Gửi mã xác minh'}
          {!submitting && <ChevronRight className="size-4" />}
        </button>
      </form>
    </RecoveryShell>
  )
}

function VerifyRecoveryPage() {
  const request = readRecoveryRequest()
  const [code, setCode] = useState(Array(6).fill(''))
  const [secondsLeft, setSecondsLeft] = useState(() => secondsUntil((request?.sentAt ?? 0) + (request?.resendAfterSeconds ?? 0) * 1000))
  const [submitting, setSubmitting] = useState(false)
  const [resending, setResending] = useState(false)
  const [formError, setFormError] = useState('')
  const [notice, setNotice] = useState('')
  const [needsNewCode, setNeedsNewCode] = useState(false)
  const inputRefs = useRef([])

  useEffect(() => {
    if (secondsLeft <= 0) return undefined
    const timer = window.setInterval(() => setSecondsLeft((value) => Math.max(0, value - 1)), 1000)
    return () => window.clearInterval(timer)
  }, [secondsLeft])

  if (!request?.destination) {
    return (
      <RecoveryRestartCard
        step={1}
        title="Chưa có yêu cầu nào"
        description="Bạn cần nhập email và nhận mã xác minh trước khi tới bước này."
      />
    )
  }

  if (needsNewCode) {
    return (
      <RecoveryRestartCard
        step={2}
        title="Mã xác minh đã bị huỷ"
        description="Bạn đã nhập sai quá số lần cho phép. Vui lòng yêu cầu một mã xác minh mới."
      />
    )
  }

  const maskedDestination = maskRecoveryDestination(request.method, request.destination)

  function updateDigit(index, value) {
    const digit = value.replace(/\D/g, '').slice(-1)
    setCode((current) => current.map((item, itemIndex) => (itemIndex === index ? digit : item)))
    if (digit && index < 5) inputRefs.current[index + 1]?.focus()
  }

  function handlePaste(event) {
    const digits = event.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6).split('')
    if (!digits.length) return
    event.preventDefault()
    setCode(Array.from({ length: 6 }, (_, index) => digits[index] ?? ''))
    inputRefs.current[Math.min(digits.length, 6) - 1]?.focus()
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting) return
    const value = code.join('')
    if (value.length < 6) return

    setSubmitting(true)
    setFormError('')
    setNotice('')

    try {
      const result = await verifyPasswordResetCodeApi({ destination: request.destination, code: value })
      saveRecoveryRequest({
        ...request,
        resetToken: result.resetToken,
        tokenExpiresAt: Date.now() + result.expiresInSeconds * 1000,
      })
      window.location.href = '/reset-password'
    } catch (error) {
      if (error instanceof ApiError) {
        setFormError(error.message)
        // Hết lượt thử thì mã đã bị huỷ ở server, ở lại màn này cũng không nhập được nữa.
        if (error.code === 'RESET_TOO_MANY_ATTEMPTS') setNeedsNewCode(true)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
      setCode(Array(6).fill(''))
      inputRefs.current[0]?.focus()
      setSubmitting(false)
    }
  }

  async function handleResend() {
    if (resending || secondsLeft > 0) return
    setResending(true)
    setFormError('')
    setNotice('')

    try {
      const result = await requestPasswordResetApi({ channel: 'email', destination: request.destination })
      saveRecoveryRequest({
        method: 'email',
        destination: request.destination,
        sentAt: Date.now(),
        resendAfterSeconds: result.resendAfterSeconds,
        codeExpiresAt: Date.now() + result.expiresInSeconds * 1000,
      })
      setSecondsLeft(result.resendAfterSeconds)
      setCode(Array(6).fill(''))
      setNotice('Đã gửi lại mã xác minh. Vui lòng kiểm tra hộp thư.')
    } catch (error) {
      if (error instanceof ApiError) {
        setFormError(error.message)
        // Server nói còn phải đợi bao lâu; đếm ngược đúng số đó thay vì đoán lại từ đầu.
        const retryAfter = Number(error.fieldErrors?.retryAfterSeconds)
        if (Number.isFinite(retryAfter) && retryAfter > 0) setSecondsLeft(retryAfter)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
    } finally {
      setResending(false)
    }
  }

  return (
    <RecoveryShell title="Xác minh danh tính" backHref="/forgot-password" step={2}>
      <RecoveryCardHeader
        icon={ShieldCheck}
        eyebrow="Bước 2 · Xác minh"
        title="Nhập mã gồm 6 chữ số"
        description={`Nếu ${maskedDestination} là email đã đăng ký, mã xác minh vừa được gửi tới hộp thư đó.`}
      />

      <form className="px-6 py-7 sm:px-8" onSubmit={handleSubmit}>
        <div className="grid grid-cols-6 gap-2 sm:gap-3" onPaste={handlePaste}>
          {code.map((digit, index) => (
            <input
              key={index}
              ref={(element) => { inputRefs.current[index] = element }}
              className="h-14 min-w-0 rounded-lg border border-stone-200 bg-stone-50 text-center text-xl font-bold text-emerald-950 outline-none transition focus:border-emerald-500 focus:bg-white focus:ring-2 focus:ring-emerald-200 sm:h-16 sm:text-2xl"
              value={digit}
              onChange={(event) => updateDigit(index, event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Backspace' && !digit && index > 0) inputRefs.current[index - 1]?.focus()
              }}
              inputMode="numeric"
              autoComplete={index === 0 ? 'one-time-code' : 'off'}
              aria-label={`Chữ số ${index + 1}`}
              maxLength={1}
              autoFocus={index === 0}
              disabled={submitting}
            />
          ))}
        </div>

        <div className="mt-5 flex flex-wrap items-center justify-between gap-3 text-sm">
          <span className="font-medium text-stone-500">
            {secondsLeft > 0 ? `Có thể gửi lại sau ${formatCountdown(secondsLeft)}` : 'Bạn chưa nhận được mã?'}
          </span>
          <button
            type="button"
            className="font-semibold text-emerald-700 disabled:cursor-not-allowed disabled:text-stone-300"
            disabled={secondsLeft > 0 || resending}
            onClick={handleResend}
          >
            {resending ? 'Đang gửi lại...' : 'Gửi lại mã'}
          </button>
        </div>

        {notice && (
          <div className="mt-4 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700">
            {notice}
          </div>
        )}
        {formatError(formError)}

        <button
          className="mt-7 flex h-13 w-full items-center justify-center gap-2 rounded-lg bg-emerald-700 text-sm font-semibold text-white shadow-[0_10px_24px_rgba(4,121,63,0.2)] disabled:cursor-not-allowed disabled:bg-stone-300 disabled:shadow-none"
          disabled={submitting || code.some((digit) => !digit)}
        >
          {submitting ? 'Đang xác minh...' : 'Xác minh tài khoản'}
          {!submitting && <ChevronRight className="size-4" />}
        </button>

        <a className="mt-4 flex justify-center text-sm font-semibold text-emerald-700" href="/forgot-password">
          Đổi email khác
        </a>
      </form>
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

function ResetPasswordSuccessPage() {
  return (
    <RecoveryShell title="Hoàn tất" backHref="/login" step={4}>
      <div className="px-6 py-10 text-center sm:px-10 sm:py-12">
        <span className="mx-auto grid size-20 place-items-center rounded-full bg-emerald-50 text-emerald-600 ring-8 ring-emerald-50/60">
          <CheckCircle2 className="size-11" />
        </span>
        <p className="mt-7 text-xs font-bold uppercase tracking-[0.16em] text-emerald-600">Khôi phục thành công</p>
        <h2 className="mt-2 text-2xl font-semibold tracking-tight text-emerald-950">Mật khẩu đã được cập nhật</h2>
        <p className="mx-auto mt-3 max-w-md text-sm font-medium leading-6 text-stone-500">
          Bạn có thể dùng mật khẩu mới để đăng nhập và tiếp tục đặt sân cùng Courtly.
        </p>
        <a className="mt-8 flex h-13 w-full items-center justify-center gap-2 rounded-lg bg-emerald-700 text-sm font-semibold text-white shadow-[0_10px_24px_rgba(4,121,63,0.2)]" href="/login">
          Quay lại đăng nhập
          <ChevronRight className="size-4" />
        </a>
      </div>
    </RecoveryShell>
  )
}

export { ForgotPasswordPage, ResetPasswordPage, ResetPasswordSuccessPage, VerifyRecoveryPage }
