import { useState } from 'react'
import { ArrowLeft, Check, ChevronRight, KeyRound, Mail, Phone } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { requestPasswordReset as requestPasswordResetApi } from '../../../api/auth'
import { saveRecoveryRequest } from '../../../shared/session'
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

export default ForgotPasswordPage
