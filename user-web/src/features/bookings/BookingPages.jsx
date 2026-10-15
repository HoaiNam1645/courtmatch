import { useEffect, useState } from 'react'
import { CalendarDays, Check, CheckCircle2, ClipboardCheck, ChevronRight, Clock3, Copy, CircleX, CircleAlert, CreditCard, History, Home, FileText, Landmark, MapPin, Navigation, Phone, Plus, ReceiptText, RefreshCw, Search, ShieldCheck, X, UserRound } from 'lucide-react'
import { cancelBooking as cancelBookingApi, createBooking as createBookingApi } from '../../api/bookings'
import { reportPayment as reportPaymentApi } from '../../api/payments'
import { AppHeader, BottomNav } from '../../app/AppLayout'
import { useBookingDetail } from '../../hooks/useBookingDetail'
import { useBookingPayment } from '../../hooks/useBookingPayment'
import { useBookings } from '../../hooks/useBookings'
import { usePayment } from '../../hooks/usePayment'
import { readPendingBooking } from '../../shared/session'
import { classNames, formatCurrency } from '../../shared/utils'
import { PageBackLink, ProfileEmptyState, ProfileSectionTitle } from '../profile/ProfilePages'

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

function BookingEmptyState({ title, description, actionLabel = 'Tìm sân ngay', actionHref = '/' }) {
  return (
    <div className="rounded-xl border border-emerald-100 bg-white px-6 py-12 text-center shadow-[0_12px_34px_rgba(24,65,44,0.08)]">
      <span className="mx-auto grid size-16 place-items-center rounded-full bg-emerald-50 text-emerald-700"><CalendarDays className="size-8" /></span>
      <h2 className="mt-5 text-xl font-semibold text-emerald-950">{title}</h2>
      <p className="mx-auto mt-2 max-w-md text-sm font-medium leading-6 text-stone-500">{description}</p>
      <a className="mt-6 inline-flex h-11 items-center justify-center rounded-lg bg-emerald-700 px-6 text-sm font-semibold text-white" href={actionHref}>{actionLabel}</a>
    </div>
  )
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
      window.sessionStorage.removeItem(PENDING_BOOKING_STORAGE_KEY)
      window.sessionStorage.removeItem(LEGACY_STORAGE_KEYS.pendingBooking)
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

const paymentStatusMeta = {
  awaiting_payment: { label: 'Chờ bạn thanh toán', className: 'bg-amber-100 text-amber-700', icon: Clock3 },
  payer_reported: { label: 'Chờ chủ sân xác nhận', className: 'bg-sky-100 text-sky-700', icon: Clock3 },
  confirmed: { label: 'Chủ sân đã xác nhận', className: 'bg-emerald-100 text-emerald-700', icon: CheckCircle2 },
  rejected: { label: 'Chưa nhận được tiền', className: 'bg-red-100 text-red-700', icon: CircleX },
  expired: { label: 'Giao dịch đã hết hạn', className: 'bg-stone-200 text-stone-600', icon: Clock3 },
}

function PaymentStatusBadge({ status }) {
  const meta = paymentStatusMeta[status] ?? paymentStatusMeta.awaiting_payment
  const Icon = meta.icon
  return <span className={classNames('inline-flex items-center gap-2 rounded-full px-3 py-1.5 text-xs font-bold', meta.className)}><Icon className="size-3.5" />{meta.label}</span>
}

function PaymentPage({ paymentId, user, onLogout }) {
  const { status, payment, error, notFound, setPayment, reload } = usePayment(paymentId)
  const { booking } = useBookingDetail(payment?.bookingId)
  const [copied, setCopied] = useState('')
  const [reporting, setReporting] = useState(false)
  const [reportError, setReportError] = useState('')
  const [method, setMethod] = useState(payment?.method ?? 'owner_bank_transfer')
  const [proofUrl, setProofUrl] = useState('')
  // Server trả secondsRemaining mỗi lần hỏi lại; giữa hai lần hỏi thì tự đếm xuống
  // cho mượt, nhưng mốc hết hạn luôn là của server chứ không phải đồng hồ máy.
  const [secondsLeft, setSecondsLeft] = useState(0)

  useEffect(() => {
    if (!payment) return
    // oxlint-disable-next-line react/set-state-in-effect
    setSecondsLeft(payment.secondsRemaining)
  }, [payment])

  useEffect(() => {
    if (payment?.status !== 'awaiting_payment' && payment?.status !== 'rejected') return undefined
    const timer = window.setInterval(() => setSecondsLeft((value) => Math.max(0, value - 1)), 1000)
    return () => window.clearInterval(timer)
  }, [payment?.status])

  // Chỉ chuyển sang màn xác nhận sau khi chủ sân xác nhận đã nhận tiền.
  useEffect(() => {
    if (payment?.status === 'confirmed' && payment.bookingId) {
      window.location.href = `/booking/success/${payment.bookingId}`
    }
  }, [payment?.status, payment?.bookingId])

  if (!user) return <ProfileEmptyState />

  if (status === 'loading') {
    return (
      <main className="min-h-screen bg-[#f4f7f1] pb-24">
        <AppHeader user={user} onLogout={onLogout} />
        <section className="mx-auto max-w-[1100px] px-4 py-10"><div className="h-80 animate-pulse rounded-2xl bg-emerald-100/60" /></section>
      </main>
    )
  }

  if (status === 'error' || !payment) {
    return (
      <main className="min-h-screen bg-[#f4f7f1] pb-24">
        <AppHeader user={user} onLogout={onLogout} />
        <section className="mx-auto max-w-[900px] px-4 py-10">
          <BookingEmptyState
            title={notFound ? 'Không tìm thấy giao dịch' : 'Không tải được giao dịch'}
            description={notFound ? 'Giao dịch có thể không tồn tại hoặc không thuộc về bạn.' : error}
            actionLabel="Xem booking của tôi"
            actionHref="/bookings" />
        </section>
      </main>
    )
  }

  async function handleReportPaid() {
    setReporting(true)
    setReportError('')
    try {
      setPayment(await reportPaymentApi(payment.id, { method, proofUrl }))
    } catch (reportFailure) {
      setReportError(reportFailure?.message ?? 'Không gửi được xác nhận thanh toán.')
    } finally {
      setReporting(false)
    }
  }

  async function copyValue(key, value) {
    try {
      await window.navigator.clipboard.writeText(value)
      setCopied(key)
      window.setTimeout(() => setCopied(''), 1600)
    } catch {
      setCopied('')
    }
  }

  const statusMeta = paymentStatusMeta[payment.status] ?? paymentStatusMeta.awaiting_payment
  const StatusIcon = statusMeta.icon
  const isPending = payment.status === 'awaiting_payment' || payment.status === 'rejected'
  const countdown = `${String(Math.floor(secondsLeft / 60)).padStart(2, '0')}:${String(secondsLeft % 60).padStart(2, '0')}`
  const venueName = booking?.venue?.name ?? ''

  return (
    <main className="min-h-screen bg-[radial-gradient(circle_at_top_left,#dff5e8_0,transparent_34%),linear-gradient(145deg,#eef8f2,#f9faf6)] pb-24 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="mx-auto max-w-[1100px] px-4 py-7 md:px-6 md:py-10">
        <PageBackLink href={`/bookings/${payment.bookingId}`} label="Về chi tiết booking" />
        <div className="mt-6 flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.15em] text-emerald-600">Thanh toán trực tiếp</p>
            <h1 className="mt-2 text-3xl font-semibold tracking-tight">Thanh toán cho chủ sân</h1>
            <p className="mt-2 text-sm font-medium text-stone-500">Mã booking {payment.bookingCode}{venueName ? ` · ${venueName}` : ''}</p>
          </div>
          <PaymentStatusBadge status={payment.status} />
        </div>

        <div className="mt-7 grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_390px]">
          <div className="grid gap-6">
            {isPending ? (
              <section className="overflow-hidden rounded-2xl border border-emerald-100 bg-white shadow-[0_18px_46px_rgba(24,65,44,0.10)]">
                <div className="border-b border-emerald-100 bg-emerald-950 px-5 py-4 text-white sm:px-6"><div className="flex items-center justify-between gap-4"><div><p className="text-xs font-bold uppercase tracking-[0.14em] text-lime-300">Thanh toán trực tiếp cho chủ sân</p><p className="mt-1 text-sm font-medium text-emerald-100">Chọn tiền mặt hoặc chuyển khoản rồi báo đã thanh toán</p></div><span className="rounded-lg bg-white/10 px-3 py-2 font-mono text-lg font-bold text-lime-300">{countdown}</span></div></div>
                <div className="grid gap-7 p-5 sm:grid-cols-[220px_minmax(0,1fr)] sm:p-7">
                  <div className="grid content-start gap-3">
                    <button type="button" className={classNames('rounded-xl border p-4 text-left', method === 'owner_bank_transfer' ? 'border-emerald-500 bg-emerald-50' : 'border-stone-200')} onClick={() => setMethod('owner_bank_transfer')}><Landmark className="size-5 text-emerald-700" /><p className="mt-2 text-sm font-semibold">Chuyển khoản</p><p className="mt-1 text-xs font-medium text-stone-500">Chuyển vào tài khoản chủ sân.</p></button>
                    <button type="button" className={classNames('rounded-xl border p-4 text-left', method === 'cash' ? 'border-emerald-500 bg-emerald-50' : 'border-stone-200')} onClick={() => setMethod('cash')}><CreditCard className="size-5 text-emerald-700" /><p className="mt-2 text-sm font-semibold">Tiền mặt</p><p className="mt-1 text-xs font-medium text-stone-500">Thanh toán trực tiếp tại sân.</p></button>
                  </div>
                  <div className="flex flex-col justify-center"><span className="grid size-12 place-items-center rounded-xl bg-emerald-50 text-emerald-700"><Landmark className="size-6" /></span><h2 className="mt-4 text-xl font-semibold">{method === 'cash' ? 'Thanh toán tiền mặt' : 'Chuyển khoản ngân hàng'}</h2><p className="mt-2 text-sm font-medium leading-6 text-stone-500">Courtly không nhận hoặc giữ khoản tiền này. Chủ sân sẽ xác nhận sau khi thực tế nhận được tiền.</p>{method === 'owner_bank_transfer' && <div className="mt-5 grid gap-3">{[
                    ['Ngân hàng', payment.bankName, 'bank'],
                    ['Số tài khoản', payment.bankAccountNo, 'account'],
                    ['Tên tài khoản', payment.bankAccountName, 'name'],
                    ['Nội dung', payment.referenceContent, 'content'],
                  ].map(([label, value, key]) => <div key={key} className="flex items-center justify-between gap-3 rounded-lg bg-stone-50 px-4 py-3 ring-1 ring-stone-100"><div><p className="text-[11px] font-medium text-stone-400">{label}</p><p className="mt-1 text-sm font-bold text-emerald-950">{value || 'Chưa cập nhật'}</p></div>{key !== 'bank' && key !== 'name' && value && <button type="button" className="grid size-9 shrink-0 place-items-center rounded-lg bg-white text-emerald-700 shadow-sm ring-1 ring-emerald-100" onClick={() => copyValue(key, String(value))} aria-label={`Sao chép ${label}`}>{copied === key ? <ClipboardCheck className="size-4" /> : <Copy className="size-4" />}</button>}</div>)}</div>}<label className="mt-5 block"><span className="text-xs font-semibold text-stone-600">Link ảnh bằng chứng (không bắt buộc)</span><input className="mt-2 h-11 w-full rounded-lg border border-stone-200 px-3 text-sm outline-none focus:border-emerald-500" value={proofUrl} onChange={(event) => setProofUrl(event.target.value)} placeholder="https://..." /></label>{reportError && <p className="mt-3 text-xs font-semibold text-red-600">{reportError}</p>}<button type="button" disabled={reporting} className="mt-4 h-11 rounded-lg bg-emerald-700 px-5 text-sm font-semibold text-white disabled:opacity-50" onClick={handleReportPaid}>{reporting ? 'Đang gửi...' : 'Tôi đã thanh toán'}</button></div>
                </div>
              </section>
            ) : (
              <section className={classNames('rounded-2xl border bg-white p-7 text-center shadow-[0_18px_46px_rgba(24,65,44,0.10)]', payment.status === 'rejected' ? 'border-red-100' : payment.status === 'confirmed' ? 'border-emerald-100' : 'border-stone-200')}>
                <span className={classNames('mx-auto grid size-20 place-items-center rounded-full', statusMeta.className)}><StatusIcon className="size-10" /></span>
                <h2 className="mt-5 text-2xl font-semibold">{statusMeta.label}</h2>
                <p className="mx-auto mt-2 max-w-lg text-sm font-medium leading-6 text-stone-500">{payment.status === 'rejected' ? `Chủ sân chưa xác nhận được khoản tiền${payment.rejectionReason ? `: ${payment.rejectionReason}` : '.'}` : payment.status === 'expired' ? 'Thời hạn thanh toán đã kết thúc. Vui lòng liên hệ chủ sân hoặc chọn lịch khác.' : payment.status === 'payer_reported' ? 'Thông tin đã được gửi. Booking chỉ được xác nhận sau khi chủ sân kiểm tra và xác nhận đã nhận tiền.' : 'Chủ sân đã xác nhận nhận tiền và lịch sân của bạn đã được xác nhận.'}</p>
                <div className="mt-6 flex flex-wrap justify-center gap-3">
                  {payment.status === 'payer_reported' && <button className="flex h-11 items-center gap-2 rounded-lg border border-emerald-200 px-5 text-sm font-semibold text-emerald-700" onClick={reload}><RefreshCw className="size-4" />Kiểm tra trạng thái</button>}
                  {booking?.venue?.slug && <a className="flex h-11 items-center rounded-lg border border-emerald-200 px-5 text-sm font-semibold text-emerald-700" href={`/san/${booking.venue.slug}#booking`}>Chọn lịch mới</a>}
                  {payment.status === 'confirmed' && <a className="flex h-11 items-center rounded-lg bg-emerald-700 px-5 text-sm font-semibold text-white" href={`/booking/success/${payment.bookingId}`}>Xem xác nhận đặt sân</a>}
                </div>
              </section>
            )}

            <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_12px_34px_rgba(24,65,44,0.07)]">
              <ProfileSectionTitle icon={ShieldCheck} eyebrow="Xác nhận hai bước" title="Sau khi bạn thanh toán" />
              <div className="mt-5 grid gap-4 sm:grid-cols-3">{[
                [ReceiptText, '1. Bạn thanh toán', 'Trả tiền mặt hoặc chuyển khoản cho chủ sân.'],
                [ClipboardCheck, '2. Bạn báo đã trả', 'Gửi trạng thái và bằng chứng nếu có.'],
                [CheckCircle2, '3. Chủ sân xác nhận', 'Booking chuyển sang đã xác nhận.'],
              ].map(([Icon, title, description]) => <div key={title} className="rounded-xl bg-emerald-50/60 p-4 ring-1 ring-emerald-100"><Icon className="size-5 text-emerald-700" /><p className="mt-3 text-sm font-semibold">{title}</p><p className="mt-1 text-xs font-medium leading-5 text-stone-500">{description}</p></div>)}</div>
            </section>
          </div>

          <aside className="grid gap-5 lg:sticky lg:top-20">
            <section className="rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_14px_38px_rgba(24,65,44,0.10)]">
              <ProfileSectionTitle icon={CreditCard} eyebrow="Giao dịch" title="Thông tin thanh toán" />
              <div className="mt-5 grid gap-3 text-sm font-medium text-stone-500">
                <p className="flex justify-between gap-4"><span>Mã booking</span><span className="font-semibold text-emerald-950">{payment.bookingCode}</span></p>
                {venueName && <p className="flex justify-between gap-4"><span>Sân</span><span className="max-w-[210px] text-right font-semibold text-emerald-950">{venueName}</span></p>}
                {booking?.startTime && <p className="flex justify-between gap-4"><span>Khung giờ</span><span className="text-right font-semibold text-emerald-950">{formatBookingTime(booking.startTime)} - {formatBookingTime(booking.endTime)}</span></p>}
                <p className="flex justify-between gap-4"><span>Phương thức</span><span className="font-semibold text-emerald-950">{payment.method === 'cash' ? 'Tiền mặt' : 'Chuyển khoản chủ sân'}</span></p>
              </div>
              <div className="mt-5 flex items-center justify-between border-t border-emerald-100 pt-5"><span className="text-sm font-semibold">Cần thanh toán</span><span className="text-2xl font-semibold text-emerald-700">{formatCurrency(payment.amount)}</span></div>
            </section>
            {isPending && (
              <section className="rounded-xl border border-amber-200 bg-amber-50 p-5">
                <p className="text-sm font-semibold text-amber-900">Lưu ý khi chuyển khoản</p>
                <p className="mt-2 text-xs font-medium leading-5 text-amber-800">Chuyển đúng số tiền và nội dung. Sau đó bấm “Tôi đã thanh toán”; chủ sân sẽ kiểm tra thực tế.</p>
              </section>
            )}
          </aside>
        </div>
      </section>
      <BottomNav activeLabel="Tài khoản" />
    </main>
  )
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

function BookingsPage({ user, onLogout }) {
  const [activeTab, setActiveTab] = useState('upcoming')
  const [query, setQuery] = useState('')

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
                <a className="flex h-10 items-center gap-2 rounded-lg bg-emerald-700 px-4 text-xs font-semibold text-white" href={`/bookings/${booking.id}`}>Xem chi tiết<ChevronRight className="size-4" /></a>
              </div>
            </article>
          ))}</div>
        )}
      </section>
      <BottomNav activeLabel="Tài khoản" />
    </main>
  )
}

export { BookingCheckoutPage, BookingDetailPage, BookingEmptyState, BookingsPage, BookingSuccessPage, PaymentPage }
