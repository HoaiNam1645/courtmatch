import { AlertTriangle, Inbox, RotateCcw } from 'lucide-react'
import { classNames } from '../../../shared/utils'

/** Khối nội dung chuẩn của khu quản lý chủ sân. */
function OwnerCard({ title, description, actions, children, className }) {
  return (
    <section className={classNames('min-w-0 rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_12px_34px_rgba(24,65,44,0.07)] md:p-6', className)}>
      {(title || actions) && (
        <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            {title && <h2 className="text-lg font-semibold text-emerald-950">{title}</h2>}
            {description && <p className="mt-1 text-sm font-medium text-stone-500">{description}</p>}
          </div>
          {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
        </div>
      )}
      {children}
    </section>
  )
}

function OwnerLoading({ rows = 2 }) {
  return (
    <div className="grid gap-4" aria-busy="true" aria-label="Đang tải">
      {Array.from({ length: rows }, (_, index) => (
        <div key={index} className="h-28 animate-pulse rounded-xl bg-emerald-100/60" />
      ))}
    </div>
  )
}

function OwnerError({ message, onRetry }) {
  return (
    <div role="alert" className="rounded-xl border border-red-200 bg-red-50 p-6 text-center">
      <AlertTriangle className="mx-auto size-7 text-red-500" />
      <p className="mt-3 text-sm font-medium text-red-700">{message || 'Không tải được dữ liệu.'}</p>
      {onRetry && (
        <button type="button" className="mt-4 inline-flex h-10 items-center gap-2 rounded-lg bg-emerald-700 px-5 text-sm font-semibold text-white" onClick={onRetry}>
          <RotateCcw className="size-4" />
          Thử lại
        </button>
      )}
    </div>
  )
}

function OwnerEmpty({ title, description, action }) {
  return (
    <div className="rounded-xl border border-dashed border-emerald-200 bg-white px-6 py-10 text-center">
      <Inbox className="mx-auto size-8 text-emerald-600" />
      <h3 className="mt-3 text-base font-semibold text-emerald-950">{title}</h3>
      {description && <p className="mx-auto mt-1 max-w-md text-sm font-medium text-stone-500">{description}</p>}
      {action && <div className="mt-5 flex justify-center">{action}</div>}
    </div>
  )
}

export { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading }
