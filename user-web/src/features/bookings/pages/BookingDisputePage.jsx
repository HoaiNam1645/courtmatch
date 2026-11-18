import { CheckCircle2, LoaderCircle } from 'lucide-react'
import { useState } from 'react'
import { ApiError } from '../../../api/client'
import { createBookingDispute, fetchDisputeCategories } from '../../../api/bookingDisputes'
import { AppHeader, BottomNav } from '../../../app/AppLayout'
import { useApiResource } from '../../../hooks/useApiResource'
import { useBookingDetail } from '../../../hooks/useBookingDetail'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { classNames } from '../../../shared/utils'
import { PageBackLink, ProfileEmptyState } from '../../profile/components/ProfileUi'
import { BookingEmptyState } from '../components/BookingEmptyState'

const MAX_DESCRIPTION = 2000

/** 2.3.85 - 2.3.86: biểu mẫu báo cáo vấn đề về một đơn đặt sân, chọn loại trong danh mục. */
function BookingDisputePage({ bookingId, user, onLogout }) {
  const detail = useBookingDetail(bookingId)
  const categories = useApiResource(fetchDisputeCategories, [])
  const [category, setCategory] = useState('')
  const [description, setDescription] = useState('')
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [busy, setBusy] = useState(false)
  const [created, setCreated] = useState(null)

  if (!user) return <ProfileEmptyState />

  const booking = detail.booking
  const selected = (categories.data ?? []).find((item) => item.code === category)

  async function submit(event) {
    event.preventDefault()
    if (busy) return
    const text = description.trim()
    if (!selected) return setErrors({ category: 'Vui lòng chọn loại báo cáo' })
    if (text.length < selected.minDescriptionLength) return setErrors({ description: `Vui lòng mô tả rõ hơn (ít nhất ${selected.minDescriptionLength} ký tự)` })
    if (text.length > MAX_DESCRIPTION) return setErrors({ description: `Mô tả tối đa ${MAX_DESCRIPTION} ký tự` })
    setErrors({})
    setFormError('')
    setBusy(true)
    try {
      setCreated(await createBookingDispute(bookingId, { category, description: text }))
    } catch (failure) {
      if (failure instanceof ApiError) {
        setErrors(failure.fieldErrors ?? {})
        setFormError(failure.message)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
      setBusy(false)
    }
  }

  const shell = (children) => (
    <main className="min-h-screen bg-[#f4f7f1] pb-24 text-emerald-950">
      <AppHeader user={user} onLogout={onLogout} />
      <section className="mx-auto max-w-[760px] px-4 py-7 md:px-6 md:py-10">{children}</section>
      <BottomNav activeLabel="Tài khoản" />
    </main>
  )

  if (detail.status === 'loading') return shell(<div className="h-48 animate-pulse rounded-xl bg-emerald-100/60" aria-busy="true" />)
  if (detail.status === 'error') {
    return shell(<BookingEmptyState title={detail.notFound ? 'Không tìm thấy booking' : 'Không tải được booking'} description={detail.notFound ? 'Booking có thể đã bị xoá hoặc không thuộc về tài khoản này.' : detail.error} actionLabel="Xem lịch sử đặt sân" actionHref="/bookings" />)
  }
  if (created) {
    return shell(
      <div className="rounded-xl border border-emerald-200 bg-white p-6 text-center shadow-[0_12px_34px_rgba(24,65,44,0.08)]" data-testid="dispute-done">
        <CheckCircle2 className="mx-auto size-12 text-emerald-600" />
        <h1 className="mt-4 text-2xl font-semibold">Đã gửi báo cáo</h1>
        <p className="mt-2 text-sm font-medium leading-6 text-stone-600">Báo cáo “{created.categoryLabel}” về đơn {created.bookingCode} đã được gửi tới chủ sân.</p>
        <p className="mt-1 text-sm font-semibold text-amber-800" data-testid="dispute-deadline">Chủ sân cần giải trình trước {new Date(created.ownerResponseDeadline).toLocaleString('vi-VN')}.</p>
        <a className="mt-6 inline-flex h-11 items-center rounded-lg bg-emerald-700 px-6 text-sm font-semibold text-white" href={`/bookings/${bookingId}`} data-testid="dispute-back">Về chi tiết đơn</a>
      </div>,
    )
  }
  if (!booking.canReportIssue) {
    return shell(
      <>
        <PageBackLink href={`/bookings/${bookingId}`} label="Về chi tiết đơn" />
        <div className="mt-6" data-testid="dispute-unavailable">
          <BookingEmptyState
            title={booking.dispute ? 'Đơn này đã có báo cáo đang xử lý' : 'Không thể báo cáo đơn này'}
            description={booking.dispute ? 'Bạn có thể theo dõi tiến trình ở chi tiết đơn.' : 'Chỉ báo cáo được đơn đã xác nhận hoặc đã hoàn thành, trong thời hạn cho phép.'}
            actionLabel="Về chi tiết đơn"
            actionHref={`/bookings/${bookingId}`}
          />
        </div>
      </>,
    )
  }

  return shell(
    <>
      <PageBackLink href={`/bookings/${bookingId}`} label="Về chi tiết đơn" />
      <p className="mt-6 text-xs font-bold uppercase tracking-[0.15em] text-emerald-600">2.3.85 · Báo cáo về đơn đặt sân</p>
      <h1 className="mt-2 text-3xl font-semibold">Báo cáo vấn đề</h1>
      <p className="mt-2 text-sm font-medium text-stone-500" data-testid="dispute-booking">{booking.venue.name} · đơn {booking.bookingCode}</p>

      <form noValidate onSubmit={submit} className="mt-6 grid gap-5 rounded-xl border border-emerald-100 bg-white p-5 shadow-[0_12px_34px_rgba(24,65,44,0.08)]" data-testid="dispute-form">
        <fieldset className="grid gap-2" disabled={busy}>
          <legend className="text-sm font-semibold">Loại báo cáo</legend>
          {categories.status === 'loading' && <div className="h-24 animate-pulse rounded-lg bg-emerald-100/60" />}
          {categories.status === 'error' && <p className="text-sm font-medium text-red-600" role="alert">{categories.error?.message ?? 'Không tải được danh mục loại báo cáo.'}</p>}
          {(categories.data ?? []).map((item) => (
            <label key={item.code} className={classNames('flex cursor-pointer items-start gap-3 rounded-lg border p-3', category === item.code ? 'border-emerald-600 bg-emerald-50/60' : 'border-stone-200')}>
              <input type="radio" name="dispute-category" value={item.code} checked={category === item.code} data-testid={`dispute-category-${item.code}`} className="mt-1" onChange={() => { setCategory(item.code); setErrors({}) }} />
              <span><span className="block text-sm font-semibold">{item.label}</span><span className="block text-xs font-medium text-stone-500">{item.hint}</span></span>
            </label>
          ))}
          <FieldError message={errors.category} />
        </fieldset>

        <label className="block">
          <span className="text-sm font-semibold">Mô tả vấn đề <span className="text-red-600">*</span></span>
          <textarea className="mt-2 h-40 w-full min-w-0 rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm outline-none focus:border-emerald-700" aria-label="Mô tả vấn đề" data-testid="dispute-description" value={description} disabled={busy}
            placeholder={selected ? selected.hint : 'Chọn loại báo cáo rồi mô tả chi tiết điều đã xảy ra.'} onChange={(event) => { setDescription(event.target.value); setErrors((current) => ({ ...current, description: undefined })) }} aria-invalid={Boolean(errors.description)} />
          <span className={classNames('mt-1 block text-right text-xs font-medium', description.length > MAX_DESCRIPTION ? 'text-red-600' : 'text-stone-400')} data-testid="dispute-counter">{description.length}/{MAX_DESCRIPTION}{selected ? ` · tối thiểu ${selected.minDescriptionLength}` : ''}</span>
          <FieldError message={errors.description} />
        </label>

        <FormAlert message={formError} />
        <p className="text-xs font-medium leading-5 text-stone-500">Chủ sân có 48 giờ để giải trình. Báo cáo này chưa yêu cầu hoàn tiền và chưa thay đổi trạng thái đơn.</p>
        <button type="submit" disabled={busy || !categories.data} data-testid="dispute-submit" className="flex h-12 items-center justify-center gap-2 rounded-lg bg-emerald-700 text-sm font-semibold text-white disabled:opacity-60">
          {busy && <LoaderCircle className="size-4 animate-spin" />}{busy ? 'Đang gửi...' : 'Gửi báo cáo'}
        </button>
      </form>
    </>,
  )
}

export default BookingDisputePage
