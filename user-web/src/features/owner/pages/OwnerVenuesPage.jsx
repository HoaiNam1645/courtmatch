import { CheckCircle2, ChevronRight, MapPin, Plus } from 'lucide-react'
import { Link, useLocation } from 'react-router-dom'
import { APPROVAL_STATUS_META, fetchOwnerVenues, statusMeta, VENUE_STATUS_META } from '../../../api/ownerVenues'
import { useApiResource } from '../../../hooks/useApiResource'
import { classNames } from '../../../shared/utils'
import { OwnerLayout } from '../components/OwnerLayout'
import { OwnerEmpty, OwnerError, OwnerLoading } from '../components/OwnerUi'
import { primaryButtonClass } from '../ownerStyles'

function Badge({ meta, testId }) {
  return <span data-testid={testId} className={classNames('inline-flex rounded-full px-2.5 py-1 text-xs font-bold', meta.className)}>{meta.label}</span>
}

/** Danh sách sân của chủ sân, mọi trạng thái (2.3.3). */
function OwnerVenuesPage() {
  const location = useLocation()
  const createdVenueName = location.state?.createdVenueName
  const { status, data, error, reload } = useApiResource(() => fetchOwnerVenues(), [], {
    isEmpty: (result) => result.venues.length === 0,
  })

  const addButton = <Link to="/owner/venues/new" className={primaryButtonClass}><Plus className="size-4" />Thêm sân mới</Link>

  return (
    <OwnerLayout eyebrow="Sân của tôi" title="Sân của tôi" description="Các sân bạn quản lý, kể cả sân đang chờ quản trị viên duyệt." actions={addButton}>
      {createdVenueName && (
        <div role="status" className="mb-5 flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-semibold text-emerald-800">
          <CheckCircle2 className="size-4 shrink-0" />
          Đã gửi sân “{createdVenueName}”. Sân sẽ hiển thị với người chơi sau khi quản trị viên duyệt.
        </div>
      )}
      {status === 'loading' && <OwnerLoading rows={3} />}
      {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
      {status === 'empty' && <OwnerEmpty title="Bạn chưa có sân nào" description="Thêm sân đầu tiên để bắt đầu nhận đặt sân trên Courtly." action={addButton} />}
      {status === 'success' && (
        <div className="grid gap-4 md:grid-cols-2">
          {data.venues.map((venue) => (
            <article key={venue.id} data-testid={`owner-venue-${venue.slug}`} className="flex min-w-0 overflow-hidden rounded-xl border border-emerald-100 bg-white shadow-[0_10px_28px_rgba(24,65,44,0.07)]">
              {venue.coverImageUrl
                ? <img className="w-28 shrink-0 object-cover sm:w-36" src={venue.coverImageUrl} alt="" />
                : <div className="grid w-28 shrink-0 place-items-center bg-emerald-50 text-emerald-700 sm:w-36"><MapPin className="size-7" /></div>}
              <div className="min-w-0 flex-1 p-4">
                <div className="flex flex-wrap gap-1.5">
                  <Badge meta={statusMeta(APPROVAL_STATUS_META, venue.approvalStatus)} testId="approval-status" />
                  <Badge meta={statusMeta(VENUE_STATUS_META, venue.status)} testId="venue-status" />
                </div>
                <h2 className="mt-2 truncate text-base font-semibold text-emerald-950">{venue.name}</h2>
                <p className="mt-1 line-clamp-2 text-sm font-medium text-stone-500">{venue.address}{venue.district && !venue.address.includes(venue.district) ? `, ${venue.district}` : ''}</p>
                <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
                  <p className="text-xs font-semibold text-emerald-700">{venue.courtCount} sân con</p>
                  <Link to={`/owner/venues/${venue.id}`} className="inline-flex h-9 items-center gap-1 rounded-lg bg-emerald-700 px-3 text-xs font-semibold text-white" aria-label={`Quản lý ${venue.name}`}>
                    Quản lý<ChevronRight className="size-4" />
                  </Link>
                </div>
              </div>
            </article>
          ))}
        </div>
      )}
    </OwnerLayout>
  )
}

export default OwnerVenuesPage
