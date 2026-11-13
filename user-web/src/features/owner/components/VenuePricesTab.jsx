import { useState } from 'react'
import { Pencil, Plus } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { COURT_STATUS_META, statusMeta } from '../../../api/ownerVenues'
import { createPriceRule, deactivatePriceRule, fetchPriceRules, updatePriceRule } from '../../../api/ownerPriceRules'
import { useApiResource } from '../../../hooks/useApiResource'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { classNames, formatCurrency } from '../../../shared/utils'
import { DAY_OF_WEEK_LABELS, dayLabel } from '../dayLabels'
import { formatIsoDate, todayInVietnam } from '../ownerDates'
import { dangerButtonClass, inputClass, primaryButtonClass, secondaryButtonClass } from '../ownerStyles'
import { OwnerCard, OwnerError, OwnerLoading } from './OwnerUi'

const emptyRule = () => ({ dayOfWeek: '', startTime: '', endTime: '', pricePerHour: '', effectiveFrom: '', effectiveTo: '' })

/** Cùng điều kiện với PriceRuleInput và OwnerPriceRuleService ở backend. */
function validateRule(values, originalFrom) {
  const errors = {}
  if (!values.startTime) errors.startTime = 'Vui lòng nhập giờ bắt đầu'
  if (!values.endTime) errors.endTime = 'Vui lòng nhập giờ kết thúc'
  else if (values.startTime && values.endTime <= values.startTime) errors.endTime = 'Giờ kết thúc phải sau giờ bắt đầu'
  if (values.pricePerHour === '') errors.pricePerHour = 'Vui lòng nhập giá mỗi giờ'
  else if (!/^\d+$/.test(values.pricePerHour)) errors.pricePerHour = 'Giá là số nguyên đồng, không âm'
  else if (Number(values.pricePerHour) > 10000000) errors.pricePerHour = 'Giá tối đa 10.000.000đ/giờ'
  const from = values.effectiveFrom || originalFrom || todayInVietnam()
  if (values.effectiveFrom && values.effectiveFrom < todayInVietnam() && values.effectiveFrom !== originalFrom) {
    errors.effectiveFrom = 'Ngày áp dụng phải từ hôm nay trở đi'
  }
  if (values.effectiveTo && values.effectiveTo < from) errors.effectiveTo = 'Ngày hết áp dụng phải sau hoặc bằng ngày bắt đầu'
  return errors
}

function RuleForm({ venueId, courtId, rule, onDone, onCancel }) {
  const [values, setValues] = useState(rule
    ? { ...rule, dayOfWeek: rule.dayOfWeek == null ? '' : String(rule.dayOfWeek), pricePerHour: String(rule.pricePerHour), effectiveTo: rule.effectiveTo ?? '' }
    : emptyRule())
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  function set(field, value) {
    setValues((current) => ({ ...current, [field]: value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting) return
    const nextErrors = validateRule(values, rule?.effectiveFrom)
    setErrors(nextErrors)
    setFormError('')
    if (Object.keys(nextErrors).length > 0) return
    setSubmitting(true)
    try {
      if (rule) await updatePriceRule(venueId, courtId, rule.id, values)
      else await createPriceRule(venueId, courtId, values)
      onDone(rule ? 'Đã cập nhật quy tắc giá.' : 'Đã thêm quy tắc giá.')
    } catch (error) {
      if (error instanceof ApiError) {
        setErrors(error.fieldErrors ?? {})
        setFormError(error.message)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
      setSubmitting(false)
    }
  }

  return (
    <form className="mt-3 rounded-lg border border-dashed border-emerald-300 bg-emerald-50/40 p-4" noValidate onSubmit={handleSubmit} data-testid="price-rule-form">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <label className="block">
          <span className="text-xs font-semibold text-stone-600">Áp dụng</span>
          <select className={`${inputClass} mt-1`} aria-label="Ngày áp dụng" value={values.dayOfWeek} onChange={(event) => set('dayOfWeek', event.target.value)}>
            <option value="">Mọi ngày</option>
            {Object.entries(DAY_OF_WEEK_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
          <FieldError message={errors.dayOfWeek} />
        </label>
        <label className="block">
          <span className="text-xs font-semibold text-stone-600">Từ giờ</span>
          <input type="time" className={`${inputClass} mt-1`} aria-label="Từ giờ" value={values.startTime} onChange={(event) => set('startTime', event.target.value)} />
          <FieldError message={errors.startTime} />
        </label>
        <label className="block">
          <span className="text-xs font-semibold text-stone-600">Đến giờ</span>
          <input type="time" className={`${inputClass} mt-1`} aria-label="Đến giờ" value={values.endTime} onChange={(event) => set('endTime', event.target.value)} />
          <FieldError message={errors.endTime} />
        </label>
        <label className="block">
          <span className="text-xs font-semibold text-stone-600">Giá mỗi giờ (đ)</span>
          <input className={`${inputClass} mt-1`} inputMode="numeric" aria-label="Giá mỗi giờ" value={values.pricePerHour} onChange={(event) => set('pricePerHour', event.target.value.replace(/[^\d]/g, ''))} />
          <FieldError message={errors.pricePerHour} />
        </label>
        <label className="block">
          <span className="text-xs font-semibold text-stone-600">Áp dụng từ ngày</span>
          <input type="date" className={`${inputClass} mt-1`} aria-label="Áp dụng từ ngày" value={values.effectiveFrom} onChange={(event) => set('effectiveFrom', event.target.value)} />
          {!errors.effectiveFrom && <span className="mt-1 block text-xs text-stone-500">Bỏ trống = {rule ? 'giữ ngày cũ' : 'hôm nay'}</span>}
          <FieldError message={errors.effectiveFrom} />
        </label>
        <label className="block">
          <span className="text-xs font-semibold text-stone-600">Đến ngày</span>
          <input type="date" className={`${inputClass} mt-1`} aria-label="Đến ngày" value={values.effectiveTo} onChange={(event) => set('effectiveTo', event.target.value)} />
          {!errors.effectiveTo && <span className="mt-1 block text-xs text-stone-500">Bỏ trống = không thời hạn</span>}
          <FieldError message={errors.effectiveTo} />
        </label>
      </div>
      <div className="mt-3"><FormAlert message={formError} /></div>
      <div className="mt-3 flex justify-end gap-2">
        <button type="button" className={secondaryButtonClass} onClick={onCancel}>Hủy</button>
        <button type="submit" className={primaryButtonClass} disabled={submitting}>{submitting ? 'Đang lưu...' : 'Lưu quy tắc giá'}</button>
      </div>
    </form>
  )
}

function CourtPrices({ venueId, court, onChanged }) {
  const [editing, setEditing] = useState(null)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState(null)
  const active = court.rules.filter((rule) => rule.status === 'active')
  const inactive = court.rules.filter((rule) => rule.status !== 'active')
  const meta = statusMeta(COURT_STATUS_META, court.courtStatus)

  async function deactivate(rule) {
    if (busyId) return
    if (!window.confirm(`Ngừng áp dụng giá ${dayLabel(rule.dayOfWeek)} ${rule.startTime}–${rule.endTime}?`)) return
    setBusyId(rule.id)
    setError('')
    try {
      await deactivatePriceRule(venueId, court.courtId, rule.id)
      onChanged('Đã ngừng áp dụng quy tắc giá.')
    } catch (deactivateError) {
      setError(deactivateError?.message ?? 'Không ngừng áp dụng được quy tắc giá.')
      setBusyId(null)
    }
  }

  return (
    <OwnerCard
      title={`${court.courtName} · ${court.courtCode}`}
      actions={<span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', meta.className)}>{meta.label}</span>}
    >
      <div data-testid={`court-prices-${court.courtCode}`}>
        {active.length === 0 && <p className="rounded-lg bg-amber-50 px-4 py-3 text-sm font-medium text-amber-800">Sân con chưa có giá — người chơi chưa đặt được sân này.</p>}
        {active.length > 0 && (
          <ul className="divide-y divide-emerald-50">
            {active.map((rule) => (
              <li key={rule.id} className="py-3" data-testid={`price-rule-${rule.id}`}>
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-sm font-semibold text-emerald-950">{dayLabel(rule.dayOfWeek)} · {rule.startTime}–{rule.endTime}</p>
                    <p className="mt-0.5 text-xs font-medium text-stone-500">
                      Từ {formatIsoDate(rule.effectiveFrom)}{rule.effectiveTo ? ` đến ${formatIsoDate(rule.effectiveTo)}` : ', không thời hạn'}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-base font-semibold text-emerald-700">{formatCurrency(rule.pricePerHour)}/giờ</span>
                    <button type="button" className="grid size-9 place-items-center rounded-lg border border-emerald-200 text-emerald-700 hover:bg-emerald-50" aria-label={`Sửa giá ${dayLabel(rule.dayOfWeek)} ${rule.startTime}`} onClick={() => setEditing(rule.id)}>
                      <Pencil className="size-4" />
                    </button>
                    <button type="button" className={`${dangerButtonClass} h-9 px-3 text-xs`} disabled={busyId === rule.id} onClick={() => deactivate(rule)}>Ngừng</button>
                  </div>
                </div>
                {editing === rule.id && (
                  <RuleForm venueId={venueId} courtId={court.courtId} rule={rule} onCancel={() => setEditing(null)} onDone={onChanged} />
                )}
              </li>
            ))}
          </ul>
        )}
        {error && <div className="mt-3"><FormAlert message={error} /></div>}
        {editing === 'new'
          ? <RuleForm venueId={venueId} courtId={court.courtId} onCancel={() => setEditing(null)} onDone={onChanged} />
          : (
            <button type="button" className={`${secondaryButtonClass} mt-3`} onClick={() => setEditing('new')}>
              <Plus className="size-4" />Thêm khung giá
            </button>
          )}
        {inactive.length > 0 && (
          <details className="mt-4 text-sm">
            <summary className="cursor-pointer font-semibold text-stone-500">Đã ngừng áp dụng ({inactive.length})</summary>
            <ul className="mt-2 grid gap-1 text-stone-500">
              {inactive.map((rule) => (
                <li key={rule.id}>{dayLabel(rule.dayOfWeek)} · {rule.startTime}–{rule.endTime} · {formatCurrency(rule.pricePerHour)}/giờ</li>
              ))}
            </ul>
          </details>
        )}
      </div>
    </OwnerCard>
  )
}

/** 2.3.5 — bảng giá theo sân con, ngày trong tuần, khung giờ và thời gian hiệu lực. */
function VenuePricesTab({ venue, onSaved }) {
  const { status, data, error, reload } = useApiResource(() => fetchPriceRules(venue.id), [venue.id])

  return (
    <div className="grid gap-5">
      <p className="rounded-lg border border-emerald-100 bg-white px-4 py-3 text-sm font-medium text-stone-600">
        Giá mới chỉ áp dụng cho booking tạo sau khi lưu; booking đã tạo giữ nguyên số tiền đã chốt. Các khung giá của cùng một sân con không được chồng lấn.
      </p>
      {status === 'loading' && <OwnerLoading rows={2} />}
      {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
      {status === 'success' && data.map((court) => (
        <CourtPrices key={court.courtId} venueId={venue.id} court={court} onChanged={onSaved} />
      ))}
    </div>
  )
}

export { VenuePricesTab }
