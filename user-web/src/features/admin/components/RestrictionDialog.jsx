import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { parseThreshold, RESTRICTION_LEVEL_META, restrictionProblems, updateDebtRestrictions } from '../../../api/adminFeePolicies'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { classNames } from '../../../shared/utils'
import { inputClass, primaryButtonClass, secondaryButtonClass } from '../../owner/ownerStyles'

const toForm = (restriction) => ({
  level: restriction.level,
  enabled: restriction.enabled,
  minOverdueAmount: restriction.minOverdueAmount == null ? '' : String(restriction.minOverdueAmount),
  minOverdueDays: restriction.minOverdueDays == null ? '' : String(restriction.minOverdueDays),
})

/** Sửa ngưỡng hạn chế chủ sân có công nợ quá hạn (2.3.40): bật/tắt từng mức, ngưỡng số tiền và số ngày. */
function RestrictionDialog({ restrictions, onClose, onSaved }) {
  const [levels, setLevels] = useState(restrictions.map(toForm))
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    function onKey(event) {
      if (event.key === 'Escape' && !busy) onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose, busy])

  function change(level, patch) {
    setLevels((current) => current.map((item) => (item.level === level ? { ...item, ...patch } : item)))
    setErrors({})
  }

  const unchanged = levels.every((level, index) => {
    const saved = restrictions[index]
    return level.enabled === saved.enabled && parseThreshold(level.minOverdueAmount) === saved.minOverdueAmount && parseThreshold(level.minOverdueDays) === saved.minOverdueDays
  })

  async function submit(event) {
    event.preventDefault()
    if (busy) return
    const problems = restrictionProblems(levels)
    setErrors(problems)
    setFormError('')
    if (Object.keys(problems).length > 0) return
    setBusy(true)
    try {
      onSaved(await updateDebtRestrictions(levels), 'Đã cập nhật ngưỡng hạn chế công nợ quá hạn.')
    } catch (failure) {
      if (failure instanceof ApiError) {
        setErrors(failure.fieldErrors ?? {})
        setFormError(failure.message)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
      setBusy(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-black/40 p-4" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose() }}>
      <form role="dialog" aria-modal="true" aria-label="Sửa ngưỡng hạn chế công nợ" noValidate onSubmit={submit} className="w-full max-w-xl rounded-xl bg-white p-5 shadow-2xl" data-testid="restriction-dialog">
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-lg font-semibold text-slate-900">Ngưỡng hạn chế công nợ quá hạn</h2>
          <button type="button" className="grid size-9 place-items-center rounded-lg text-slate-500 hover:bg-slate-100" aria-label="Đóng" onClick={onClose} disabled={busy}><X className="size-5" /></button>
        </div>
        <p className="mt-2 text-xs font-medium text-slate-500">Đạt một trong hai ngưỡng là áp dụng. Để trống ô nào thì không dùng ngưỡng đó. Mức 2 phải nghiêm hơn hoặc bằng mức 1.</p>
        <div className="mt-4 grid gap-4">
          {levels.map((level) => {
            const meta = RESTRICTION_LEVEL_META[level.level]
            const prefix = `level${level.level}.`
            return (
              <fieldset key={level.level} className={classNames('rounded-xl border p-4', level.enabled ? 'border-slate-300' : 'border-slate-200 bg-slate-50/60')} data-testid={`restriction-form-${level.level}`}>
                <legend className="px-1 text-sm font-semibold text-slate-900">{meta.title}</legend>
                <label className="flex cursor-pointer items-center gap-2 text-sm font-semibold text-slate-800">
                  <input type="checkbox" checked={level.enabled} onChange={(event) => change(level.level, { enabled: event.target.checked })} data-testid={`restriction-enabled-${level.level}`} />Bật mức này
                </label>
                <p className="mt-1 text-xs font-medium text-slate-500">{meta.effect}</p>
                <FieldError message={errors[`${prefix}enabled`]} />
                <div className="mt-3 grid gap-3 sm:grid-cols-2">
                  <label className="block">
                    <span className="text-xs font-semibold text-slate-700">Công nợ quá hạn từ (đồng)</span>
                    <input className={`${inputClass} mt-1`} aria-label={`Mức ${level.level}: ngưỡng số tiền`} inputMode="numeric" value={level.minOverdueAmount} onChange={(event) => change(level.level, { minOverdueAmount: event.target.value })} aria-invalid={Boolean(errors[`${prefix}minOverdueAmount`])} data-testid={`restriction-amount-${level.level}`} />
                    <FieldError message={errors[`${prefix}minOverdueAmount`]} />
                  </label>
                  <label className="block">
                    <span className="text-xs font-semibold text-slate-700">Quá hạn từ (ngày)</span>
                    <input className={`${inputClass} mt-1`} aria-label={`Mức ${level.level}: ngưỡng số ngày`} inputMode="numeric" value={level.minOverdueDays} onChange={(event) => change(level.level, { minOverdueDays: event.target.value })} aria-invalid={Boolean(errors[`${prefix}minOverdueDays`])} data-testid={`restriction-days-${level.level}`} />
                    <FieldError message={errors[`${prefix}minOverdueDays`]} />
                  </label>
                </div>
              </fieldset>
            )
          })}
          <FieldError message={errors.levels} />
          <FormAlert message={formError} />
          <div className="flex justify-end gap-2">
            <button type="button" className={secondaryButtonClass} disabled={busy} onClick={onClose}>Hủy</button>
            <button type="submit" className={primaryButtonClass} disabled={busy || unchanged} data-testid="restriction-submit">{busy ? 'Đang lưu...' : 'Lưu ngưỡng'}</button>
          </div>
        </div>
      </form>
    </div>
  )
}

export { RestrictionDialog }
