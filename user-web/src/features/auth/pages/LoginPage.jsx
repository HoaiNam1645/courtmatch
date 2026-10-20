import { useState } from 'react'
import { ArrowLeft, ChevronDown, Eye, EyeOff, Mail } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { login as loginApi } from '../../../api/auth'
import { saveCachedUser, savePendingVerification } from '../../../shared/session'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { classNames } from '../../../shared/utils'

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

export default LoginPage
