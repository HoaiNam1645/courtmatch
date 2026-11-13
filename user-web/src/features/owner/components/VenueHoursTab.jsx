import { useState } from 'react'
import { AlertTriangle, Copy } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { fetchOperatingHours, updateOperatingHours } from '../../../api/ownerOperatingHours'
import { useApiResource } from '../../../hooks/useApiResource'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { classNames } from '../../../shared/utils'
import { dayLabel } from '../dayLabels'
import { formatDateTime, formatTime } from '../ownerDates'
import { inputClass, primaryButtonClass, secondaryButtonClass } from '../ownerStyles'
import { OwnerCard, OwnerError, OwnerLoading } from './OwnerUi'

/** Cùng điều kiện với OwnerOperatingHoursService ở backend. */
function validateDays(days) {
  const errors = {}
  days.forEach((day, index) => {
    if (day.closed) return
    if (!day.openTime) errors[`days[${index}].openTime`] = 'Vui lòng nhập giờ mở cửa'
    if (!day.closeTime) errors[`days[${index}].closeTime`] = 'Vui lòng nhập giờ đóng cửa'
    else if (day.openTime && day.closeTime <= day.openTime) errors[`days[${index}].closeTime`] = 'Giờ đóng cửa phải sau giờ mở cửa'
  })
  return errors
}

function HoursForm({ venueId, initial, onSaved }) {
  const [days, setDays] = useState(initial.days)
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  function update(index, patch) {
    setDays((current) => current.map((day, position) => (position === index ? { ...day, ...patch } : day)))
  }

  function copyFirstDay() {
    const [first] = days
    setDays((current) => current.map((day) => ({ ...day, closed: first.closed, openTime: first.openTime, closeTime: first.closeTime })))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting) return
    const nextErrors = validateDays(days)
    setErrors(nextErrors)
    setFormError('')
    if (Object.keys(nextErrors).length > 0) return
    setSubmitting(true)
    try {
      await updateOperatingHours(venueId, days)
      onSaved('Đã lưu giờ hoạt động.')
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
    <form noValidate onSubmit={handleSubmit}>
      <OwnerCard
        title="Giờ hoạt động trong tuần"
        description="Khung giờ đặt sân chỉ được tạo trong giờ mở cửa. Thay đổi có hiệu lực ngay với lượt đặt tiếp theo; booking đã tạo không bị hủy."
        actions={<button type="button" className={secondaryButtonClass} onClick={copyFirstDay}><Copy className="size-4" />Áp dụng giờ Thứ hai cho cả tuần</button>}
      >
        <div className="grid gap-3">
          {days.map((day, index) => (
            <div key={day.dayOfWeek} className="grid gap-3 rounded-lg border border-emerald-100 p-3 sm:grid-cols-[120px_120px_minmax(0,1fr)_minmax(0,1fr)] sm:items-start" data-testid={`hours-day-${day.dayOfWeek}`}>
              <div className="flex h-11 items-center gap-2">
                <p className="text-sm font-semibold text-emerald-950">{dayLabel(day.dayOfWeek)}</p>
                {!day.configured && <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[11px] font-bold text-amber-800">Chưa khai báo</span>}
              </div>
              <label className="flex h-11 items-center gap-2 text-sm font-semibold text-emerald-950">
                <input type="checkbox" className="size-4 accent-emerald-700" checked={day.closed} onChange={(event) => update(index, { closed: event.target.checked })} aria-label={`Nghỉ ${dayLabel(day.dayOfWeek)}`} />
                Nghỉ cả ngày
              </label>
              <label className={classNames('block', day.closed && 'opacity-50')}>
                <span className="sr-only">Giờ mở cửa {dayLabel(day.dayOfWeek)}</span>
                <input type="time" className={inputClass} disabled={day.closed} aria-label={`Giờ mở cửa ${dayLabel(day.dayOfWeek)}`} value={day.openTime} onChange={(event) => update(index, { openTime: event.target.value })} />
                <FieldError message={errors[`days[${index}].openTime`]} />
              </label>
              <label className={classNames('block', day.closed && 'opacity-50')}>
                <span className="sr-only">Giờ đóng cửa {dayLabel(day.dayOfWeek)}</span>
                <input type="time" className={inputClass} disabled={day.closed} aria-label={`Giờ đóng cửa ${dayLabel(day.dayOfWeek)}`} value={day.closeTime} onChange={(event) => update(index, { closeTime: event.target.value })} />
                <FieldError message={errors[`days[${index}].closeTime`]} />
              </label>
            </div>
          ))}
        </div>
        <div className="mt-4"><FormAlert message={formError} /></div>
        <div className="mt-4 flex justify-end">
          <button type="submit" className={primaryButtonClass} disabled={submitting}>{submitting ? 'Đang lưu...' : 'Lưu giờ hoạt động'}</button>
        </div>
      </OwnerCard>
    </form>
  )
}

function ConflictWarning({ conflicts }) {
  if (conflicts.length === 0) return null
  return (
    <section className="rounded-xl border border-amber-200 bg-amber-50 p-5" data-testid="hours-conflicts" role="status">
      <div className="flex items-start gap-3">
        <AlertTriangle className="mt-0.5 size-5 shrink-0 text-amber-600" />
        <div className="min-w-0">
          <h3 className="text-sm font-semibold text-amber-900">{conflicts.length} booking chưa chơi nằm ngoài giờ hoạt động hiện tại</h3>
          <p className="mt-1 text-sm font-medium text-amber-800">Các booking này không bị hủy tự động. Hãy liên hệ người chơi hoặc điều chỉnh lại giờ hoạt động.</p>
          <ul className="mt-3 grid gap-1.5 text-sm text-amber-900">
            {conflicts.map((conflict) => (
              <li key={`${conflict.bookingId}-${conflict.startTime}`} className="font-medium">
                <span className="font-semibold">{conflict.bookingCode}</span> · {conflict.courtName} · {formatDateTime(conflict.startTime)}–{formatTime(conflict.endTime)}
              </li>
            ))}
          </ul>
        </div>
      </div>
    </section>
  )
}

/** 2.3.7 — giờ mở/đóng cửa từng ngày trong tuần. */
function VenueHoursTab({ venue, onSaved }) {
  const { status, data, error, reload } = useApiResource(() => fetchOperatingHours(venue.id), [venue.id])
  return (
    <div className="grid gap-5">
      {status === 'loading' && <OwnerLoading rows={2} />}
      {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
      {status === 'success' && (
        <>
          <ConflictWarning conflicts={data.conflicts} />
          <HoursForm venueId={venue.id} initial={data} onSaved={onSaved} />
        </>
      )}
    </div>
  )
}

export { VenueHoursTab }
