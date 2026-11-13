import { useState } from 'react'
import { CheckCircle2, ExternalLink } from 'lucide-react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { APPROVAL_STATUS_META, fetchOwnerVenue, fetchVenueOptions, statusMeta, VENUE_STATUS_META } from '../../../api/ownerVenues'
import { useApiResource } from '../../../hooks/useApiResource'
import { classNames } from '../../../shared/utils'
import { OwnerLayout } from '../components/OwnerLayout'
import { OwnerError, OwnerLoading } from '../components/OwnerUi'
import { VenueCourtsTab } from '../components/VenueCourtsTab'
import { VenueHoursTab } from '../components/VenueHoursTab'
import { VenueImagesTab } from '../components/VenueImagesTab'
import { VenueInfoTab } from '../components/VenueInfoTab'
import { VenuePricesTab } from '../components/VenuePricesTab'
import { VenueScheduleTab } from '../components/VenueScheduleTab'
import { secondaryButtonClass } from '../ownerStyles'

/** Các tab quản lý một sân; thêm tab khi chức năng tương ứng hoàn thành. */
const VENUE_TABS = [
  { id: 'info', label: 'Thông tin', component: VenueInfoTab },
  { id: 'courts', label: 'Sân con', component: VenueCourtsTab },
  { id: 'prices', label: 'Bảng giá', component: VenuePricesTab },
  { id: 'images', label: 'Hình ảnh', component: VenueImagesTab },
  { id: 'hours', label: 'Giờ hoạt động', component: VenueHoursTab },
  { id: 'schedule', label: 'Lịch sân', component: VenueScheduleTab },
]

async function loadVenuePage(venueId) {
  const [venue, options] = await Promise.all([fetchOwnerVenue(venueId), fetchVenueOptions()])
  return { venue, options }
}

/** Trang quản lý một sân của chủ sân (2.3.4 trở đi). */
function OwnerVenueDetailPage() {
  const { venueId } = useParams()
  const [searchParams, setSearchParams] = useSearchParams()
  const { status, data, error, reload } = useApiResource(() => loadVenuePage(venueId), [venueId])
  const [notice, setNotice] = useState('')
  const activeTab = VENUE_TABS.find((tab) => tab.id === searchParams.get('tab')) ?? VENUE_TABS[0]
  const TabComponent = activeTab.component

  const venue = data?.venue
  const published = venue?.status === 'active' && venue?.approvalStatus === 'approved'

  return (
    <OwnerLayout
      eyebrow="Quản lý sân"
      title={venue?.name ?? 'Quản lý sân'}
      description={venue ? `${venue.address}` : undefined}
      actions={(
        <>
          <Link to="/owner/venues" className={secondaryButtonClass}>Về danh sách sân</Link>
          {published && <a className={secondaryButtonClass} href={`/san/${venue.slug}`} target="_blank" rel="noreferrer"><ExternalLink className="size-4" />Xem trang công khai</a>}
        </>
      )}
    >
      {notice && status !== 'loading' && (
        <div role="status" className="mb-5 flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-semibold text-emerald-800">
          <CheckCircle2 className="size-4 shrink-0" />
          {notice}
        </div>
      )}
      {status === 'loading' && <OwnerLoading rows={3} />}
      {status === 'error' && (
        <OwnerError
          message={error?.status === 404 ? 'Không tìm thấy sân này trong danh sách sân của bạn.' : error?.message}
          onRetry={error?.status === 404 ? undefined : reload}
        />
      )}
      {status === 'success' && (
        <>
          <div className="mb-5 flex flex-wrap gap-2">
            {[statusMeta(APPROVAL_STATUS_META, venue.approvalStatus), statusMeta(VENUE_STATUS_META, venue.status)].map((meta) => (
              <span key={meta.label} className={classNames('rounded-full px-3 py-1.5 text-xs font-bold', meta.className)}>{meta.label}</span>
            ))}
          </div>
          <div className="mb-5 flex gap-1 overflow-x-auto rounded-xl border border-emerald-100 bg-white p-1.5" role="tablist" aria-label="Quản lý sân">
            {VENUE_TABS.map((tab) => (
              <button
                key={tab.id}
                type="button"
                role="tab"
                aria-selected={tab.id === activeTab.id}
                className={classNames('h-10 shrink-0 rounded-lg px-4 text-sm font-semibold', tab.id === activeTab.id ? 'bg-emerald-700 text-white' : 'text-stone-600 hover:bg-emerald-50')}
                onClick={() => {
                  setNotice('')
                  setSearchParams({ tab: tab.id }, { replace: true })
                }}
              >
                {tab.label}
              </button>
            ))}
          </div>
          <TabComponent key={`${activeTab.id}-${venue.updatedAt}`} venue={venue} options={data.options} onSaved={(message) => {
            setNotice(message)
            reload()
          }} />
        </>
      )}
    </OwnerLayout>
  )
}

export default OwnerVenueDetailPage
