import { useState } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { createBlocks, unlockBlock } from '../../../api/ownerBlocks'
import { fetchVenueSchedule, slotMeta, SLOT_STATUS_META } from '../../../api/ownerSchedule'
import { useApiResource } from '../../../hooks/useApiResource'
import { FormAlert } from '../../../shared/FormFeedback'
import { classNames, formatCurrency } from '../../../shared/utils'
import { dayLabel } from '../dayLabels'
import { formatDateTime, formatIsoDate, formatTime, todayInVietnam } from '../ownerDates'
import { dangerButtonClass, inputClass, secondaryButtonClass } from '../ownerStyles'
import { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading } from './OwnerUi'

function shiftDate(isoDate, days) {
  const [year, month, day] = isoDate.split('-').map(Number)
  const next = new Date(Date.UTC(year, month - 1, day + days))
  return next.toISOString().slice(0, 10)
}

/** Lưới: hàng = giờ bắt đầu, cột = sân con (xem ngày) hoặc ngày (xem tuần). */
function ScheduleGrid({ columns, selected, onSelect }) {
  const rows = [...new Set(columns.flatMap((column) => column.slots.map((slot) => slot.startTime)))].sort()
  if (rows.length === 0) return null

  return (
    <div className="overflow-x-auto rounded-lg border border-emerald-100">
      <table className="w-full min-w-[520px] border-collapse text-sm" data-testid="schedule-grid">
        <thead>
          <tr className="bg-emerald-50/60">
            <th className="sticky left-0 z-10 w-16 bg-emerald-50 px-2 py-2 text-left text-xs font-semibold text-stone-500">Giờ</th>
            {columns.map((column) => (
              <th key={column.key} className="px-2 py-2 text-left text-xs font-semibold text-emerald-900">
                <span className="block">{column.label}</span>
                {column.sublabel && <span className="block font-medium text-stone-500">{column.sublabel}</span>}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((time) => (
            <tr key={time} className="border-t border-emerald-50">
              <td className="sticky left-0 bg-white px-2 py-1 text-xs font-semibold text-stone-500">{time}</td>
              {columns.map((column) => {
                const slot = column.slots.find((item) => item.startTime === time)
                if (!slot) return <td key={column.key} className="bg-stone-50" />
                const meta = slotMeta(slot.status)
                const isSelected = selected?.key === `${column.key}|${slot.startAt}`
                return (
                  <td key={column.key} className="p-1">
                    <button
                      type="button"
                      data-testid={`slot-${column.key}-${slot.startTime}`}
                      data-status={slot.status}
                      className={classNames('flex h-10 w-full items-center justify-center rounded-md border px-1 text-xs font-semibold', meta.className, isSelected && 'ring-2 ring-lime-400')}
                      onClick={() => onSelect({ key: `${column.key}|${slot.startAt}`, column, slot })}
                      aria-label={`${column.label} ${slot.startTime}–${slot.endTime}: ${meta.label}`}
                    >
                      {slot.booking ? slot.booking.playerName : meta.label}
                    </button>
                  </td>
                )
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/** Khóa nhanh một ô trống (2.3.10). */
function QuickBlock({ venueId, selected, onChanged }) {
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting) return
    if (reason.trim().length < 3) {
      setError('Lý do từ 3 đến 255 ký tự')
      return
    }
    setSubmitting(true)
    setError('')
    try {
      await createBlocks(venueId, { courtIds: [selected.column.courtId], startTime: selected.slot.startAt, endTime: selected.slot.endAt, reason: reason.trim() })
      setReason('')
      onChanged()
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : 'Đã có lỗi xảy ra, vui lòng thử lại.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="mt-3 flex flex-wrap items-start gap-2" noValidate onSubmit={handleSubmit}>
      <input className={`${inputClass} h-10 min-w-0 flex-1 basis-48`} aria-label="Lý do khóa ô này" maxLength={255} placeholder="Lý do khóa (bảo trì, sự kiện...)" value={reason} onChange={(event) => setReason(event.target.value)} />
      <button type="submit" className={`${dangerButtonClass} h-10`} disabled={submitting}>{submitting ? 'Đang khóa...' : 'Khóa khung giờ này'}</button>
      <div className="basis-full"><FormAlert message={error} /></div>
    </form>
  )
}

/** Mở khóa khoảng khóa đang phủ ô này (2.3.11). */
function QuickUnlock({ venueId, block, onChanged }) {
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleUnlock() {
    if (submitting) return
    if (!window.confirm(`Mở khóa cả khoảng ${formatDateTime(block.startAt)}–${formatTime(block.endAt)}? Khung giờ sẽ nhận đặt lại ngay.`)) return
    setSubmitting(true)
    setError('')
    try {
      await unlockBlock(venueId, block.blockId)
      onChanged()
    } catch (failure) {
      setError(failure?.message ?? 'Không mở khóa được khoảng này.')
      setSubmitting(false)
    }
  }

  return (
    <div className="mt-3">
      <button type="button" className={`${secondaryButtonClass} h-10`} disabled={submitting} onClick={handleUnlock}>{submitting ? 'Đang mở khóa...' : 'Mở khóa khoảng này'}</button>
      <div className="mt-2"><FormAlert message={error} /></div>
    </div>
  )
}

function SlotDetail({ selected, venueId, onChanged }) {
  if (!selected) {
    return <p className="rounded-lg bg-emerald-50/60 px-4 py-3 text-sm font-medium text-stone-500">Chọn một ô trong lịch để xem chi tiết.</p>
  }
  const { slot, column } = selected
  const meta = slotMeta(slot.status)
  return (
    <div className="rounded-lg border border-emerald-100 bg-white p-4 text-sm" data-testid="slot-detail">
      <div className="flex flex-wrap items-center gap-2">
        <span className={classNames('rounded-full border px-2.5 py-1 text-xs font-bold', meta.className)}>{meta.label}</span>
        <p className="font-semibold text-emerald-950">{column.label} · {slot.startTime}–{slot.endTime}</p>
      </div>
      {slot.price != null && <p className="mt-2 font-medium text-stone-600">Giá: {formatCurrency(slot.price)}</p>}
      {slot.booking && (
        <dl className="mt-3 grid gap-1 font-medium text-stone-700">
          <div>Mã booking: <span className="font-semibold">{slot.booking.bookingCode}</span></div>
          <div>Người đặt: <span className="font-semibold">{slot.booking.playerName}</span>{slot.booking.playerPhone ? ` · ${slot.booking.playerPhone}` : ''}</div>
          <div>Khung giờ giữ: {formatDateTime(slot.booking.startAt)}–{formatTime(slot.booking.endAt)}</div>
        </dl>
      )}
      {slot.status === 'available' && selected.column.courtId && (
        <QuickBlock key={selected.key} venueId={venueId} selected={selected} onChanged={onChanged} />
      )}
      {slot.block && (
        <>
          <p className="mt-3 font-medium text-stone-700">Lý do khóa: <span className="font-semibold">{slot.block.reason || 'Không ghi lý do'}</span></p>
          <p className="mt-1 text-xs font-medium text-stone-500">Khoảng khóa: {formatDateTime(slot.block.startAt)}–{formatTime(slot.block.endAt)}</p>
          {slot.status === 'blocked' && <QuickUnlock key={slot.block.blockId} venueId={venueId} block={slot.block} onChanged={onChanged} />}
        </>
      )}
    </div>
  )
}

function Legend() {
  return (
    <ul className="flex flex-wrap gap-2" aria-label="Chú giải">
      {Object.entries(SLOT_STATUS_META).map(([status, meta]) => (
        <li key={status} className={classNames('rounded-full border px-2.5 py-1 text-xs font-semibold', meta.className)}>{meta.label}</li>
      ))}
    </ul>
  )
}

/** 2.3.8 — lịch sân theo ngày hoặc tuần. */
function VenueScheduleTab({ venue }) {
  const [date, setDate] = useState(todayInVietnam())
  const [view, setView] = useState('day')
  const [courtId, setCourtId] = useState('')
  const [selected, setSelected] = useState(null)
  const days = view === 'day' ? 1 : 7
  const { status, data, error, reload } = useApiResource(() => fetchVenueSchedule(venue.id, { date, days }), [venue.id, date, days])

  const firstDay = data?.days[0]
  const courts = firstDay?.courts ?? []
  const activeCourtId = courts.some((court) => court.courtId === courtId) ? courtId : courts[0]?.courtId

  function move(delta) {
    setSelected(null)
    setDate(shiftDate(date, delta * days))
  }

  let columns = []
  if (data && view === 'day') {
    columns = courts.map((court) => ({ key: court.courtCode, courtId: court.courtId, label: court.courtName, sublabel: court.courtStatus === 'active' ? null : 'Tạm ngưng', slots: court.slots }))
  } else if (data) {
    columns = data.days.map((day) => ({
      key: day.date,
      courtId: activeCourtId,
      label: dayLabel(day.dayOfWeek),
      sublabel: day.closed ? 'Nghỉ' : formatIsoDate(day.date).slice(0, 5),
      slots: day.courts.find((court) => court.courtId === activeCourtId)?.slots ?? [],
    }))
  }
  const noSlots = data && columns.every((column) => column.slots.length === 0)

  return (
    <OwnerCard
      title="Lịch sân"
      description="Ô trống, đang chờ duyệt, đã xác nhận hoặc bị khóa của từng sân con. Lịch tính từ giờ hoạt động, bảng giá, khung giờ khóa và các booking đang giữ chỗ."
    >
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div className="flex gap-1 rounded-lg border border-emerald-100 p-1" role="group" aria-label="Chế độ xem">
          {[['day', 'Theo ngày'], ['week', 'Theo tuần']].map(([value, label]) => (
            <button key={value} type="button" aria-pressed={view === value} className={classNames('h-9 rounded-md px-3 text-sm font-semibold', view === value ? 'bg-emerald-700 text-white' : 'text-stone-600 hover:bg-emerald-50')} onClick={() => { setView(value); setSelected(null) }}>{label}</button>
          ))}
        </div>
        <div className="flex items-center gap-1">
          <button type="button" className={`${secondaryButtonClass} h-10 px-3`} aria-label={view === 'day' ? 'Ngày trước' : 'Tuần trước'} onClick={() => move(-1)}><ChevronLeft className="size-4" /></button>
          <input type="date" className={`${inputClass} h-10 w-44`} aria-label="Chọn ngày" value={date} onChange={(event) => { if (event.target.value) { setSelected(null); setDate(event.target.value) } }} />
          <button type="button" className={`${secondaryButtonClass} h-10 px-3`} aria-label={view === 'day' ? 'Ngày sau' : 'Tuần sau'} onClick={() => move(1)}><ChevronRight className="size-4" /></button>
          <button type="button" className={`${secondaryButtonClass} h-10`} onClick={() => { setSelected(null); setDate(todayInVietnam()) }}>Hôm nay</button>
        </div>
        {view === 'week' && courts.length > 0 && (
          <label className="block">
            <span className="text-xs font-semibold text-stone-600">Sân con</span>
            <select className={`${inputClass} mt-1 h-10 w-44`} aria-label="Sân con" value={activeCourtId} onChange={(event) => { setCourtId(event.target.value); setSelected(null) }}>
              {courts.map((court) => <option key={court.courtId} value={court.courtId}>{court.courtName}</option>)}
            </select>
          </label>
        )}
      </div>

      {status === 'loading' && <OwnerLoading rows={2} />}
      {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
      {status === 'success' && courts.length === 0 && <OwnerEmpty title="Sân chưa có sân con" description="Thêm sân con ở tab “Sân con” để xem lịch." />}
      {status === 'success' && courts.length > 0 && (
        <div className="grid gap-4">
          <Legend />
          {view === 'day' && firstDay.closed && <OwnerEmpty title="Sân nghỉ ngày này" description={`${dayLabel(firstDay.dayOfWeek)} ${formatIsoDate(firstDay.date)} không có giờ hoạt động.`} />}
          {noSlots && !(view === 'day' && firstDay.closed) && <OwnerEmpty title="Không có khung giờ" description="Các ngày trong khoảng này đều nghỉ." />}
          {!noSlots && <ScheduleGrid columns={columns} selected={selected} onSelect={setSelected} />}
          <SlotDetail selected={selected} venueId={venue.id} onChanged={() => { setSelected(null); reload() }} />
        </div>
      )}
    </OwnerCard>
  )
}

export { VenueScheduleTab }
