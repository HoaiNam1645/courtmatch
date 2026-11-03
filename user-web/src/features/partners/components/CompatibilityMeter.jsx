function CompatibilityMeter({ icon: Icon, label, value, accentClass }) {
  return (
    <div className="rounded-xl border border-emerald-100 bg-white px-3.5 py-3">
      <div className="flex items-center justify-between gap-3">
        <span className="flex items-center gap-2 text-xs font-semibold text-stone-600">
          <Icon className="size-4 text-emerald-600" />
          {label}
        </span>
        <span className="text-xs font-bold tabular-nums text-emerald-950">{value.toFixed(0)}%</span>
      </div>
      <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-emerald-50">
        <div className={`h-full rounded-full ${accentClass}`} style={{ width: `${value}%` }} />
      </div>
    </div>
  )
}

export { CompatibilityMeter }
