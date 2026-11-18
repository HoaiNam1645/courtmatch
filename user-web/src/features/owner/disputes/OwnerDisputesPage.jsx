import { useState } from 'react'
import { Link } from 'react-router-dom'
import { AlertTriangle, ChevronRight, ClipboardList, ImageIcon } from 'lucide-react'
import { fetchOwnerDisputes, OWNER_DISPUTE_TABS, OWNER_STATUS_LABEL } from '../../../api/ownerDisputes'
import { RESOLUTION_LABEL } from '../../../api/bookingDisputes'
import { fetchOwnerVenues } from '../../../api/ownerVenues'
import { useApiResource } from '../../../hooks/useApiResource'
import { classNames, formatCurrency } from '../../../shared/utils'
import { formatDateTime, remainingText } from '../../disputes/disputeUi'
import { OwnerLayout } from '../components/OwnerLayout'
import { OwnerEmpty, OwnerError, OwnerLoading } from '../components/OwnerUi'
import { inputClass, secondaryButtonClass } from '../ownerStyles'

const STATUS_CLASS = {
  open: 'bg-amber-100 text-amber-800',
  awaiting_owner_response: 'bg-amber-100 text-amber-800',
  reviewing: 'bg-violet-100 text-violet-700',
  resolved: 'bg-emerald-100 text-emerald-700',
  rejected: 'bg-red-100 text-red-700',
  cancelled: 'bg-stone-200 text-stone-600',
}

/** 2.3.93 - chủ sân xem báo cáo của khách về đơn thuộc sân của mình. */
function OwnerDisputesPage() {
  const [tab, setTab] = useState('awaiting')
  const [venueId, setVenueId] = useState('')
  const [page, setPage] = useState(0)
  const venues = useApiResource(() => fetchOwnerVenues({ size: 100 }), [])
  const { status, data, error, reload } = useApiResource(
    () => fetchOwnerDisputes({ venueId, status: tab, page }),
    [venueId, tab, page],
    { isEmpty: (result) => result.items.length === 0 },
  )
  const counts = data?.counts

  return (
    <OwnerLayout eyebrow="Báo cáo của khách" title="Báo cáo của khách" description="Báo cáo về đơn đặt sân thuộc sân của bạn. Giải trình trước hạn 48 giờ để quản trị viên xem xét; quá hạn thì hệ thống sẽ tự xử lý.">
      <div className="grid gap-4">
        {counts && counts.awaiting > 0 && (
          <p className={classNames('flex items-center gap-2 rounded-xl border p-3 text-sm font-semibold', counts.overdue > 0 ? 'border-red-200 bg-red-50 text-red-800' : counts.expiringSoon > 0 ? 'border-amber-200 bg-amber-50 text-amber-900' : 'border-emerald-100 bg-white text-emerald-900')} data-testid="od-summary">
            <AlertTriangle className="size-4 shrink-0" />
            {counts.awaiting} báo cáo đang chờ bạn giải trình{counts.expiringSoon > 0 ? ` · ${counts.expiringSoon} sắp hết hạn (dưới 6 giờ)` : ''}{counts.overdue > 0 ? ` · ${counts.overdue} đã quá hạn` : ''}
          </p>
        )}
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex gap-1 overflow-x-auto rounded-lg border border-emerald-100 bg-white p-1" role="tablist" aria-label="Trạng thái báo cáo">
            {OWNER_DISPUTE_TABS.map(([id, label]) => (
              <button key={id} type="button" role="tab" aria-selected={tab === id} data-testid={`od-tab-${id}`} onClick={() => { setTab(id); setPage(0) }}
                className={classNames('h-9 shrink-0 whitespace-nowrap rounded-md px-3 text-sm font-semibold', tab === id ? 'bg-emerald-700 text-white' : 'text-stone-600 hover:bg-emerald-50')}>{label}</button>
            ))}
          </div>
          <select className={`${inputClass} sm:max-w-56`} aria-label="Lọc theo sân" data-testid="od-venue" value={venueId} onChange={(event) => { setVenueId(event.target.value); setPage(0) }}>
            <option value="">Tất cả sân của tôi</option>
            {(venues.data?.venues ?? []).map((venue) => <option key={venue.id} value={venue.id}>{venue.name}</option>)}
          </select>
        </div>

        {status === 'loading' && <OwnerLoading rows={3} />}
        {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
        {status === 'empty' && <OwnerEmpty title={tab === 'awaiting' ? 'Không có báo cáo nào chờ bạn giải trình' : 'Không có báo cáo nào'} description="Báo cáo mới của khách sẽ xuất hiện tại đây." />}
        {status === 'success' && (
          <>
            <ul className="grid gap-3" data-testid="od-list">
              {data.items.map((item) => (
                <li key={item.id}>
                  <Link to={`/owner/disputes/${item.id}`} data-testid={`od-${item.id}`} data-status={item.status} data-overdue={item.overdue} data-soon={item.expiringSoon}
                    className={classNames('block rounded-xl border bg-white p-4 shadow-sm transition hover:border-emerald-300', item.overdue ? 'border-red-300' : item.expiringSoon ? 'border-amber-300' : 'border-emerald-100')}>
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className={classNames('rounded-full px-2.5 py-1 text-xs font-bold', STATUS_CLASS[item.status])} data-testid="od-status">{OWNER_STATUS_LABEL[item.status] ?? item.status}</span>
                        <span className="rounded-full bg-stone-100 px-2.5 py-1 text-xs font-bold text-stone-700">{item.categoryLabel}</span>
                      </div>
                      <ChevronRight className="size-5 text-stone-400" />
                    </div>
                    <p className="mt-3 text-base font-semibold">{item.venueName} <span className="font-medium text-stone-500">· đơn {item.bookingCode}</span></p>
                    <p className="mt-1 text-xs font-medium text-stone-500">{item.playerName} · gửi lúc {formatDateTime(item.createdAt)}</p>
                    <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs font-semibold text-stone-600">
                      {item.refundRequested && <span className="text-emerald-800" data-testid="od-refund">Yêu cầu hoàn {formatCurrency(item.refundAmount)}</span>}
                      {item.evidenceCount > 0 && <span className="inline-flex items-center gap-1"><ImageIcon className="size-3.5" />{item.evidenceCount} ảnh</span>}
                      {item.ownerResponseDeadline && <span data-testid="od-deadline" className={item.overdue ? 'text-red-700' : item.expiringSoon ? 'text-amber-800' : 'text-stone-600'}>Hạn giải trình: {remainingText(item.ownerResponseDeadline)}</span>}
                      {item.closed && item.resolution && <span className="text-emerald-800">{RESOLUTION_LABEL[item.resolution] ?? item.resolution}</span>}
                    </div>
                  </Link>
                </li>
              ))}
            </ul>
            <div className="flex items-center justify-between gap-3">
              <button type="button" className={secondaryButtonClass} disabled={page === 0} onClick={() => setPage(page - 1)}>Trang trước</button>
              <p className="text-sm font-semibold text-stone-600" data-testid="od-pager">Trang {data.page + 1}/{data.totalPages} · {data.totalElements} báo cáo</p>
              <button type="button" className={secondaryButtonClass} disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Trang sau</button>
            </div>
          </>
        )}
        {status === 'empty' && <p className="flex items-center gap-2 text-xs font-medium text-stone-500"><ClipboardList className="size-4" />Bạn chỉ thấy báo cáo về đơn của sân mình.</p>}
      </div>
    </OwnerLayout>
  )
}

export default OwnerDisputesPage
