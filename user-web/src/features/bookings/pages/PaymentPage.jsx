import { useEffect, useState } from 'react'
import { CheckCircle2, ClipboardCheck, Clock3, Copy, CircleX, CreditCard, Landmark, ReceiptText, RefreshCw, ShieldCheck } from 'lucide-react'
import { reportPayment as reportPaymentApi } from '../../../api/payments'
import { AppHeader, BottomNav } from '../../../app/AppLayout'
import { useBookingDetail } from '../../../hooks/useBookingDetail'
import { usePayment } from '../../../hooks/usePayment'
import { classNames, formatCurrency } from '../../../shared/utils'
import { PageBackLink, ProfileEmptyState, ProfileSectionTitle } from '../../profile/components/ProfileUi'

import { BookingEmptyState } from '../components/BookingEmptyState'

function formatBookingTime(value) {
  return new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value))
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



export default PaymentPage
