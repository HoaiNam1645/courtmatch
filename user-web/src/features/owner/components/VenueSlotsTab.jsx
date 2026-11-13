import { useState } from 'react'
import { AlertTriangle } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { fetchSlotConfig, updateSlotStep } from '../../../api/ownerSlotConfig'
import { useApiResource } from '../../../hooks/useApiResource'
import { FormAlert } from '../../../shared/FormFeedback'
import { classNames } from '../../../shared/utils'
import { dayLabel } from '../dayLabels'
import { primaryButtonClass } from '../ownerStyles'
import { OwnerCard, OwnerError, OwnerLoading } from './OwnerUi'

const STEP_LABEL = { 30: '30 phút', 60: '60 phút (1 giờ)' }

function SlotConfigForm({ venueId, config, onSaved }) {
  const [step, setStep] = useState(config.stepMinutes)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState('')
  const missing = config.days.filter((day) => Object.keys(day.unpricedSlots).length > 0)

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting || step === config.stepMinutes) return
    setSubmitting(true)
    setFormError('')
    try {
      await updateSlotStep(venueId, step)
      onSaved(`Đã đổi bước khung giờ sang ${step} phút.`)
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : 'Đã có lỗi xảy ra, vui lòng thử lại.')
      setSubmitting(false)
    }
  }

  return (
    <div className="grid gap-5">
      <form noValidate onSubmit={handleSubmit}>
        <OwnerCard
          title="Bước khung giờ"
          description="Khung giờ đặt sân được sinh tự động từ giờ mở cửa và bảng giá theo bước này. Booking đã tạo không bị ảnh hưởng."
        >
          <fieldset>
            <legend className="sr-only">Bước khung giờ</legend>
            <div className="flex flex-wrap gap-3">
              {config.allowedSteps.map((value) => (
                <label key={value} className={classNames('flex h-11 cursor-pointer items-center gap-2 rounded-lg border px-4 text-sm font-semibold', step === value ? 'border-emerald-700 bg-emerald-50 text-emerald-900' : 'border-stone-300 text-stone-600')}>
                  <input type="radio" name="slotStep" className="size-4 accent-emerald-700" checked={step === value} onChange={() => setStep(value)} />
                  {STEP_LABEL[value] ?? `${value} phút`}
                </label>
              ))}
            </div>
          </fieldset>
          <div className="mt-4"><FormAlert message={formError} /></div>
          <div className="mt-4 flex justify-end">
            <button type="submit" className={primaryButtonClass} disabled={submitting || step === config.stepMinutes}>
              {submitting ? 'Đang lưu...' : 'Lưu bước khung giờ'}
            </button>
          </div>
        </OwnerCard>
      </form>

      {missing.length > 0 && (
        <section className="rounded-xl border border-amber-200 bg-amber-50 p-5" role="status" data-testid="unpriced-warning">
          <div className="flex items-start gap-3">
            <AlertTriangle className="mt-0.5 size-5 shrink-0 text-amber-600" />
            <div>
              <h3 className="text-sm font-semibold text-amber-900">Có khung giờ chưa có giá</h3>
              <p className="mt-1 text-sm font-medium text-amber-800">Người chơi chưa đặt được các khung này. Bổ sung ở tab “Bảng giá”.</p>
              <ul className="mt-2 grid gap-1 text-sm font-medium text-amber-900">
                {missing.map((day) => (
                  <li key={day.dayOfWeek}>{dayLabel(day.dayOfWeek)}: {Object.entries(day.unpricedSlots).map(([code, count]) => `sân ${code} thiếu ${count} khung`).join(', ')}</li>
                ))}
              </ul>
            </div>
          </div>
        </section>
      )}

      <OwnerCard title="Xem trước khung giờ" description={`Số khung giờ mỗi sân con theo bước ${config.stepMinutes} phút.`}>
        <ul className="grid gap-2 sm:grid-cols-2" data-testid="slot-preview">
          {config.days.map((day) => (
            <li key={day.dayOfWeek} className="flex items-center justify-between rounded-lg border border-emerald-100 px-3 py-2 text-sm" data-testid={`slot-preview-${day.dayOfWeek}`}>
              <span className="font-semibold text-emerald-950">{dayLabel(day.dayOfWeek)}</span>
              {day.closed
                ? <span className="font-medium text-stone-500">Nghỉ</span>
                : <span className="font-medium text-stone-600">{day.openTime}–{day.closeTime} · <span className="font-semibold text-emerald-700">{day.slotCount} khung</span></span>}
            </li>
          ))}
        </ul>
      </OwnerCard>
    </div>
  )
}

/** 2.3.9 — chọn bước sinh khung giờ đặt sân. */
function VenueSlotsTab({ venue, onSaved }) {
  const { status, data, error, reload } = useApiResource(() => fetchSlotConfig(venue.id), [venue.id])
  return (
    <>
      {status === 'loading' && <OwnerLoading rows={2} />}
      {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
      {status === 'success' && <SlotConfigForm venueId={venue.id} config={data} onSaved={onSaved} />}
    </>
  )
}

export { VenueSlotsTab }
