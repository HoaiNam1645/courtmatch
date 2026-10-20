import { useState } from 'react'
import { Check, CircleAlert, CreditCard, History, FileText, MapPin, Navigation, Phone } from 'lucide-react'
import { cancelBooking as cancelBookingApi } from '../../../api/bookings'
import { AppHeader, BottomNav } from '../../../app/AppLayout'
import { useBookingDetail } from '../../../hooks/useBookingDetail'
import { useBookingPayment } from '../../../hooks/useBookingPayment'
import { classNames, formatCurrency } from '../../../shared/utils'
import { PageBackLink, ProfileEmptyState, ProfileSectionTitle } from '../../profile/components/ProfileUi'
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

function BookingDetailPage({ bookingId, user, onLogout }) {
  const { status, booking, error, notFound, setBooking, reload } = useBookingDetail(bookingId)
  const paymentLookup = useBookingPayment(bookingId, booking?.status === 'awaiting_payment')
  const [showCancelModal, setShowCancelModal] = useState(false)
  const [cancelReason, setCancelReason] = useState('Thay đổi kế hoạch cá nhân')
  const [cancelling, setCancelling] = useState(false)
  const [cancelError, setCancelError] = useState('')

  if (!user) return <ProfileEmptyState />

  if (status === 'loading') {
    return (
      <main className="min-h-screen bg-[#f4f7f1] pb-24">
        <AppHeader user={user} onLogout={onLogout} />
        <section className="mx-auto max-w-[1100px] px-4 py-10">
          <div className="grid gap-4">
            <div className="h-24 animate-pulse rounded-xl bg-emerald-100/70" />
            <div className="h-56 animate-pulse rounded-xl bg-emerald-100/50" />
          </div>
        </section>
      </main>
    )
  }

  if (status === 'error') {
    return (
      <main className="min-h-screen bg-[#f4f7f1] pb-24">
        <AppHeader user={user} onLogout={onLogout} />
        <section className="mx-auto max-w-[900px] px-4 py-10">
          <BookingEmptyState
            title={notFound ? 'Không tìm thấy booking' : 'Không tải được booking'}
            description={notFound ? 'Booking có thể đã bị xoá hoặc không thuộc về tài khoản này.' : error}
            actionLabel="Xem lịch sử đặt sân"
            actionHref="/bookings"
          />
          {!notFound && (
            <div className="mt-5 text-center">
              <button className="h-11 rounded-lg bg-emerald-700 px-6 text-sm font-semibold text-white" onClick={reload}>
                Thử lại
              </button>
            </div>
          )}
        </section>
      </main>
    )
  }

  const venue = booking.venue
  const court = booking.court
  // Server quyết định có huỷ được không, giao diện không tự suy từ trạng thái.
  const canCancel = booking.cancellable

  async function handleCancel() {
    if (cancelling) return
    setCancelling(true)
    setCancelError('')
    try {
      setBooking(await cancelBookingApi(bookingId, cancelReason))
      setShowCancelModal(false)
    } catch (submitError) {
      setCancelError(submitError?.message ?? 'Không huỷ được, vui lòng thử lại.')
    } finally {
      setCancelling(false)
    }
  }


  const directionsHref = `https://www.google.com/maps/dir/?api=1&destination=${venue.latitude},${venue.longitude}`
  return (
    <main className="min-h-screen bg-[linear-gradient(145deg,#eef8f2,#f9faf6)] pb-24 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="mx-auto max-w-[1100px] px-4 py-7 md:px-6 md:py-10">
        <PageBackLink href="/bookings" label="Về lịch sử đặt sân" />
        <div className="mt-6 flex flex-wrap items-start justify-between gap-4"><div><p className="text-xs font-bold uppercase tracking-[0.15em] text-emerald-600">2.1.29–2.1.31 · Chi tiết booking</p><h1 className="mt-2 text-3xl font-semibold">{booking.bookingCode}</h1><p className="mt-2 text-sm font-medium text-stone-500">Tạo lúc {new Date(booking.createdAt).toLocaleString('vi-VN')}</p></div><BookingStatusBadge status={booking.status} /></div>

        <div className="mt-7 grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
          <div className="grid gap-6">
            <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_12px_34px_rgba(24,65,44,0.08)]">
              <ProfileSectionTitle icon={History} eyebrow="Trạng thái" title="Tiến trình đặt sân" />
              <div className="mt-6 grid gap-0">{booking.history.map((item, index) => <div key={`${item.status}-${item.createdAt}`} className="grid grid-cols-[28px_1fr] gap-3"><div className="flex flex-col items-center"><span className="grid size-7 place-items-center rounded-full bg-emerald-700 text-white"><Check className="size-4" /></span>{index < booking.history.length - 1 && <span className="min-h-10 w-px flex-1 bg-emerald-200" />}</div><div className="pb-6"><p className="text-sm font-semibold">{item.label}</p>{item.reason && <p className="mt-1 text-xs font-medium text-stone-500">{item.reason}</p>}<p className="mt-1 text-xs font-medium text-stone-400">{new Date(item.createdAt).toLocaleString('vi-VN')}{item.bySystem && ' · hệ thống'}</p></div></div>)}</div>
            </section>

            <section className="overflow-hidden rounded-xl border border-emerald-100 bg-white shadow-[0_12px_34px_rgba(24,65,44,0.08)]">
              <img className="h-48 w-full object-cover" src={venue.image} alt={venue.name} />
              <div className="p-5"><h2 className="text-xl font-semibold">{venue.name}</h2><p className="mt-2 flex gap-2 text-sm font-medium leading-6 text-stone-500"><MapPin className="mt-0.5 size-4 shrink-0 text-emerald-600" />{venue.address}</p><div className="mt-5 grid gap-3 sm:grid-cols-3">{[[court.name, 'Sân con'], [formatBookingDate(booking.startTime), 'Ngày chơi'], [`${formatBookingTime(booking.startTime)} - ${formatBookingTime(booking.endTime)}`, `${booking.durationMinutes} phút`]].map(([value, label]) => <div key={label} className="rounded-lg bg-emerald-50/70 p-3 ring-1 ring-emerald-100"><p className="text-xs font-medium text-stone-400">{label}</p><p className="mt-1 text-sm font-semibold">{value}</p></div>)}</div><div className="mt-5 flex flex-wrap gap-3"><a className="flex h-10 items-center gap-2 rounded-lg bg-emerald-700 px-4 text-xs font-semibold text-white" href={directionsHref} target="_blank" rel="noreferrer"><Navigation className="size-4" />Chỉ đường</a><a className="flex h-10 items-center gap-2 rounded-lg border border-emerald-100 px-4 text-xs font-semibold text-emerald-700" href={`tel:${venue.phone}`}><Phone className="size-4" />Gọi sân</a></div></div>
            </section>

            {booking.note && <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_12px_34px_rgba(24,65,44,0.08)]"><ProfileSectionTitle icon={FileText} eyebrow="Ghi chú" title="Yêu cầu với sân" /><p className="mt-4 text-sm font-medium leading-6 text-stone-600">{booking.note}</p></section>}
          </div>

          <aside className="grid gap-6 lg:sticky lg:top-20">
            <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_12px_34px_rgba(24,65,44,0.08)]"><ProfileSectionTitle icon={CreditCard} eyebrow="Thanh toán" title="Chi tiết chi phí" /><div className="mt-5 grid gap-3 text-sm font-medium text-stone-500"><p className="flex justify-between"><span>Tiền sân</span><span className="font-semibold text-emerald-950">{formatCurrency(booking.price)}</span></p><p className="flex justify-between border-t border-emerald-100 pt-3"><span className="font-semibold text-emerald-950">Tổng cộng</span><span className="text-lg font-semibold text-emerald-700">{formatCurrency(booking.totalAmount)}</span></p></div>{booking.status === 'pending_owner_confirmation' && (
              <div className="mt-4 rounded-lg bg-amber-50 p-3 ring-1 ring-amber-100"><p className="text-xs font-semibold text-amber-800">Chờ chủ sân duyệt</p><p className="mt-1 text-xs font-medium leading-5 text-amber-700">Chủ sân đang kiểm tra lịch. Thông tin thanh toán sẽ xuất hiện sau khi yêu cầu được chấp nhận.</p></div>
            )}{booking.status === 'awaiting_payment' && booking.expiresAt && (
              <div className="mt-4 rounded-lg bg-amber-50 p-3 ring-1 ring-amber-100">
                <p className="text-xs font-semibold text-amber-800">Chờ thanh toán</p>
                <p className="mt-1 text-xs font-medium leading-5 text-amber-700">
                  Thanh toán trực tiếp cho chủ sân trước {new Date(booking.expiresAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}.
                </p>
                {paymentLookup.status === 'success' && paymentLookup.payment && (
                  <a className="mt-3 flex h-11 w-full items-center justify-center gap-2 rounded-lg bg-emerald-700 px-4 text-sm font-semibold text-white" href={`/payments/${paymentLookup.payment.id}`}>
                    <CreditCard className="size-4" />Xem hướng dẫn thanh toán
                  </a>
                )}
                {paymentLookup.status === 'loading' && <p className="mt-3 text-xs font-semibold text-amber-800">Đang tải hướng dẫn thanh toán...</p>}
                {paymentLookup.status === 'error' && (
                  <div className="mt-3">
                    <p className="text-xs font-medium text-red-600">{paymentLookup.error}</p>
                    <button type="button" className="mt-2 h-9 rounded-lg border border-amber-300 px-3 text-xs font-semibold text-amber-800" onClick={paymentLookup.reload}>Thử lại</button>
                  </div>
                )}
              </div>
            )}</section>
            {canCancel && <section className="rounded-xl border border-red-100 bg-white p-5"><p className="text-sm font-semibold text-red-700">Bạn cần thay đổi kế hoạch?</p><p className="mt-2 text-xs font-medium leading-5 text-stone-500">Việc hoàn tiền phụ thuộc thời điểm hủy và chính sách của sân.</p><button className="mt-4 h-11 w-full rounded-lg border border-red-200 text-sm font-semibold text-red-600" onClick={() => setShowCancelModal(true)}>Hủy đặt sân</button></section>}
            {booking.status === 'cancelled' && <section className="rounded-xl border border-red-100 bg-red-50 p-5"><p className="text-sm font-semibold text-red-700">Booking đã hủy</p><p className="mt-2 text-xs font-medium leading-5 text-red-600">Lý do: {booking.cancellationReason}</p></section>}
          </aside>
        </div>
      </section>

      {showCancelModal && <div className="fixed inset-0 z-[1200] grid place-items-center bg-emerald-950/50 p-4 backdrop-blur-sm" onMouseDown={() => setShowCancelModal(false)}><section className="w-full max-w-md rounded-xl bg-white p-6 shadow-2xl" onMouseDown={(event) => event.stopPropagation()}><span className="grid size-12 place-items-center rounded-full bg-red-50 text-red-600"><CircleAlert className="size-6" /></span><h2 className="mt-5 text-xl font-semibold">Xác nhận hủy đặt sân?</h2><p className="mt-2 text-sm font-medium leading-6 text-stone-500">Thao tác này sẽ cập nhật trạng thái booking và không thể hoàn tác.</p><label className="mt-5 block"><span className="text-sm font-semibold">Lý do hủy</span><select className="mt-2.5 h-12 w-full rounded-lg border border-stone-200 px-3 text-sm outline-none" value={cancelReason} onChange={(event) => setCancelReason(event.target.value)}><option>Thay đổi kế hoạch cá nhân</option><option>Đặt nhầm ngày hoặc giờ</option><option>Muốn chọn sân khác</option><option>Lý do khác</option></select></label>{cancelError && <p className="mt-4 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-xs font-medium text-red-700">{cancelError}</p>}<div className="mt-6 grid grid-cols-2 gap-3"><button type="button" className="h-11 rounded-lg border border-stone-200 text-sm font-semibold text-stone-600" onClick={() => setShowCancelModal(false)}>Giữ booking</button><button type="button" disabled={cancelling} className="h-11 rounded-lg bg-red-600 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60" onClick={handleCancel}>{cancelling ? 'Đang huỷ...' : 'Xác nhận hủy'}</button></div></section></div>}
      <BottomNav activeLabel="Tài khoản" />
    </main>
  )
}

export default BookingDetailPage
