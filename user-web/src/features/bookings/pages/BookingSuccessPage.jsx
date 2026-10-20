import { Check, Home, ReceiptText } from 'lucide-react'
import { AppHeader, BottomNav } from '../../../app/AppLayout'
import { useBookingDetail } from '../../../hooks/useBookingDetail'
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

function BookingSuccessPage({ bookingId, user, onLogout }) {
  const { status, booking } = useBookingDetail(bookingId)

  if (!user) return <ProfileEmptyState />
  if (status === 'loading') {
    return (
      <main className="min-h-screen bg-[#f4f7f1] pb-24">
        <AppHeader user={user} onLogout={onLogout} />
        <section className="mx-auto max-w-[820px] px-4 py-10"><div className="h-64 animate-pulse rounded-2xl bg-emerald-100/60" /></section>
      </main>
    )
  }
  if (status === 'error') {
    return <main className="min-h-screen bg-[#f4f7f1] pb-24"><AppHeader user={user} onLogout={onLogout} /><section className="mx-auto max-w-[900px] px-4 py-10"><BookingEmptyState title="Không tìm thấy booking" description="Không thể hiển thị xác nhận cho booking này." actionLabel="Xem booking của tôi" actionHref="/bookings" /></section></main>
  }

  if (booking.status !== 'confirmed') {
    return (
      <main className="min-h-screen bg-[#f4f7f1] pb-24">
        <AppHeader user={user} onLogout={onLogout} />
        <section className="mx-auto max-w-[900px] px-4 py-10">
          <BookingEmptyState
            title="Booking chưa được xác nhận"
            description={booking.status === 'pending_owner_confirmation'
              ? 'Yêu cầu đang chờ chủ sân kiểm tra lịch.'
              : booking.status === 'awaiting_payment' ? 'Booking đang chờ bạn thanh toán trực tiếp cho chủ sân.' : 'Booking này không ở trạng thái đã xác nhận.'}
            actionLabel="Xem chi tiết booking"
            actionHref={`/bookings/${booking.id}`}
          />
        </section>
      </main>
    )
  }

  const venue = booking.venue
  const court = booking.court

  return (
    <main className="min-h-screen bg-[radial-gradient(circle_at_top,#d9f99d_0,transparent_26%),linear-gradient(145deg,#eaf7ef,#fbfcf8)] pb-24 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="mx-auto max-w-[820px] px-4 py-8 md:px-6 md:py-12">
        <PageBackLink href="/bookings" label="Về danh sách booking" />
        <section className="mt-7 overflow-hidden rounded-2xl border border-emerald-100 bg-white shadow-[0_24px_70px_rgba(24,65,44,0.13)]">
          <div className="bg-emerald-950 px-6 py-9 text-center text-white sm:px-10"><span className="mx-auto grid size-20 place-items-center rounded-full bg-lime-300 text-emerald-950 shadow-[0_0_0_10px_rgba(190,242,100,0.12)]"><Check className="size-10" strokeWidth={3} /></span><p className="mt-6 text-xs font-bold uppercase tracking-[0.18em] text-lime-300">Đặt sân thành công</p><h1 className="mt-2 text-3xl font-semibold">Lịch sân của bạn đã được xác nhận</h1><p className="mx-auto mt-3 max-w-xl text-sm font-medium leading-6 text-emerald-100">Chủ sân đã xác nhận nhận tiền. Courtly lưu trạng thái booking và thông tin đối soát.</p></div>
          <div className="p-5 sm:p-8"><div className="flex flex-wrap items-start justify-between gap-4"><div><p className="text-xs font-medium text-stone-400">Mã booking</p><p className="mt-1 text-2xl font-semibold text-emerald-700">{booking.bookingCode}</p></div><BookingStatusBadge status={booking.status} /></div><div className="mt-6 grid gap-4 rounded-xl bg-emerald-50/70 p-5 ring-1 ring-emerald-100 sm:grid-cols-2"><div className="sm:col-span-2"><p className="text-xs font-medium text-stone-400">Địa điểm</p><p className="mt-1 text-lg font-semibold">{venue.name}</p><p className="mt-1 text-sm font-medium text-stone-500">{venue.address}</p></div>{[[court.name, 'Sân con'], [formatBookingDate(booking.startTime), 'Ngày chơi'], [`${formatBookingTime(booking.startTime)} - ${formatBookingTime(booking.endTime)}`, 'Khung giờ'], [formatCurrency(booking.totalAmount), 'Đã thanh toán']].map(([value, label]) => <div key={label} className="rounded-lg bg-white p-3.5 ring-1 ring-emerald-100"><p className="text-[11px] font-medium text-stone-400">{label}</p><p className="mt-1 text-sm font-semibold">{value}</p></div>)}</div><div className="mt-6 grid gap-3 sm:grid-cols-2"><a className="flex h-12 items-center justify-center gap-2 rounded-lg bg-emerald-700 text-sm font-semibold text-white" href={`/bookings/${booking.id}`}><ReceiptText className="size-4" />Xem chi tiết booking</a><a className="flex h-12 items-center justify-center gap-2 rounded-lg border border-emerald-200 text-sm font-semibold text-emerald-700" href="/"><Home className="size-4" />Về trang chủ</a></div></div>
        </section>
      </section>
      <BottomNav activeLabel="Tài khoản" />
    </main>
  )
}

export default BookingSuccessPage
