import { CalendarDays } from 'lucide-react'

function BookingEmptyState({ title, description, actionLabel = 'Tìm sân ngay', actionHref = '/' }) {
  return (
    <div className="rounded-xl border border-emerald-100 bg-white px-6 py-12 text-center shadow-[0_12px_34px_rgba(24,65,44,0.08)]">
      <span className="mx-auto grid size-16 place-items-center rounded-full bg-emerald-50 text-emerald-700"><CalendarDays className="size-8" /></span>
      <h2 className="mt-5 text-xl font-semibold text-emerald-950">{title}</h2>
      <p className="mx-auto mt-2 max-w-md text-sm font-medium leading-6 text-stone-500">{description}</p>
      <a className="mt-6 inline-flex h-11 items-center justify-center rounded-lg bg-emerald-700 px-6 text-sm font-semibold text-white" href={actionHref}>{actionLabel}</a>
    </div>
  )
}

export { BookingEmptyState }
