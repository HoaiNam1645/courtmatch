import { useState } from 'react'
import { ArrowLeft, ChevronDown, CircleX, Eye, EyeOff } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { register as registerApi } from '../../../api/auth'
import { savePendingVerification } from '../../../shared/session'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'

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

export default RegisterPage
