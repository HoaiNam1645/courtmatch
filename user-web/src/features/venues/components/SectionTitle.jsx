import { ChevronRight } from 'lucide-react'

function SectionTitle({ eyebrow, title, action = 'Xem tất cả', icon: Icon, href }) {
  const actionContent = (
    <>
      {action}
      <ChevronRight className="size-4" />
    </>
  )

  return (
    <div className="flex flex-wrap items-end justify-between gap-3">
      <div>
        <p className="inline-flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.16em] text-emerald-600">
          {Icon && <Icon className="size-3.5" />}
          {eyebrow}
        </p>
        <h2 className="mt-1 text-xl font-semibold tracking-normal text-emerald-950 md:text-2xl">
          {title}
        </h2>
      </div>
      {href ? (
        <a
          className="inline-flex items-center gap-2 rounded-md bg-emerald-50 px-4 py-2.5 text-xs font-semibold text-emerald-700 shadow-sm ring-1 ring-emerald-100"
          href={href}
        >
          {actionContent}
        </a>
      ) : (
        <button className="inline-flex items-center gap-2 rounded-md bg-emerald-50 px-4 py-2.5 text-xs font-semibold text-emerald-700 shadow-sm ring-1 ring-emerald-100">
          {actionContent}
        </button>
      )}
    </div>
  )
}

export { SectionTitle }
