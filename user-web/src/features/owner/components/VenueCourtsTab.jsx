import { useState } from 'react'
import { Plus } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { addOwnerCourt, COURT_STATUS_META, statusMeta, updateOwnerCourt } from '../../../api/ownerVenues'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { classNames } from '../../../shared/utils'
import { inputClass, primaryButtonClass, secondaryButtonClass } from '../ownerStyles'
import { emptyCourt, validateCourt } from '../venueFormRules'
import { CourtFields } from './CourtFields'
import { OwnerCard } from './OwnerUi'

function useCourtSubmit(save, onDone) {
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function submit(court, options) {
    if (submitting) return
    const nextErrors = validateCourt(court, options)
    setErrors(nextErrors)
    setFormError('')
    if (Object.keys(nextErrors).length > 0) return
    setSubmitting(true)
    try {
      await save(court)
      onDone()
    } catch (error) {
      if (error instanceof ApiError) {
        setErrors(error.fieldErrors ?? {})
        setFormError(error.message)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return { errors, formError, submitting, submit }
}

function CourtEditor({ venueId, court: initial, options, onSaved }) {
  const [court, setCourt] = useState(initial)
  const { errors, formError, submitting, submit } = useCourtSubmit(
    (value) => updateOwnerCourt(venueId, value),
    () => onSaved(`Đã lưu sân con ${court.courtCode.toUpperCase()}.`),
  )
  const meta = statusMeta(COURT_STATUS_META, initial.status)

  return (
    <form
      className="rounded-lg border border-emerald-100 p-4"
      data-testid={`court-editor-${initial.courtCode}`}
      noValidate
      onSubmit={(event) => {
        event.preventDefault()
        submit(court, options)
      }}
    >
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm font-semibold text-emerald-900">{initial.name} · {initial.courtCode}</p>
        <span data-testid="court-status" className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', meta.className)}>{meta.label}</span>
      </div>
      <CourtFields court={court} onChange={setCourt} errors={errors} options={options} labelSuffix={` ${initial.courtCode}`} />
      <div className="mt-3 flex flex-wrap items-end justify-between gap-3">
        <label className="block w-full sm:w-56">
          <span className="text-xs font-semibold text-stone-600">Trạng thái</span>
          <select className={`${inputClass} mt-1`} aria-label={`Trạng thái sân con ${initial.courtCode}`} value={court.status} onChange={(event) => setCourt({ ...court, status: event.target.value })}>
            {Object.entries(COURT_STATUS_META).map(([value, item]) => <option key={value} value={value}>{item.label}</option>)}
          </select>
          <FieldError message={errors.status} />
        </label>
        <button type="submit" className={secondaryButtonClass} disabled={submitting}>
          {submitting ? 'Đang lưu...' : 'Lưu sân con'}
        </button>
      </div>
      <div className="mt-3"><FormAlert message={formError} /></div>
    </form>
  )
}

function NewCourtForm({ venueId, options, onSaved, onCancel }) {
  const [court, setCourt] = useState(emptyCourt())
  const { errors, formError, submitting, submit } = useCourtSubmit(
    (value) => addOwnerCourt(venueId, value),
    () => onSaved(`Đã thêm sân con ${court.courtCode.toUpperCase()}.`),
  )

  return (
    <form
      className="rounded-lg border border-dashed border-emerald-300 bg-emerald-50/40 p-4"
      noValidate
      onSubmit={(event) => {
        event.preventDefault()
        submit(court, options)
      }}
    >
      <p className="mb-3 text-sm font-semibold text-emerald-900">Sân con mới</p>
      <CourtFields court={court} onChange={setCourt} errors={errors} options={options} labelSuffix=" mới" />
      <div className="mt-3"><FormAlert message={formError} /></div>
      <div className="mt-3 flex justify-end gap-2">
        <button type="button" className={secondaryButtonClass} onClick={onCancel}>Hủy</button>
        <button type="submit" className={primaryButtonClass} disabled={submitting}>{submitting ? 'Đang thêm...' : 'Thêm sân con'}</button>
      </div>
    </form>
  )
}

/** 2.3.4 — thêm, sửa và đổi trạng thái các sân con. */
function VenueCourtsTab({ venue, options, onSaved }) {
  const [adding, setAdding] = useState(false)
  const canAdd = venue.courts.length < options.maxCourts

  return (
    <OwnerCard
      title="Sân con"
      description="Sân con đang có booking chưa chơi không thể chuyển sang bảo trì hoặc ngừng. Sân con bảo trì/ngừng không nhận đặt mới."
      actions={canAdd && !adding && (
        <button type="button" className={secondaryButtonClass} onClick={() => setAdding(true)}><Plus className="size-4" />Thêm sân con</button>
      )}
    >
      <div className="grid gap-4">
        {adding && (
          <NewCourtForm
            venueId={venue.id}
            options={options}
            onCancel={() => setAdding(false)}
            onSaved={(message) => {
              setAdding(false)
              onSaved(message)
            }}
          />
        )}
        {venue.courts.map((court) => (
          <CourtEditor key={`${court.id}-${court.status}-${court.courtCode}-${court.name}`} venueId={venue.id} court={court} options={options} onSaved={onSaved} />
        ))}
      </div>
    </OwnerCard>
  )
}

export { VenueCourtsTab }
