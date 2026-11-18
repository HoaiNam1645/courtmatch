import { useState } from 'react'
import { CheckCircle2, ExternalLink } from 'lucide-react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { APPROVAL_STATUS_META, fetchOwnerVenue, fetchVenueOptions, resubmitOwnerVenue, statusMeta, VENUE_STATUS_META } from '../../../api/ownerVenues'
import { useApiResource } from '../../../hooks/useApiResource'
import { classNames } from '../../../shared/utils'
import { OwnerLayout } from '../components/OwnerLayout'
import { OwnerError, OwnerLoading } from '../components/OwnerUi'
import { VenueCourtsTab } from '../components/VenueCourtsTab'
import { VenueBlocksTab } from '../components/VenueBlocksTab'
import { VenueHoursTab } from '../components/VenueHoursTab'
import { VenueImagesTab } from '../components/VenueImagesTab'
import { VenueInfoTab } from '../components/VenueInfoTab'
import { VenuePricesTab } from '../components/VenuePricesTab'
import { VenueScheduleTab } from '../components/VenueScheduleTab'
import { VenueSlotsTab } from '../components/VenueSlotsTab'
import { secondaryButtonClass } from '../ownerStyles'

/** Các tab quản lý một sân; thêm tab khi chức năng tương ứng hoàn thành. */
const VENUE_TABS = [
  { id: 'info', label: 'Thông tin', component: VenueInfoTab },
  { id: 'courts', label: 'Sân con', component: VenueCourtsTab },
  { id: 'prices', label: 'Bảng giá', component: VenuePricesTab },
  { id: 'images', label: 'Hình ảnh', component: VenueImagesTab },
  { id: 'hours', label: 'Giờ hoạt động', component: VenueHoursTab },
  { id: 'slots', label: 'Khung giờ', component: VenueSlotsTab },
  { id: 'schedule', label: 'Lịch sân', component: VenueScheduleTab },
  { id: 'blocks', label: 'Khóa giờ', component: VenueBlocksTab },
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
  const [resubmitting, setResubmitting] = useState(false)
  const [resubmitError, setResubmitError] = useState('')
  const activeTab = VENUE_TABS.find((tab) => tab.id === searchParams.get('tab')) ?? VENUE_TABS[0]
  const TabComponent = activeTab.component

  const venue = data?.venue
  async function resubmit() {
    if (resubmitting) return
    setResubmitting(true)
    setResubmitError('')
    try {
      await resubmitOwnerVenue(venueId)
      setNotice('Đã gửi sân để quản trị viên duyệt lại.')
      reload()
    } catch (failure) {
      setResubmitError(failure?.message ?? 'Đã có lỗi xảy ra, vui lòng thử lại.')
    } finally {
      setResubmitting(false)
    }
  }

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
          {venue.suspensionReason && venue.status === 'inactive' && (
            <section className="mb-5 rounded-xl border border-red-300 bg-red-50 p-4" data-testid="venue-suspension">
              <h2 className="text-sm font-semibold text-red-800">Sân đang bị tạm ngưng hoạt động</h2>
              <p className="mt-1 text-sm font-medium text-red-700" data-testid="venue-suspension-reason">{venue.suspensionReason}</p>
              <p className="mt-1 text-xs font-medium text-red-700">Sân không hiện với người chơi và không nhận đặt mới; các đơn đã đặt vẫn được giữ nguyên. Hãy liên hệ quản trị viên để được xem xét bật lại.</p>
            </section>
          )}
          {venue.approvalStatus === 'rejected' && (
            <section className="mb-5 rounded-xl border border-red-200 bg-red-50 p-4" data-testid="venue-rejection">
              <h2 className="text-sm font-semibold text-red-800">Quản trị viên đã từ chối sân này</h2>
              <p className="mt-1 text-sm font-medium text-red-700" data-testid="venue-rejection-reason">Lý do: {venue.rejectionReason}</p>
              <p className="mt-1 text-xs font-medium text-red-700">Hãy chỉnh sửa theo yêu cầu rồi gửi duyệt lại. Sân chưa hiển thị cho người chơi cho tới khi được duyệt.</p>
              {resubmitError && <p role="alert" className="mt-2 text-sm font-semibold text-red-800">{resubmitError}</p>}
              <button type="button" className={`${secondaryButtonClass} mt-3`} data-testid="venue-resubmit" disabled={resubmitting} onClick={resubmit}>{resubmitting ? 'Đang gửi...' : 'Gửi duyệt lại'}</button>
            </section>
          )}
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
