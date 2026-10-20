import { useEffect, useRef, useState } from 'react'
import { ArrowLeft, Check, ChevronRight, KeyRound, ShieldCheck } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { requestPasswordReset as requestPasswordResetApi, verifyPasswordResetCode as verifyPasswordResetCodeApi } from '../../../api/auth'
import { maskRecoveryDestination, readRecoveryRequest, saveRecoveryRequest } from '../../../shared/session'
import { formatError } from '../../../shared/formatError'
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

export default VerifyRecoveryPage
