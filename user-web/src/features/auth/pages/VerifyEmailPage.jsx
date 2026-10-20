import { useEffect, useRef, useState } from 'react'
import { ArrowLeft, ChevronRight, MailCheck } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { resendVerification as resendVerificationApi, verifyEmail as verifyEmailApi } from '../../../api/auth'
import { clearPendingVerification, readPendingVerification, saveCachedUser, savePendingVerification } from '../../../shared/session'
import { formatError } from '../../../shared/formatError'

function secondsUntil(timestamp) {
  return Math.max(0, Math.ceil((timestamp - Date.now()) / 1000))
}

function formatCountdown(totalSeconds) {
  const minutes = Math.floor(totalSeconds / 60).toString().padStart(2, '0')
  const seconds = (totalSeconds % 60).toString().padStart(2, '0')
  return `${minutes}:${seconds}`
}

function VerifyEmailPage() {
  const pending = readPendingVerification()
  const [code, setCode] = useState(Array(6).fill(''))
  const [secondsLeft, setSecondsLeft] = useState(
    () => secondsUntil((pending?.sentAt ?? 0) + (pending?.resendAfterSeconds ?? 0) * 1000),
  )
  const [submitting, setSubmitting] = useState(false)
  const [resending, setResending] = useState(false)
  const [formError, setFormError] = useState('')
  const [notice, setNotice] = useState('')
  const inputRefs = useRef([])

  useEffect(() => {
    if (secondsLeft <= 0) return undefined
    const timer = window.setInterval(() => setSecondsLeft((value) => Math.max(0, value - 1)), 1000)
    return () => window.clearInterval(timer)
  }, [secondsLeft])

  if (!pending?.email) {
    return (
      <main className="login-bg relative grid min-h-screen place-items-center px-4 text-emerald-950">
        <div className="w-full max-w-[460px] overflow-hidden rounded-xl bg-white p-8 text-center shadow-[0_18px_44px_rgba(4,72,42,0.24)]">
          <span className="mx-auto grid size-14 place-items-center rounded-full bg-emerald-50 text-emerald-700">
            <MailCheck className="size-7" />
          </span>
          <h1 className="mt-5 text-xl font-semibold tracking-tight">Chưa có yêu cầu nào</h1>
          <p className="mt-2 text-sm font-medium leading-6 text-stone-500">
            Bạn cần đăng ký tài khoản trước, hoặc đăng nhập để hệ thống gửi lại mã xác minh.
          </p>
          <a
            className="mt-6 flex h-12 items-center justify-center gap-2 rounded-lg bg-emerald-700 text-sm font-semibold text-white"
            href="/register"
          >
            Về trang đăng ký
            <ChevronRight className="size-4" />
          </a>
        </div>
      </main>
    )
  }

  const maskedEmail = maskRecoveryDestination('email', pending.email)

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
      const verifiedUser = await verifyEmailApi({ email: pending.email, code: value })
      saveCachedUser(verifiedUser)
      clearPendingVerification()
      window.location.href = '/'
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : 'Đã có lỗi xảy ra, vui lòng thử lại.')
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
      const result = await resendVerificationApi({ email: pending.email })
      savePendingVerification({
        email: pending.email,
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
    <main className="login-bg relative min-h-screen overflow-hidden px-4 pb-10 pt-6 text-emerald-950 sm:px-6">
      <header className="relative z-10 mx-auto flex max-w-[620px] items-center justify-center">
        <a
          href="/register"
          className="absolute left-0 top-0 grid size-9 place-items-center rounded-full bg-white/10 text-white backdrop-blur sm:left-5"
          aria-label="Quay lại đăng ký"
        >
          <ArrowLeft className="size-5 stroke-[3]" />
        </a>
        <h1 className="text-[20px] font-semibold leading-9 text-white">Xác minh email</h1>
      </header>

      <section className="relative z-10 mx-auto flex min-h-[calc(100svh-72px)] max-w-[560px] flex-col justify-center py-10">
        <div className="overflow-hidden rounded-xl border border-white/20 bg-white shadow-[0_24px_60px_rgba(4,72,42,0.28)]">
          <div className="border-b border-emerald-100 bg-[linear-gradient(135deg,#effcf5_0%,#ffffff_68%)] px-6 py-6 sm:px-8">
            <span className="grid size-12 place-items-center rounded-lg bg-emerald-700 text-white shadow-[0_10px_24px_rgba(4,121,63,0.18)]">
              <MailCheck className="size-6" />
            </span>
            <p className="mt-5 text-xs font-bold uppercase tracking-[0.16em] text-emerald-600">
              Bước cuối · Kích hoạt tài khoản
            </p>
            <h2 className="mt-1.5 text-2xl font-semibold tracking-tight text-emerald-950">
              Nhập mã gồm 6 chữ số
            </h2>
            <p className="mt-2 max-w-lg text-sm font-medium leading-6 text-stone-500">
              Mã xác minh đã được gửi tới <span className="font-semibold text-emerald-800">{maskedEmail}</span>.
              Tài khoản chỉ đăng nhập được sau khi xác minh.
            </p>
          </div>

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
              {submitting ? 'Đang xác minh...' : 'Kích hoạt tài khoản'}
              {!submitting && <ChevronRight className="size-4" />}
            </button>

            <a className="mt-4 flex justify-center text-sm font-semibold text-emerald-700" href="/register">
              Đăng ký bằng email khác
            </a>
          </form>
        </div>
      </section>
    </main>
  )
}

export default VerifyEmailPage
