import { useEffect, useRef, useState } from 'react'
import { ArrowLeft, ChevronDown, ChevronRight, CircleX, Eye, EyeOff, Mail, MailCheck } from 'lucide-react'
import { ApiError } from '../../api/client'
import { login as loginApi, register as registerApi, resendVerification as resendVerificationApi, verifyEmail as verifyEmailApi } from '../../api/auth'
import { clearPendingVerification, readPendingVerification, saveCachedUser, savePendingVerification } from '../../shared/session'
import { FieldError, FormAlert } from '../../shared/FormFeedback'
import { formatError } from '../../shared/formatError'
import { classNames } from '../../shared/utils'

function GoogleMark() {
  return (
    <span className="relative grid size-5 place-items-center rounded-full bg-white text-[15px] font-bold">
      <span className="text-[#4285f4]">G</span>
    </span>
  )
}

function LoginPage() {
  const [method, setMethod] = useState('phone')
  const [showPassword, setShowPassword] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})

  async function handleLogin(event) {
    event.preventDefault()
    if (submitting) return

    const formData = new FormData(event.currentTarget)
    const emailOrPhone = (method === 'phone'
      ? formData.get('courtly_login_phone')
      : formData.get('courtly_login_email'))?.toString().trim() ?? ''
    const password = formData.get('courtly_login_password')?.toString() ?? ''

    // Kiểm tra phía client chỉ để báo sớm; backend vẫn kiểm tra lại toàn bộ.
    const nextFieldErrors = {}
    if (!emailOrPhone) {
      nextFieldErrors.emailOrPhone = method === 'phone' ? 'Vui lòng nhập số điện thoại' : 'Vui lòng nhập email'
    }
    if (!password) nextFieldErrors.password = 'Vui lòng nhập mật khẩu'
    if (Object.keys(nextFieldErrors).length > 0) {
      setFieldErrors(nextFieldErrors)
      setFormError('')
      return
    }

    setSubmitting(true)
    setFormError('')
    setFieldErrors({})

    try {
      const user = await loginApi({ emailOrPhone, password })
      saveCachedUser(user)
      window.location.href = '/'
    } catch (error) {
      if (error instanceof ApiError) {
        // Tài khoản đã đăng ký nhưng chưa xác minh email: đưa thẳng sang màn hình
        // nhập mã thay vì báo lỗi rồi bỏ mặc người dùng không biết làm gì tiếp.
        if (error.code === 'EMAIL_NOT_VERIFIED') {
          savePendingVerification({ email: emailOrPhone, sentAt: 0, resendAfterSeconds: 0 })
          window.location.href = '/register/verify'
          return
        }
        setFieldErrors(error.fieldErrors ?? {})
        setFormError(error.message)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
      setSubmitting(false)
    }
  }

  return (
    <main className="login-bg relative min-h-screen overflow-hidden px-4 pb-8 pt-6 text-emerald-950 sm:px-6">
      <header className="relative z-10 flex items-center justify-center">
        <a
          href="/"
          className="absolute left-0 top-0 grid size-8 place-items-center text-white sm:left-5"
          aria-label="Quay lại trang chủ"
        >
          <ArrowLeft className="size-6 stroke-[3]" />
        </a>
        <h1 className="text-[20px] font-semibold leading-8 text-white">Đăng nhập</h1>
      </header>

      <section className="relative z-10 mx-auto flex min-h-[calc(100svh-64px)] max-w-[600px] flex-col items-center justify-start pt-[96px]">
        <div className="w-full">
          <div className="overflow-hidden rounded-md bg-white shadow-[0_18px_44px_rgba(4,72,42,0.24)]">
            <div className="login-tabs grid h-[58px] grid-cols-2 bg-[#f4f4f4] text-[18px] font-semibold">
              {[
                { id: 'phone', label: 'Số điện thoại' },
                { id: 'email', label: 'Email' },
              ].map((item) => {
                const active = method === item.id
                return (
                  <button
                    key={item.id}
                    className={classNames(
                      'login-tab relative flex items-center justify-center transition',
                      active && 'login-tab-active z-10 bg-white text-[#0c5130]',
                      !active && 'login-tab-muted text-stone-400',
                      !active && item.id === 'phone' && 'login-tab-muted-phone',
                      !active && item.id === 'email' && 'login-tab-muted-email',
                    )}
                    onClick={() => setMethod(item.id)}
                  >
                    <span className="relative z-10">{item.label}</span>
                  </button>
                )
              })}
            </div>

            <form className="grid gap-6 px-6 pb-8 pt-9 sm:px-7" autoComplete="off" onSubmit={handleLogin}>
              {method === 'phone' ? (
                <label className="block">
                  <span className="text-[18px] font-semibold text-[#0c5130]">Số điện thoại của bạn?</span>
                  <div className="mt-3 flex h-[48px] overflow-hidden rounded-md border border-stone-300 bg-white focus-within:border-emerald-700">
                    <span className="flex w-[104px] items-center justify-center gap-2.5 text-sm font-medium text-[#0c5130]">
                      <span className="grid size-5 place-items-center rounded-full bg-[#e21a2c] text-[11px] text-yellow-300">
                        ★
                      </span>
                      <span>+ 84</span>
                      <ChevronDown className="size-4 text-stone-400" />
                    </span>
                    <input
                      className="min-w-0 flex-1 px-3 text-base font-normal text-stone-800 outline-none placeholder:text-stone-400"
                      placeholder="Nhập số điện thoại"
                      inputMode="tel"
                      name="courtly_login_phone"
                      type="tel"
                      autoComplete="off"
                    />
                  </div>
                  <FieldError message={fieldErrors.emailOrPhone} />
                </label>
              ) : (
                <label className="block">
                  <span className="text-[18px] font-semibold text-[#0c5130]">Email của bạn? (*)</span>
                  <div className="mt-3 flex h-[48px] items-center gap-3 rounded-md border border-stone-300 bg-white px-4 focus-within:border-emerald-700">
                    <Mail className="size-4 text-[#0c5130]" />
                    <input
                      className="min-w-0 flex-1 text-base font-normal text-stone-800 outline-none placeholder:text-stone-400"
                      placeholder="name@email.com"
                      name="courtly_login_email"
                      type="email"
                      autoComplete="off"
                    />
                  </div>
                  <FieldError message={fieldErrors.emailOrPhone} />
                </label>
              )}

              <label className="block">
                <span className="text-[18px] font-semibold text-[#0c5130]">Mật khẩu (*)</span>
                <div className="mt-3 flex h-[50px] items-center gap-3 rounded-md border border-stone-300 bg-white px-4 focus-within:border-emerald-700">
                  <input
                    className="min-w-0 flex-1 text-base font-normal text-stone-800 outline-none placeholder:text-stone-400"
                    placeholder="Nhập mật khẩu (*)"
                    name="courtly_login_password"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="new-password"
                  />
                  <button
                    type="button"
                    className="grid size-9 place-items-center text-[#0b7b47]"
                    onClick={() => setShowPassword((current) => !current)}
                    aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                  >
                    {showPassword ? <EyeOff className="size-5" /> : <Eye className="size-5" />}
                  </button>
                </div>
                <FieldError message={fieldErrors.password} />
              </label>

              <FormAlert message={formError} />

              <button
                type="submit"
                disabled={submitting}
                className="mt-1 h-[50px] rounded-md bg-[#04793f] text-base font-semibold uppercase tracking-normal text-white hover:bg-[#046d39] disabled:cursor-not-allowed disabled:opacity-60"
              >
                {submitting ? 'Đang đăng nhập...' : 'Đăng nhập'}
              </button>

              <div className="text-center text-sm font-normal text-[#0c5130]">
                <span>Bạn quên mật khẩu? </span>
                <a className="font-semibold underline underline-offset-2" href="/forgot-password">
                  Quên mật khẩu
                </a>
              </div>
            </form>
          </div>

          <p className="mt-7 text-center text-base font-normal text-white">
            Bạn chưa có tài khoản?{' '}
            <a className="font-semibold" href="/register">
              Đăng ký
            </a>
          </p>

          <div className="mt-6 grid gap-6">
            {/* 2.1.3 chưa triển khai. Vô hiệu hoá thay vì đăng nhập giả. */}
            <button
              type="button"
              disabled
              title="Chức năng đang được phát triển"
              className="flex h-[44px] cursor-not-allowed items-center justify-center gap-3 rounded-md bg-white text-[17px] font-normal text-[#23543a] opacity-60 shadow-[0_8px_22px_rgba(4,72,42,0.12)]"
            >
              <GoogleMark />
              Đăng nhập với Google
            </button>

            <a
              className="login-owner-banner flex min-h-[76px] items-center justify-center rounded-md border border-[#e4b02d] bg-white px-5 text-center text-base leading-7 text-[#e3a528] underline underline-offset-4"
              href="/owner-app"
            >
              Nếu bạn là CHỦ SÂN hoặc NHÂN VIÊN, Bấm vào đây để tải ứng dụng
              <br />
              Courtly - Quản lý sân cầu lông!
            </a>
          </div>
        </div>
      </section>
    </main>
  )
}

function RegisterPage() {
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})

  async function handleRegister(event) {
    event.preventDefault()
    if (submitting) return

    const formData = new FormData(event.currentTarget)
    const payload = {
      fullName: formData.get('courtly_register_name')?.toString().trim() ?? '',
      email: formData.get('courtly_register_email')?.toString().trim() ?? '',
      phone: formData.get('courtly_register_phone')?.toString().trim() ?? '',
      password: formData.get('courtly_register_password')?.toString() ?? '',
      confirmPassword: formData.get('courtly_register_confirm_password')?.toString() ?? '',
    }

    // Các điều kiện dưới đây phải khớp đúng với Bean Validation ở RegisterRequest.
    const nextFieldErrors = {}
    if (payload.fullName.length < 2) {
      nextFieldErrors.fullName = 'Họ và tên từ 2 đến 150 ký tự'
    }
    // Email bắt buộc từ khi có xác minh email: không có email thì không gửi được mã.
    if (!payload.email) {
      nextFieldErrors.email = 'Vui lòng nhập email'
    } else if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(payload.email)) {
      nextFieldErrors.email = 'Email không đúng định dạng'
    }
    if (payload.password.length < 8) {
      nextFieldErrors.password = 'Mật khẩu từ 8 đến 72 ký tự'
    } else if (!/^(?=.*[A-Za-z])(?=.*\d).+$/.test(payload.password)) {
      nextFieldErrors.password = 'Mật khẩu phải có ít nhất một chữ cái và một chữ số'
    }
    if (payload.password !== payload.confirmPassword) {
      nextFieldErrors.passwordConfirmed = 'Mật khẩu nhập lại không khớp'
    }
    if (Object.keys(nextFieldErrors).length > 0) {
      setFieldErrors(nextFieldErrors)
      setFormError('')
      return
    }

    setSubmitting(true)
    setFormError('')
    setFieldErrors({})

    try {
      const result = await registerApi(payload)
      // Đăng ký chưa cấp token: tài khoản chờ xác minh email.
      savePendingVerification({
        email: result.email,
        sentAt: Date.now(),
        resendAfterSeconds: result.resendAfterSeconds,
        codeExpiresAt: Date.now() + result.expiresInSeconds * 1000,
      })
      window.location.href = '/register/verify'
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
    <main className="login-bg relative min-h-screen overflow-hidden px-4 pb-8 pt-6 text-emerald-950 sm:px-6">
      <header className="relative z-10 flex items-center justify-center">
        <a
          href="/login"
          className="absolute left-0 top-0 grid size-8 place-items-center text-white sm:left-5"
          aria-label="Quay lại đăng nhập"
        >
          <ArrowLeft className="size-6 stroke-[3]" />
        </a>
        <h1 className="text-[20px] font-semibold leading-8 text-white">Đăng ký</h1>
      </header>

      <section className="relative z-10 mx-auto flex min-h-[calc(100svh-64px)] max-w-[560px] flex-col items-center justify-start pt-[88px]">
        <div className="w-full rounded-md bg-white px-6 pb-9 pt-8 shadow-[0_18px_44px_rgba(4,72,42,0.24)] sm:px-7">
          <form className="grid gap-5" autoComplete="off" onSubmit={handleRegister}>
            <label className="block">
              <span className="text-[18px] font-semibold text-[#0c5130]">Số điện thoại của bạn?</span>
              <div className="mt-3 flex h-[48px] overflow-hidden rounded-md border border-stone-300 bg-white focus-within:border-emerald-700">
                <span className="flex w-[104px] items-center justify-center gap-2.5 text-sm font-medium text-[#0c5130]">
                  <span className="grid size-5 place-items-center rounded-full bg-[#e21a2c] text-[11px] text-yellow-300">
                    ★
                  </span>
                  <span>+ 84</span>
                  <ChevronDown className="size-4 text-stone-400" />
                </span>
                <input
                  className="min-w-0 flex-1 px-3 text-base font-normal text-stone-800 outline-none placeholder:text-stone-400"
                  placeholder="Nhập số điện thoại"
                  inputMode="tel"
                  name="courtly_register_phone"
                  type="tel"
                  autoComplete="off"
                />
              </div>
            <FieldError message={fieldErrors.phone} />
            </label>

            <label className="block">
              <span className="text-[18px] font-semibold text-[#0c5130]">Email của bạn?</span>
              <div className="mt-3 flex h-[48px] items-center gap-3 rounded-md border border-stone-300 bg-white px-3 focus-within:border-emerald-700">
                <input
                  className="min-w-0 flex-1 text-base font-normal text-stone-800 outline-none placeholder:text-stone-400"
                  placeholder="Nhập email của bạn"
                  name="courtly_register_email"
                  type="email"
                  autoComplete="off"
                />
                <CircleX className="size-5 shrink-0 fill-[#08793f] text-white" />
              </div>
            <FieldError message={fieldErrors.email} />
            </label>

            <label className="block">
              <span className="text-[18px] font-semibold text-[#0c5130]">Tên đầy đủ (*)</span>
              <div className="mt-3 flex h-[48px] items-center gap-3 rounded-md border border-stone-300 bg-white px-3 focus-within:border-emerald-700">
                <input
                  className="min-w-0 flex-1 text-base font-normal text-stone-800 outline-none placeholder:text-stone-400"
                  placeholder="Nhập họ và tên"
                  name="courtly_register_name"
                  type="text"
                  autoComplete="off"
                />
                <CircleX className="size-5 shrink-0 fill-[#08793f] text-white" />
              </div>
            <FieldError message={fieldErrors.fullName} />
            </label>

            <label className="block">
              <span className="text-[18px] font-semibold text-[#0c5130]">Mật khẩu (*)</span>
              <div className="mt-3 flex h-[48px] items-center gap-3 rounded-md border border-stone-300 bg-white px-3 focus-within:border-emerald-700">
                <input
                  className="min-w-0 flex-1 text-base font-normal text-stone-800 outline-none placeholder:text-stone-400"
                  placeholder="Nhập mật khẩu (*)"
                  name="courtly_register_password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="new-password"
                />
                <button
                  type="button"
                  className="grid size-9 shrink-0 place-items-center text-[#0b7b47]"
                  onClick={() => setShowPassword((current) => !current)}
                  aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                >
                  {showPassword ? <EyeOff className="size-5" /> : <Eye className="size-5" />}
                </button>
              </div>
            <FieldError message={fieldErrors.password} />
            </label>

            <label className="block">
              <span className="text-[18px] font-semibold text-[#0c5130]">Nhập mật khẩu</span>
              <div className="mt-3 flex h-[48px] items-center gap-3 rounded-md border border-stone-300 bg-white px-3 focus-within:border-emerald-700">
                <input
                  className="min-w-0 flex-1 text-base font-normal text-stone-800 outline-none placeholder:text-stone-400"
                  placeholder="Nhập lại mật khẩu"
                  name="courtly_register_confirm_password"
                  type={showConfirmPassword ? 'text' : 'password'}
                  autoComplete="new-password"
                />
                <button
                  type="button"
                  className="grid size-9 shrink-0 place-items-center text-[#0b7b47]"
                  onClick={() => setShowConfirmPassword((current) => !current)}
                  aria-label={showConfirmPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                >
                  {showConfirmPassword ? <EyeOff className="size-5" /> : <Eye className="size-5" />}
                </button>
              </div>
            <FieldError message={fieldErrors.passwordConfirmed} />
            </label>

            <FormAlert message={formError} />

            <button
              type="submit"
              disabled={submitting}
              className="mt-7 h-[50px] rounded-md bg-[#04793f] text-base font-semibold uppercase tracking-normal text-white hover:bg-[#046d39] disabled:cursor-not-allowed disabled:opacity-60"
            >
              {submitting ? 'Đang tạo tài khoản...' : 'Đăng ký'}
            </button>

            <p className="pt-3 text-center text-sm font-normal text-stone-600">
              Bạn đã có tài khoản?{' '}
              <a className="font-semibold text-[#04793f]" href="/login">
                Đăng nhập
              </a>
            </p>
          </form>
        </div>
      </section>
    </main>
  )
}

/**
 * Nhập mã xác minh email sau khi đăng ký (2.1.1).
 *
 * Tài khoản mới ở trạng thái chờ, chưa đăng nhập được. Nhập đúng mã thì backend kích hoạt
 * tài khoản và cấp token luôn, nên trang này kết thúc bằng việc vào thẳng trang chủ.
 */
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

export { LoginPage, RegisterPage, VerifyEmailPage }
