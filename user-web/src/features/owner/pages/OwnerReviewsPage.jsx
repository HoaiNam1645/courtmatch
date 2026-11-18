import { useState } from 'react'
import { CheckCircle2, LoaderCircle, MessageSquareReply, Pencil } from 'lucide-react'
import { ApiError } from '../../../api/client'
import { fetchOwnerVenues } from '../../../api/ownerVenues'
import { fetchOwnerVenueReviews, replyToVenueReview, updateVenueReviewReply } from '../../../api/venueReviews'
import { useApiResource } from '../../../hooks/useApiResource'
import { FieldError, FormAlert } from '../../../shared/FormFeedback'
import { classNames } from '../../../shared/utils'
import { StarRow } from '../../reviews/StarRating'
import { OwnerLayout } from '../components/OwnerLayout'
import { OwnerEmpty, OwnerError, OwnerLoading } from '../components/OwnerUi'
import { formatDateTime } from '../ownerDates'
import { inputClass, primaryButtonClass, secondaryButtonClass, textareaClass } from '../ownerStyles'

const FILTERS = [
  { id: 'all', label: 'Tất cả' },
  { id: 'unanswered', label: 'Chưa trả lời' },
  { id: 'answered', label: 'Đã trả lời' },
]
const MAX_REPLY = 1000

/** Ô trả lời công khai dưới một đánh giá chưa có câu trả lời (2.3.81). */
function ReplyForm({ review, onReplied, editing = false, onCancel }) {
  const [content, setContent] = useState(editing ? review.reply.content : '')
  const [error, setError] = useState('')
  const [formError, setFormError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(event) {
    event.preventDefault()
    if (busy) return
    const text = content.trim()
    if (!text) return setError('Vui lòng nhập nội dung trả lời')
    if (text.length > MAX_REPLY) return setError(`Câu trả lời tối đa ${MAX_REPLY} ký tự`)
    setError('')
    setFormError('')
    setBusy(true)
    try {
      await (editing ? updateVenueReviewReply : replyToVenueReview)(review.id, text)
      onReplied(editing ? 'Đã cập nhật câu trả lời.' : undefined)
    } catch (failure) {
      if (failure instanceof ApiError) {
        setError(failure.fieldErrors?.content ?? '')
        setFormError(failure.message)
        // Đã có người/phiên khác trả lời hoặc đánh giá bị ẩn: tải lại để thấy trạng thái thật.
        if (failure.status === 409) onReplied(failure.message)
      } else {
        setFormError('Đã có lỗi xảy ra, vui lòng thử lại.')
      }
      setBusy(false)
    }
  }

  return (
    <form noValidate onSubmit={submit} className="mt-3 grid gap-2" data-testid={`reply-form-${review.id}`}>
      <label className="block">
        <span className="text-sm font-semibold text-emerald-950">{editing ? 'Sửa câu trả lời' : 'Trả lời công khai'}</span>
        <textarea className={`${textareaClass} mt-1 h-24`} aria-label="Nội dung trả lời" data-testid={`reply-text-${review.id}`} value={content} disabled={busy}
          placeholder="Cảm ơn khách và phản hồi nhận xét. Câu trả lời hiển thị công khai dưới đánh giá." onChange={(event) => { setContent(event.target.value); setError('') }} aria-invalid={Boolean(error)} />
        <span className={classNames('mt-1 block text-right text-xs font-medium', content.length > MAX_REPLY ? 'text-red-600' : 'text-stone-400')}>{content.length}/{MAX_REPLY}</span>
        <FieldError message={error} />
      </label>
      <FormAlert message={formError} />
      <div className="flex justify-end gap-2">
        {editing && <button type="button" className={secondaryButtonClass} data-testid={`reply-cancel-${review.id}`} onClick={onCancel} disabled={busy}>Hủy</button>}
        <button type="submit" className={primaryButtonClass} data-testid={`reply-submit-${review.id}`} disabled={busy}>
          {busy ? <LoaderCircle className="size-4 animate-spin" /> : <MessageSquareReply className="size-4" />}{busy ? 'Đang gửi...' : editing ? 'Lưu thay đổi' : 'Gửi trả lời'}
        </button>
      </div>
    </form>
  )
}

function ReviewCard({ review, onReplied }) {
  const [editing, setEditing] = useState(false)
  return (
    <article className="rounded-xl border border-emerald-100 bg-white p-4 shadow-[0_10px_28px_rgba(24,65,44,0.06)]" data-testid={`owner-review-${review.id}`} data-answered={review.reply ? 'true' : 'false'} data-rating={review.rating}>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <p className="text-sm font-semibold text-emerald-950">{review.reviewerName}</p>
          <p className="mt-0.5 text-xs font-medium text-stone-500">{review.venueName} · {formatDateTime(review.createdAt)}{review.edited && ' · Đã chỉnh sửa'}</p>
        </div>
        <StarRow value={review.rating} />
      </div>
      {review.comment ? <p className="mt-2 whitespace-pre-line text-sm font-medium leading-6 text-stone-700">{review.comment}</p> : <p className="mt-2 text-sm font-medium text-stone-400">Khách chỉ chấm sao, không viết nhận xét.</p>}
      {review.reply && !editing && (
        <div className="mt-3 rounded-lg border-l-4 border-emerald-500 bg-emerald-50/70 p-3" data-testid={`owner-reply-${review.id}`}>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-xs font-semibold text-emerald-800">Bạn đã trả lời · {formatDateTime(review.reply.repliedAt)}{review.reply.edited && <span data-testid={`owner-reply-edited-${review.id}`}> · Đã chỉnh sửa</span>}</p>
            <button type="button" data-testid={`reply-edit-${review.id}`} onClick={() => setEditing(true)} className="inline-flex h-8 items-center gap-1 rounded-md border border-emerald-200 bg-white px-2.5 text-xs font-semibold text-emerald-800"><Pencil className="size-3.5" />Sửa câu trả lời</button>
          </div>
          <p className="mt-1 whitespace-pre-line text-sm font-medium leading-6 text-stone-700">{review.reply.content}</p>
        </div>
      )}
      {review.reply && editing && <ReplyForm review={review} editing onCancel={() => setEditing(false)} onReplied={(message) => { setEditing(false); onReplied(message) }} />}
      {!review.reply && <ReplyForm review={review} onReplied={onReplied} />}
    </article>
  )
}

/** 2.3.81 - chủ sân xem đánh giá sân của mình và trả lời công khai. */
function OwnerReviewsPage() {
  const [filter, setFilter] = useState('all')
  const [venueId, setVenueId] = useState('')
  const [rating, setRating] = useState('')
  const [page, setPage] = useState(0)
  const [notice, setNotice] = useState('')
  const venues = useApiResource(() => fetchOwnerVenues({ size: 100 }), [])
  const { status, data, error, reload } = useApiResource(
    () => fetchOwnerVenueReviews({ venueId, filter, rating, page }),
    [venueId, filter, rating, page],
    { isEmpty: (result) => result.items.length === 0 },
  )
  const change = (setter) => (value) => { setter(value); setPage(0); setNotice('') }

  return (
    <OwnerLayout eyebrow="Đánh giá của khách" title="Đánh giá của khách" description="Đánh giá người chơi viết về sân của bạn. Trả lời công khai để cảm ơn hoặc giải thích; mỗi đánh giá chỉ có một câu trả lời.">
      <div className="grid gap-4">
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex gap-1 rounded-lg border border-emerald-100 bg-white p-1" role="group" aria-label="Lọc theo trả lời">
            {FILTERS.map((item) => (
              <button key={item.id} type="button" aria-pressed={filter === item.id} data-testid={`or-filter-${item.id}`} onClick={() => change(setFilter)(item.id)}
                className={classNames('h-9 whitespace-nowrap rounded-md px-3 text-sm font-semibold', filter === item.id ? 'bg-emerald-700 text-white' : 'text-stone-600 hover:bg-emerald-50')}>
                {item.label}{item.id === 'unanswered' && data ? ` (${data.unanswered})` : ''}
              </button>
            ))}
          </div>
          <select className={`${inputClass} sm:max-w-56`} aria-label="Lọc theo sân" data-testid="or-venue" value={venueId} onChange={(event) => change(setVenueId)(event.target.value)}>
            <option value="">Tất cả sân của tôi</option>
            {(venues.data?.venues ?? []).map((venue) => <option key={venue.id} value={venue.id}>{venue.name}</option>)}
          </select>
          <select className={`${inputClass} sm:max-w-40`} aria-label="Lọc theo số sao" data-testid="or-rating" value={rating} onChange={(event) => change(setRating)(event.target.value)}>
            <option value="">Mọi số sao</option>
            {[5, 4, 3, 2, 1].map((star) => <option key={star} value={star}>{star} sao</option>)}
          </select>
        </div>

        {notice && <p role="status" data-testid="or-notice" className="flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm font-semibold text-emerald-800"><CheckCircle2 className="size-4 shrink-0" />{notice}</p>}
        {status === 'loading' && <OwnerLoading rows={3} />}
        {status === 'error' && <OwnerError message={error?.message} onRetry={reload} />}
        {status === 'empty' && <OwnerEmpty title={filter === 'unanswered' ? 'Bạn đã trả lời hết đánh giá' : 'Chưa có đánh giá nào'} description="Đánh giá mới của người chơi sẽ xuất hiện tại đây." />}
        {status === 'success' && (
          <>
            <div className="grid gap-3" data-testid="or-list">
              {data.items.map((review) => <ReviewCard key={review.id} review={review} onReplied={(message) => { setNotice(message ?? 'Đã đăng câu trả lời công khai.'); reload() }} />)}
            </div>
            <div className="flex items-center justify-between gap-3">
              <button type="button" className={secondaryButtonClass} disabled={page === 0} onClick={() => setPage(page - 1)}>Trang trước</button>
              <p className="text-sm font-semibold text-stone-600" data-testid="or-pager">Trang {data.page + 1}/{data.totalPages} · {data.totalElements} đánh giá</p>
              <button type="button" className={secondaryButtonClass} disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Trang sau</button>
            </div>
          </>
        )}
      </div>
    </OwnerLayout>
  )
}

export default OwnerReviewsPage
