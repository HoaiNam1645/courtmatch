import { useState } from 'react'
import { CheckCircle2, ChevronRight, Pencil, Plus, Search, Star, Trash2, X } from 'lucide-react'
import { createVenueReview, deleteVenueReview, updateVenueReview } from '../../../api/venueReviews'
import { ConfirmDeleteDialog } from '../../reviews/ConfirmDeleteDialog'
import { VenueReviewDialog } from '../../reviews/VenueReviewDialog'
import { AppHeader, BottomNav } from '../../../app/AppLayout'
import { useBookings } from '../../../hooks/useBookings'
import { classNames, formatCurrency } from '../../../shared/utils'
import { PageBackLink, ProfileEmptyState } from '../../profile/components/ProfileUi'
import { BookingEmptyState } from '../components/BookingEmptyState'

const bookingStatusMeta = {
  pending_owner_confirmation: { label: 'Chờ chủ sân duyệt', className: 'bg-amber-100 text-amber-700' },
  awaiting_payment: { label: 'Chờ thanh toán', className: 'bg-orange-100 text-orange-700' },
  confirmed: { label: 'Đã xác nhận', className: 'bg-emerald-100 text-emerald-700' },
  rejected: { label: 'Đã từ chối', className: 'bg-red-100 text-red-700' },
  cancelled: { label: 'Đã hủy', className: 'bg-red-100 text-red-700' },
  completed: { label: 'Đã hoàn thành', className: 'bg-sky-100 text-sky-700' },
  expired: { label: 'Đã hết hạn', className: 'bg-stone-200 text-stone-600' },
  disputed: { label: 'Đang tranh chấp', className: 'bg-amber-100 text-amber-800' },
  refunded: { label: 'Đã hoàn tiền', className: 'bg-violet-100 text-violet-700' },
}

function BookingStatusBadge({ status }) {
  const meta = bookingStatusMeta[status] ?? bookingStatusMeta.pending_owner_confirmation
  return <span className={classNames('inline-flex rounded-full px-3 py-1.5 text-xs font-bold', meta.className)}>{meta.label}</span>
}

function formatBookingDate(value) {
  return new Intl.DateTimeFormat('vi-VN', { weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric' }).format(new Date(value))
}

function formatBookingTime(value) {
  return new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value))
}

function BookingsPage({ user, onLogout }) {
  const [activeTab, setActiveTab] = useState('upcoming')
  const [query, setQuery] = useState('')
  const [reviewing, setReviewing] = useState(null)
  const [editing, setEditing] = useState(null)
  const [deleting, setDeleting] = useState(null)
  const [notice, setNotice] = useState('')

  // Lọc theo tab và tìm kiếm đều do server làm (2.1.32).
  const { status, bookings, error, reload } = useBookings({ tab: activeTab, q: query })

  if (!user) return <ProfileEmptyState />

  const tabs = [
    { id: 'upcoming', label: 'Sắp tới' },
    { id: 'pending', label: 'Chờ xử lý' },
    { id: 'completed', label: 'Đã hoàn thành' },
    { id: 'cancelled', label: 'Đã hủy' },
  ]
  return (
    <main className="min-h-screen bg-[linear-gradient(145deg,#eef8f2,#f9faf6)] pb-24 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="mx-auto max-w-[1100px] px-4 py-7 md:px-6 md:py-10">
        <div className="flex flex-wrap items-end justify-between gap-4"><div><PageBackLink href="/profile" label="Về hồ sơ" /><p className="mt-6 text-xs font-bold uppercase tracking-[0.15em] text-emerald-600">2.1.32 · Lịch sử đặt sân</p><h1 className="mt-2 text-3xl font-semibold">Booking của tôi</h1><p className="mt-2 text-sm font-medium text-stone-500">Theo dõi các lịch sắp tới và toàn bộ lịch sử đặt sân.</p></div><a className="flex h-11 items-center gap-2 rounded-lg bg-lime-300 px-5 text-sm font-semibold text-emerald-950" href="/"><Plus className="size-4" />Đặt sân mới</a></div>

        <div className="mt-7 rounded-xl border border-emerald-100 bg-white p-3 shadow-[0_12px_34px_rgba(24,65,44,0.08)]"><div className="flex gap-2 overflow-x-auto pb-1">{tabs.map((tab) => { return <button key={tab.id} className={classNames('flex h-11 shrink-0 items-center gap-2 rounded-lg px-4 text-sm font-semibold', activeTab === tab.id ? 'bg-emerald-700 text-white' : 'bg-stone-50 text-stone-500')} onClick={() => setActiveTab(tab.id)}>{tab.label}</button> })}</div><label className="mt-3 flex h-11 items-center gap-3 rounded-lg border border-stone-200 px-4"><Search className="size-4 text-emerald-700" /><input className="min-w-0 flex-1 text-sm outline-none placeholder:text-stone-400" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Tìm theo mã booking hoặc tên sân" />{query && <button onClick={() => setQuery('')} aria-label="Xóa tìm kiếm"><X className="size-4 text-stone-400" /></button>}</label></div>

        {notice && <p role="status" data-testid="booking-notice" className="mt-5 flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-semibold text-emerald-800"><CheckCircle2 className="size-4 shrink-0" />{notice}</p>}

        {status === 'loading' && (
          <div className="mt-6 grid gap-4">
            {[0, 1].map((index) => <div key={index} className="h-44 animate-pulse rounded-xl bg-emerald-100/60" />)}
          </div>
        )}

        {status === 'error' && (
          <div className="mt-6 rounded-xl border border-red-200 bg-red-50 p-8 text-center">
            <p className="font-medium text-red-700">{error}</p>
            <button className="mt-4 h-11 rounded-lg bg-emerald-700 px-6 text-sm font-semibold text-white" onClick={reload}>
              Thử lại
            </button>
          </div>
        )}

        {status === 'empty' && (
          <div className="mt-6">
            <BookingEmptyState
              title="Chưa có booking phù hợp"
              description={query ? 'Không tìm thấy booking khớp từ khoá này.' : 'Không có booking trong nhóm trạng thái này.'}
              actionLabel="Đặt sân ngay"
              actionHref="/"
            />
          </div>
        )}

        {status === 'success' && (
          <div className="mt-6 grid gap-4">{bookings.map((booking) => (
            <article key={booking.id} className="grid overflow-hidden rounded-xl border border-emerald-100 bg-white shadow-[0_10px_28px_rgba(24,65,44,0.07)] md:grid-cols-[190px_minmax(0,1fr)_180px]">
              {booking.venueImage
                ? <img className="h-44 w-full object-cover md:h-full" src={booking.venueImage} alt={booking.venueName} />
                : <div className="h-44 w-full bg-emerald-50 md:h-full" />}
              <div className="p-5">
                <div className="flex flex-wrap items-center gap-2"><BookingStatusBadge status={booking.status} /><span className="text-xs font-bold text-stone-400">{booking.bookingCode}</span></div>
                <h2 className="mt-3 text-lg font-semibold">{booking.venueName}</h2>
                <p className="mt-2 text-sm font-medium text-stone-500">{booking.courtName} · {formatBookingDate(booking.startTime)}</p>
                <p className="mt-1 text-sm font-semibold text-emerald-700">{formatBookingTime(booking.startTime)} - {formatBookingTime(booking.endTime)} · {booking.durationMinutes} phút</p>
              </div>
              <div className="flex flex-row items-center justify-between gap-3 border-t border-emerald-100 bg-emerald-50/50 p-5 md:flex-col md:items-end md:justify-center md:border-l md:border-t-0">
                <div className="md:text-right"><p className="text-xs font-medium text-stone-400">Tổng tiền</p><p className="mt-1 text-lg font-semibold text-emerald-700">{formatCurrency(booking.totalAmount)}</p></div>
                <div className="grid justify-items-end gap-2">
                  <a className="flex h-10 items-center gap-2 rounded-lg bg-emerald-700 px-4 text-xs font-semibold text-white" href={`/bookings/${booking.id}`}>Xem chi tiết<ChevronRight className="size-4" /></a>
                  {booking.canReview && (
                    <>
                      <button type="button" data-testid={`review-open-${booking.id}`} onClick={() => { setNotice(''); setReviewing(booking) }} className="flex h-10 items-center gap-1.5 whitespace-nowrap rounded-lg border border-amber-300 bg-amber-50 px-3 text-xs font-semibold text-amber-800"><Star className="size-4" />Đánh giá sân</button>
                      {booking.reviewDeadline && <span className="text-[11px] font-medium text-stone-400">Đánh giá được đến {formatBookingDate(booking.reviewDeadline)}</span>}
                    </>
                  )}
                  {booking.review && (
                    <>
                      <span data-testid={`review-done-${booking.id}`} className="inline-flex items-center gap-1 text-xs font-semibold text-amber-700"><Star className="size-4 fill-amber-400 text-amber-400" />Bạn đã đánh giá {booking.review.rating}/5</span>
                      {booking.review.status === 'visible' ? (
                        <div className="flex gap-2">
                          <button type="button" data-testid={`review-edit-${booking.id}`} onClick={() => { setNotice(''); setEditing(booking) }} className="inline-flex h-9 items-center gap-1 rounded-lg border border-emerald-200 bg-white px-3 text-xs font-semibold text-emerald-800"><Pencil className="size-3.5" />Sửa</button>
                          <button type="button" data-testid={`review-delete-${booking.id}`} onClick={() => { setNotice(''); setDeleting(booking) }} className="inline-flex h-9 items-center gap-1 rounded-lg border border-red-200 bg-white px-3 text-xs font-semibold text-red-700"><Trash2 className="size-3.5" />Xóa</button>
                        </div>
                      ) : (
                        <span data-testid={`review-locked-${booking.id}`} className="max-w-[160px] text-right text-[11px] font-medium text-stone-500">{booking.review.status === 'reported' ? 'Đánh giá đang bị báo cáo, tạm thời chưa sửa hoặc xóa được.' : 'Đánh giá đã bị quản trị viên ẩn.'}</span>
                      )}
                    </>
                  )}
                </div>
              </div>
            </article>
          ))}</div>
        )}
      </section>
      {reviewing && (
        <VenueReviewDialog
          venueName={reviewing.venueName}
          onClose={() => setReviewing(null)}
          onSubmit={async (payload) => {
            await createVenueReview(reviewing.id, payload)
            setReviewing(null)
            setNotice(`Cảm ơn bạn! Đánh giá cho ${reviewing.venueName} đã được đăng.`)
            reload()
          }}
        />
      )}
      {editing && (
        <VenueReviewDialog
          venueName={editing.venueName}
          initial={editing.review}
          onClose={() => setEditing(null)}
          onSubmit={async (payload) => {
            await updateVenueReview(editing.review.id, payload)
            setEditing(null)
            setNotice(`Đã cập nhật đánh giá cho ${editing.venueName}.`)
            reload()
          }}
        />
      )}
      {deleting && (
        <ConfirmDeleteDialog
          venueName={deleting.venueName}
          onClose={() => setDeleting(null)}
          onConfirm={async () => {
            await deleteVenueReview(deleting.review.id)
            setDeleting(null)
            setNotice(`Đã xóa đánh giá cho ${deleting.venueName}.`)
            reload()
          }}
        />
      )}
      <BottomNav activeLabel="Tài khoản" />
    </main>
  )
}

export default BookingsPage
