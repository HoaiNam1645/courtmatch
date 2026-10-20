import { useState } from 'react'
import { ClipboardCheck, ChevronRight, CreditCard, FileText, MapPin, UserRound } from 'lucide-react'
import { createBooking as createBookingApi } from '../../../api/bookings'
import { ApiError } from '../../../api/client'
import { AppHeader, BottomNav } from '../../../app/AppLayout'
import { clearPendingBooking, readPendingBooking } from '../../../shared/session'
import { formatError } from '../../../shared/formatError'
import { classNames, formatCurrency } from '../../../shared/utils'
import { PageBackLink, ProfileEmptyState, ProfileSectionTitle } from '../../profile/components/ProfileUi'
import { BookingEmptyState } from '../components/BookingEmptyState'

function formatBookingDate(value) {
  return new Intl.DateTimeFormat('vi-VN', { weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric' }).format(new Date(value))
}

function BookingCheckoutPage({ user, onLogout }) {
  const [pending] = useState(() => readPendingBooking())
  const [note, setNote] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState('')

  // Người chơi trả trực tiếp cho chủ sân sau khi yêu cầu được chấp nhận.
  const totalAmount = Number(pending?.price ?? 0)
  const venue = pending ? { id: pending.venueId, name: pending.venueName } : null
  const court = pending ? { id: pending.courtId, name: pending.courtName } : null

  if (!user) return <ProfileEmptyState />

  /** 2.1.28 - gửi lựa chọn lên server; server tính lại giá và kiểm tra lại khung giờ. */
  async function createBooking(event) {
    event.preventDefault()
    if (submitting || !pending) return

    setSubmitting(true)
    setFormError('')
    try {
      const booking = await createBookingApi({
        courtId: pending.courtId,
        startTime: pending.startAt,
        durationMinutes: pending.durationMinutes,
        note: note.trim(),
      })
      // Tạo xong thì bỏ lựa chọn tạm để bấm back không tạo đơn thứ hai.
      clearPendingBooking()
      window.location.href = `/bookings/${booking.id}`
    } catch (error) {
      setSubmitting(false)
      if (error instanceof ApiError) {
        setFormError(error.code === 'BOOKING_SLOT_TAKEN'
          ? `${error.message} Vui lòng quay lại chọn khung giờ khác.`
          : error.message)
      } else {
        setFormError('Không tạo được đơn, vui lòng thử lại.')
      }
    }
  }

  return (
    <main className="min-h-screen bg-[linear-gradient(145deg,#eef8f2,#f9faf6)] pb-24 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="mx-auto max-w-[1100px] px-4 py-7 md:px-6 md:py-10">
        <PageBackLink href={venue ? `/san/${venue.id}#booking` : '/'} label="Quay lại chọn lịch" />
        <div className="mt-6"><p className="text-xs font-bold uppercase tracking-[0.15em] text-emerald-600">2.1.28 · Tạo yêu cầu đặt sân</p><h1 className="mt-2 text-3xl font-semibold tracking-tight">Xác nhận đặt sân</h1><p className="mt-2 text-sm font-medium text-stone-500">Sau khi gửi yêu cầu, chủ sân sẽ kiểm tra lịch trước khi mở bước thanh toán.</p></div>

        {!pending || !venue || !court ? (
          <div className="mt-7"><BookingEmptyState title="Chưa có lịch sân được chọn" description="Hãy chọn sân con, ngày và khung giờ từ trang chi tiết sân trước khi tiếp tục." actionLabel="Quay lại tìm sân" /></div>
        ) : (
          <form className="mt-7 grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_380px]" onSubmit={createBooking}>
            <div className="grid gap-6">
              <section className="overflow-hidden rounded-xl border border-emerald-100 bg-white shadow-[0_12px_34px_rgba(24,65,44,0.08)]">
                <div className="grid gap-5 p-5 sm:grid-cols-[180px_minmax(0,1fr)]">
                  <img className="h-36 w-full rounded-lg object-cover" src={venue.image} alt={venue.name} />
                  <div><div className="flex flex-wrap items-start justify-between gap-3"><div><p className="text-xs font-bold uppercase tracking-[0.12em] text-emerald-600">Sân đã chọn</p><h2 className="mt-2 text-xl font-semibold">{venue.name}</h2></div><span className="rounded-full bg-lime-100 px-3 py-1 text-xs font-bold text-emerald-800">Chờ chủ sân duyệt</span></div><p className="mt-3 flex gap-2 text-sm font-medium leading-6 text-stone-500"><MapPin className="mt-0.5 size-4 shrink-0 text-emerald-600" />{venue.address}</p><a className="mt-3 inline-flex items-center gap-2 text-xs font-semibold text-emerald-700" href={`/san/${venue.id}`}>Xem lại thông tin sân <ChevronRight className="size-4" /></a></div>
                </div>
                <div className="grid gap-px border-t border-emerald-100 bg-emerald-100 sm:grid-cols-3">{[[court.name, 'Sân con'], [formatBookingDate(`${pending.date}T12:00:00`), 'Ngày chơi'], [`${pending.startTime} - ${pending.endTime}`, `${pending.durationMinutes} phút`]].map(([value, label]) => <div key={label} className="bg-emerald-50/50 p-4"><p className="text-xs font-medium text-stone-400">{label}</p><p className="mt-1.5 text-sm font-semibold text-emerald-950">{value}</p></div>)}</div>
              </section>

              <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_12px_34px_rgba(24,65,44,0.08)]">
                <ProfileSectionTitle icon={UserRound} eyebrow="Người đặt" title="Thông tin liên hệ" />
                <div className="mt-5 grid gap-3 sm:grid-cols-2">{[['Họ và tên', user.name], ['Số điện thoại', user.phone], ['Email', user.email]].map(([label, value], index) => <div key={label} className={classNames('rounded-lg bg-stone-50 px-4 py-3 ring-1 ring-stone-100', index === 2 && 'sm:col-span-2')}><p className="text-xs font-medium text-stone-400">{label}</p><p className="mt-1 text-sm font-semibold">{value}</p></div>)}</div>
                <label className="mt-5 block"><span className="text-sm font-semibold">Ghi chú cho sân</span><textarea className="mt-2.5 min-h-24 w-full resize-none rounded-lg border border-stone-200 p-4 text-sm leading-6 outline-none focus:border-emerald-500" value={note} onChange={(event) => setNote(event.target.value)} placeholder="Ví dụ: chuẩn bị nước, thuê vợt..." maxLength={240} /></label>
              </section>

              <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_12px_34px_rgba(24,65,44,0.08)]">
                <ProfileSectionTitle icon={CreditCard} eyebrow="Thanh toán" title="Thanh toán sau khi được duyệt" />
                <div className="mt-5 rounded-xl bg-emerald-50 p-4 text-sm font-medium leading-6 text-emerald-900 ring-1 ring-emerald-100">Bạn có thể trả tiền mặt hoặc chuyển khoản trực tiếp vào tài khoản của chủ sân. Courtly không thu và không giữ tiền sân.</div>
              </section>
            </div>

            <aside className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_14px_38px_rgba(24,65,44,0.10)] lg:sticky lg:top-20">
              <ProfileSectionTitle icon={FileText} eyebrow="Đơn đặt sân" title="Chi tiết thanh toán" />
              <div className="mt-5 grid gap-3 text-sm font-medium text-stone-500"><p className="flex justify-between gap-4"><span>Tiền sân</span><span className="font-semibold text-emerald-950">{formatCurrency(pending.price)}</span></p><p className="flex justify-between gap-4"><span>Giảm giá</span><span className="font-semibold text-emerald-700">0đ</span></p></div>
              <div className="mt-5 flex items-center justify-between border-t border-emerald-100 pt-5"><span className="text-sm font-semibold">Tổng thanh toán</span><span className="text-2xl font-semibold text-emerald-700">{formatCurrency(totalAmount)}</span></div>
              <div className="mt-5 rounded-lg bg-amber-50 p-4 text-xs font-medium leading-5 text-amber-800 ring-1 ring-amber-100"><strong>Quy trình:</strong> chủ sân duyệt yêu cầu trước, sau đó bạn thanh toán trực tiếp và báo đã thanh toán để chủ sân xác nhận.</div>
              <label className="mt-5 flex items-start gap-3 text-xs font-medium leading-5 text-stone-500"><input className="mt-1 accent-emerald-600" type="checkbox" required /><span>Tôi xác nhận thông tin đặt sân và đồng ý với chính sách hủy sân.</span></label>
              {formatError(formError)}
              <button type="submit" disabled={submitting} className="mt-5 flex h-12 w-full items-center justify-center gap-2 rounded-lg bg-lime-300 text-sm font-semibold text-emerald-950 shadow-[0_10px_22px_rgba(163,230,53,0.22)] disabled:cursor-not-allowed disabled:bg-stone-200 disabled:text-stone-400 disabled:shadow-none"><ClipboardCheck className="size-4" />{submitting ? 'Đang gửi yêu cầu...' : 'Gửi yêu cầu đặt sân'}</button>
            </aside>
          </form>
        )}
      </section>
      <BottomNav activeLabel="Khám phá" />
    </main>
  )
}

export default BookingCheckoutPage
