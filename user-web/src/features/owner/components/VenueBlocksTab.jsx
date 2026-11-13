import { useState } from 'react'
import { Lock } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { createBlocks, fetchBlocks, unlockBlock, vietnamToIso } from '../../../api/ownerBlocks'
import { useApiResource } from '../../../hooks/useApiResource'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { classNames } from '../../../shared/utils'
import { formatDateTime, formatTime, todayInVietnam } from '../ownerDates'
import { dangerButtonClass, inputClass, primaryButtonClass } from '../ownerStyles'
import { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading } from './OwnerUi'

const emptyForm = (courtIds) => ({ courtIds, date: todayInVietnam(), startTime: '', endTime: '', reason: '' })

/** Cùng điều kiện với CreateCourtBlockRequest và OwnerCourtBlockService ở backend. */
function validate(values) {
  const errors = {}
  if (values.courtIds.length === 0) errors.courtIds = 'Vui lòng chọn ít nhất một sân con'
  if (!values.date) errors.date = 'Vui lòng chọn ngày'
  if (!values.startTime) errors.startTime = 'Vui lòng nhập giờ bắt đầu'
  if (!values.endTime) errors.endTime = 'Vui lòng nhập giờ kết thúc'
  else if (values.startTime && values.endTime <= values.startTime) errors.endTime = 'Giờ kết thúc phải sau giờ bắt đầu'
  const reason = values.reason.trim()
  if (!reason) errors.reason = 'Vui lòng nhập lý do khóa'
  else if (reason.length < 3 || reason.length > 255) errors.reason = 'Lý do từ 3 đến 255 ký tự'
  if (values.date && values.endTime && !errors.endTime && new Date(`${values.date}T${values.endTime}:00+07:00`) <= new Date()) {
    errors.startTime = 'Không khóa được khoảng thời gian đã qua'
  }
  return errors
}

function BlockForm({ venue, onSaved }) {
  const [values, setValues] = useState(() => emptyForm(venue.courts.map((court) => court.id).slice(0, 1)))
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  function toggleCourt(courtId, checked) {
    setValues((current) => ({
      ...current,
      courtIds: checked ? [...current.courtIds, courtId] : current.courtIds.filter((id) => id !== courtId),
    }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting) return
    const nextErrors = validate(values)
    setErrors(nextErrors)
    setFormError('')
    if (Object.keys(nextErrors).length > 0) return
    setSubmitting(true)
    try {
      await createBlocks(venue.id, {
        courtIds: values.courtIds,
        startTime: vietnamToIso(values.date, values.startTime),
        endTime: vietnamToIso(values.date, values.endTime),
        reason: values.reason.trim(),
      })
      onSaved(`Đã khóa ${values.courtIds.length} sân con.`)
      setValues(emptyForm(values.courtIds))
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

  return (
    <form noValidate onSubmit={handleSubmit}>
      <OwnerCard title="Khóa khung giờ" description="Khung giờ bị khóa không hiện trống và không nhận đặt. Không khóa được khung đã có booking chờ duyệt hoặc đã xác nhận.">
        <div className="grid gap-4">
          <fieldset>
            <legend className="text-sm font-semibold text-emerald-950">Sân con</legend>
            <div className="mt-2 flex flex-wrap gap-2">
              {venue.courts.map((court) => (
                <label key={court.id} className={classNames('flex h-10 cursor-pointer items-center gap-2 rounded-lg border px-3 text-sm font-semibold', values.courtIds.includes(court.id) ? 'border-emerald-700 bg-emerald-50 text-emerald-900' : 'border-stone-300 text-stone-600')}>
                  <input type="checkbox" className="size-4 accent-emerald-700" checked={values.courtIds.includes(court.id)} onChange={(event) => toggleCourt(court.id, event.target.checked)} />
                  {court.name}
                </label>
              ))}
            </div>
            <FieldError message={errors.courtIds} />
          </fieldset>
          <div className="grid gap-3 sm:grid-cols-3">
            <label className="block">
              <span className="text-xs font-semibold text-stone-600">Ngày</span>
              <input type="date" className={`${inputClass} mt-1`} aria-label="Ngày khóa" value={values.date} onChange={(event) => setValues({ ...values, date: event.target.value })} />
              <FieldError message={errors.date} />
            </label>
            <label className="block">
              <span className="text-xs font-semibold text-stone-600">Từ giờ</span>
              <input type="time" className={`${inputClass} mt-1`} aria-label="Khóa từ giờ" value={values.startTime} onChange={(event) => setValues({ ...values, startTime: event.target.value })} />
              <FieldError message={errors.startTime} />
            </label>
            <label className="block">
              <span className="text-xs font-semibold text-stone-600">Đến giờ</span>
              <input type="time" className={`${inputClass} mt-1`} aria-label="Khóa đến giờ" value={values.endTime} onChange={(event) => setValues({ ...values, endTime: event.target.value })} />
              <FieldError message={errors.endTime} />
            </label>
          </div>
          <label className="block">
            <span className="text-xs font-semibold text-stone-600">Lý do khóa</span>
            <input className={`${inputClass} mt-1`} aria-label="Lý do khóa" maxLength={255} placeholder="Ví dụ: bảo trì lưới, giải đấu nội bộ" value={values.reason} onChange={(event) => setValues({ ...values, reason: event.target.value })} />
            <FieldError message={errors.reason} />
          </label>
          <FormAlert message={formError} />
          <div className="flex justify-end">
            <button type="submit" className={primaryButtonClass} disabled={submitting}><Lock className="size-4" />{submitting ? 'Đang khóa...' : 'Khóa khung giờ'}</button>
          </div>
        </div>
      </OwnerCard>
    </form>
  )
}

/** 2.3.10 — khóa khung giờ sân và xem các khoảng đang khóa. */
function VenueBlocksTab({ venue, onSaved }) {
  const { status, data, error, reload } = useApiResource(() => fetchBlocks(venue.id), [venue.id], { isEmpty: (result) => result.blocks.length === 0 })
  const [busyId, setBusyId] = useState(null)
  const [unlockError, setUnlockError] = useState('')

  async function handleUnlock(block) {
    if (busyId) return
    const range = `${block.courtName} · ${formatDateTime(block.startTime)}–${formatTime(block.endTime)}`
    if (!window.confirm(`Mở khóa ${range}? Khung giờ sẽ nhận đặt lại ngay.`)) return
    setBusyId(block.id)
    setUnlockError('')
    try {
      await unlockBlock(venue.id, block.id)
      onSaved(`Đã mở khóa ${block.courtName}.`)
    } catch (failure) {
      setUnlockError(failure?.message ?? 'Không mở khóa được khoảng này.')
      setBusyId(null)
      // Có thể đã được mở/hết hạn ở nơi khác: tải lại danh sách.
      if (failure?.status === 404 || failure?.status === 409) reload()
    }
  }

  return (
    <div className="grid gap-5">
      <BlockForm venue={venue} onSaved={(message) => { onSaved(message) }} />
      <OwnerCard title="Các khoảng đang khóa" description="Khoảng khóa chưa kết thúc, sắp theo thời gian.">
        {status === 'loading' && <OwnerLoading rows={1} />}
        {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
        {status === 'empty' && <OwnerEmpty title="Không có khung giờ nào đang bị khóa" />}
        {unlockError && <div className="mb-3"><FormAlert message={unlockError} /></div>}
        {status === 'success' && (
          <ul className="divide-y divide-emerald-50" data-testid="block-list">
            {data.blocks.map((block) => (
              <li key={block.id} className="flex flex-wrap items-center justify-between gap-3 py-3" data-testid={`block-${block.id}`}>
                <div className="min-w-0">
                  <p className="text-sm font-semibold text-emerald-950">{block.courtName} · {formatDateTime(block.startTime)}–{formatTime(block.endTime)}</p>
                  <p className="mt-0.5 text-xs font-medium text-stone-500">{block.reason}{block.createdByName ? ` · ${block.createdByName}` : ''}</p>
                </div>
                <button type="button" className={`${dangerButtonClass} h-9 px-3 text-xs`} disabled={busyId === block.id} aria-label={`Mở khóa ${block.courtName} ${formatDateTime(block.startTime)}`} onClick={() => handleUnlock(block)}>
                  {busyId === block.id ? 'Đang mở...' : 'Mở khóa'}
                </button>
              </li>
            ))}
          </ul>
        )}
      </OwnerCard>
    </div>
  )
}

export { VenueBlocksTab }
