import { useEffect, useState } from 'react'
import { CheckCircle2, Clock, RefreshCw } from 'lucide-react'
import { acceptBookingRequest, bookingStatusMeta, BOOKING_STATUS_META, fetchBookingRequests } from '../../../api/ownerBookingRequests'
import { fetchOwnerVenues } from '../../../api/ownerVenues'
import { useApiResource } from '../../../hooks/useApiResource'
import { FormAlert } from '../../../shared/FormFeedback'
import { classNames, formatCurrency } from '../../../shared/utils'
import { OwnerLayout } from '../components/OwnerLayout'
import { OwnerCard, OwnerEmpty, OwnerError, OwnerLoading } from '../components/OwnerUi'
import { formatDateTime, formatTime } from '../ownerDates'
import { inputClass, primaryButtonClass, secondaryButtonClass } from '../ownerStyles'

/** "còn 12 phút", "còn 45 giây", "đã quá hạn". */
function formatRemaining(seconds) {
  if (seconds <= 0) return 'Đã quá hạn phản hồi'
  if (seconds < 60) return `Còn ${seconds} giây`
  if (seconds < 3600) return `Còn ${Math.floor(seconds / 60)} phút`
  return `Còn ${Math.floor(seconds / 3600)} giờ ${Math.floor((seconds % 3600) / 60)} phút`
}

/** Đếm lùi theo hạn phản hồi do backend trả về, không tự đếm từ lúc mở trang. */
function useSecondsUntil(deadline) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!deadline) return undefined
    const timer = window.setInterval(() => setNow(Date.now()), 15000)
    return () => window.clearInterval(timer)
  }, [deadline])
  return deadline ? Math.max(0, Math.floor((new Date(deadline).getTime() - now) / 1000)) : 0
}

function RequestCard({ item, onAccept, busy }) {
  const meta = bookingStatusMeta(item.status)
  const remaining = useSecondsUntil(item.respondBy)
  const pending = item.status === 'pending_owner_confirmation'
  const urgent = pending && remaining > 0 && remaining <= 600

  return (
    <article
      data-testid={`booking-request-${item.bookingCode}`}
      data-urgent={urgent ? 'true' : 'false'}
      className={classNames('rounded-xl border bg-white p-4 shadow-[0_10px_28px_rgba(24,65,44,0.06)]', urgent ? 'border-red-300 ring-1 ring-red-200' : 'border-emerald-100')}
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', meta.className)} data-testid="request-status">{meta.label}</span>
          <span className="text-xs font-bold text-stone-400">{item.bookingCode}</span>
        </div>
        {pending && (
          <span data-testid="request-deadline" className={classNames('inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-bold', remaining <= 0 ? 'bg-stone-200 text-stone-600' : urgent ? 'bg-red-100 text-red-700' : 'bg-emerald-50 text-emerald-800')}>
            <Clock className="size-3.5" />{formatRemaining(remaining)}
          </span>
        )}
      </div>
      <h2 className="mt-3 text-base font-semibold text-emerald-950">{item.venueName}</h2>
      <ul className="mt-1 grid gap-0.5 text-sm font-medium text-stone-600">
        {item.slots.map((slot) => (
          <li key={`${slot.courtName}-${slot.startTime}`}>{slot.courtName} · {formatDateTime(slot.startTime)}–{formatTime(slot.endTime)}</li>
        ))}
      </ul>
      <div className="mt-3 flex flex-wrap items-end justify-between gap-3 text-sm">
        <div className="min-w-0">
          <p className="font-semibold text-emerald-950">{item.playerName}{item.playerPhone ? ` · ${item.playerPhone}` : ''}</p>
          {item.note && <p className="mt-1 break-words text-stone-500">Ghi chú: “{item.note}”</p>}
          <p className="mt-1 text-xs font-medium text-stone-400">Gửi lúc {formatDateTime(item.createdAt)}</p>
        </div>
        <p className="text-lg font-semibold text-emerald-700">{formatCurrency(item.totalAmount)}</p>
      </div>
      {item.status === 'awaiting_payment' && item.paymentExpiresAt && (
        <p className="mt-3 rounded-lg bg-orange-50 px-3 py-2 text-xs font-semibold text-orange-800" data-testid="payment-deadline">
          Đã xác nhận{item.ownerRespondedByName ? ` bởi ${item.ownerRespondedByName}` : ''} lúc {formatDateTime(item.ownerRespondedAt)} · người chơi thanh toán trước {formatDateTime(item.paymentExpiresAt)}
        </p>
      )}
      {pending && remaining > 0 && (
        <div className="mt-3 flex flex-wrap justify-end gap-2">
          <button type="button" className={`${primaryButtonClass} h-10`} disabled={busy} onClick={() => onAccept(item)}>
            <CheckCircle2 className="size-4" />{busy ? 'Đang xử lý...' : 'Chấp nhận'}
          </button>
        </div>
      )}
    </article>
  )
}

/** 2.3.12–2.3.14 — danh sách và xử lý yêu cầu đặt sân. */
function OwnerBookingRequestsPage() {
  const [filters, setFilters] = useState({ venueId: '', status: 'pending_owner_confirmation', date: '', page: 0 })
  const venues = useApiResource(() => fetchOwnerVenues(), [])
  const [busyId, setBusyId] = useState(null)
  const [notice, setNotice] = useState('')
  const [actionError, setActionError] = useState('')
  const { status, data, error, reload } = useApiResource(
    () => fetchBookingRequests(filters),
    [filters.venueId, filters.status, filters.date, filters.page],
    { isEmpty: (result) => result.requests.length === 0 },
  )

  async function handleAccept(item) {
    if (busyId) return
    if (!window.confirm(`Chấp nhận yêu cầu ${item.bookingCode}? Người chơi sẽ được yêu cầu thanh toán trực tiếp cho bạn.`)) return
    setBusyId(item.bookingId)
    setNotice('')
    setActionError('')
    try {
      await acceptBookingRequest(item.bookingId)
      setNotice(`Đã chấp nhận yêu cầu ${item.bookingCode}. Đang chờ người chơi thanh toán.`)
    } catch (error) {
      setActionError(error?.message ?? 'Không chấp nhận được yêu cầu.')
    } finally {
      setBusyId(null)
      // Luôn tải lại: yêu cầu có thể đã hết hạn hoặc đã xử lý ở nơi khác.
      reload()
    }
  }

  function setFilter(patch) {
    setFilters((current) => ({ ...current, page: 0, ...patch }))
  }

  const summary = data ?? null
  return (
    <OwnerLayout
      eyebrow="Yêu cầu đặt sân"
      title="Yêu cầu đặt sân"
      description="Các yêu cầu người chơi gửi tới sân của bạn. Yêu cầu sắp hết hạn phản hồi được xếp lên đầu."
      actions={<button type="button" className={secondaryButtonClass} onClick={reload}><RefreshCw className="size-4" />Tải lại</button>}
    >
      {notice && <div role="status" className="mb-5 flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-semibold text-emerald-800"><CheckCircle2 className="size-4 shrink-0" />{notice}</div>}
      {actionError && <div className="mb-5"><FormAlert message={actionError} /></div>}
      {summary && (
        <div className="mb-5 grid gap-3 sm:grid-cols-2" data-testid="request-summary">
          <div className="rounded-xl border border-emerald-100 bg-white p-4"><p className="text-xs font-semibold text-stone-500">Đang chờ bạn duyệt</p><p className="mt-1 text-2xl font-semibold text-emerald-900" data-testid="awaiting-count">{summary.awaitingCount}</p></div>
          <div className={classNames('rounded-xl border p-4', summary.expiringSoonCount > 0 ? 'border-red-200 bg-red-50' : 'border-emerald-100 bg-white')}><p className="text-xs font-semibold text-stone-500">Sắp hết hạn phản hồi (dưới 10 phút)</p><p className={classNames('mt-1 text-2xl font-semibold', summary.expiringSoonCount > 0 ? 'text-red-700' : 'text-emerald-900')} data-testid="expiring-count">{summary.expiringSoonCount}</p></div>
        </div>
      )}

      <OwnerCard>
        <div className="mb-4 grid gap-3 sm:grid-cols-3">
          <label className="block">
            <span className="text-xs font-semibold text-stone-600">Sân</span>
            <select className={`${inputClass} mt-1`} aria-label="Lọc theo sân" value={filters.venueId} onChange={(event) => setFilter({ venueId: event.target.value })}>
              <option value="">Tất cả sân</option>
              {(venues.data?.venues ?? []).map((venue) => <option key={venue.id} value={venue.id}>{venue.name}</option>)}
            </select>
          </label>
          <label className="block">
            <span className="text-xs font-semibold text-stone-600">Trạng thái</span>
            <select className={`${inputClass} mt-1`} aria-label="Lọc theo trạng thái" value={filters.status} onChange={(event) => setFilter({ status: event.target.value })}>
              <option value="all">Tất cả trạng thái</option>
              {Object.entries(BOOKING_STATUS_META).map(([value, item]) => <option key={value} value={value}>{item.label}</option>)}
            </select>
          </label>
          <label className="block">
            <span className="text-xs font-semibold text-stone-600">Ngày chơi</span>
            <input type="date" className={`${inputClass} mt-1`} aria-label="Lọc theo ngày chơi" value={filters.date} onChange={(event) => setFilter({ date: event.target.value })} />
          </label>
        </div>

        {status === 'loading' && <OwnerLoading rows={2} />}
        {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
        {status === 'empty' && (
          <OwnerEmpty
            title={filters.status === 'pending_owner_confirmation' && !filters.venueId && !filters.date ? 'Chưa có yêu cầu nào đang chờ duyệt' : 'Không có yêu cầu phù hợp bộ lọc'}
            description="Yêu cầu mới sẽ xuất hiện ở đây ngay khi người chơi gửi."
          />
        )}
        {status === 'success' && (
          <>
            <div className="grid gap-4 lg:grid-cols-2" data-testid="request-list">
              {data.requests.map((item) => <RequestCard key={item.bookingId} item={item} busy={busyId === item.bookingId} onAccept={handleAccept} />)}
            </div>
            {data.totalPages > 1 && (
              <div className="mt-5 flex items-center justify-between gap-3">
                <button type="button" className={secondaryButtonClass} disabled={filters.page === 0} onClick={() => setFilters((current) => ({ ...current, page: current.page - 1 }))}>Trang trước</button>
                <p className="text-sm font-semibold text-stone-600" data-testid="request-page">Trang {data.page + 1}/{data.totalPages} · {data.totalElements} yêu cầu</p>
                <button type="button" className={secondaryButtonClass} disabled={filters.page + 1 >= data.totalPages} onClick={() => setFilters((current) => ({ ...current, page: current.page + 1 }))}>Trang sau</button>
              </div>
            )}
          </>
        )}
      </OwnerCard>
    </OwnerLayout>
  )
}

export default OwnerBookingRequestsPage
