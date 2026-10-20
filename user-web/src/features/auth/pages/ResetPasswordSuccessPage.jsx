import { ArrowLeft, Check, CheckCircle2, ChevronRight } from 'lucide-react'
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

/** Đếm ngược còn lại của một mốc thời gian đã lưu, tính lại theo đồng hồ thật. */
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

export default ResetPasswordSuccessPage
