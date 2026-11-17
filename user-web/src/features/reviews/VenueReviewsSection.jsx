import { CheckCircle2, LoaderCircle, MessageSquareReply, Pencil, PenLine, Star, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { deleteVenueReview, createVenueReview, updateVenueReview } from '../../api/venueReviews'
import { useReviewableBookings } from '../../hooks/useReviewableBookings'
import { useVenueReviews } from '../../hooks/useVenueReviews'
import { classNames } from '../../shared/utils'
import { SectionTitle } from '../venues/HomeVenueComponents'
import { ConfirmDeleteDialog } from './ConfirmDeleteDialog'
import { formatReviewDate } from './reviewFormat'
import { StarRow } from './StarRating'
import { VenueReviewDialog } from './VenueReviewDialog'

/** Biểu đồ thanh: mỗi mức sao một hàng, bấm để lọc danh sách theo mức đó. */
function Distribution({ summary, rating, onSelect }) {
  const max = Math.max(...Object.values(summary.distribution), 1)
  return (
    <ul className="grid gap-1.5" data-testid="review-distribution">
      {[5, 4, 3, 2, 1].map((star) => (
        <li key={star}>
          <button type="button" aria-pressed={rating === star} data-testid={`review-filter-${star}`} data-count={summary.distribution[star]} onClick={() => onSelect(rating === star ? null : star)}
            className={classNames('flex w-full items-center gap-2 rounded-md px-2 py-1 text-xs font-semibold', rating === star ? 'bg-emerald-50 ring-1 ring-emerald-300' : 'hover:bg-stone-50')}>
            <span className="inline-flex w-7 items-center gap-0.5 text-stone-600">{star}<Star className="size-3 fill-amber-400 text-amber-400" /></span>
            <span className="h-2 flex-1 overflow-hidden rounded-full bg-stone-100"><span className="block h-full rounded-full bg-amber-400" style={{ width: `${(summary.distribution[star] / max) * 100}%` }} /></span>
            <span className="w-8 text-right text-stone-500">{summary.distribution[star]}</span>
          </button>
        </li>
      ))}
    </ul>
  )
}

/** Mục đánh giá ở trang chi tiết sân (#danh-gia): tổng hợp, danh sách, viết / sửa / xóa đánh giá của mình (2.3.77 - 2.3.80). */
export function VenueReviewsSection({ venue, user }) {
  const reviewState = useVenueReviews(venue?.slug ?? venue?.id)
  const reviewable = useReviewableBookings(venue?.id, Boolean(user))
  const [writing, setWriting] = useState(false)
  const [editing, setEditing] = useState(null)
  const [deleting, setDeleting] = useState(null)
  const [notice, setNotice] = useState('')
  const target = reviewable.bookings[0] ?? null
  const { summary } = reviewState

  function refresh(message) {
    setNotice(message)
    reviewState.reload()
    reviewable.reload()
  }

  return (
    <section id="danh-gia" className="mt-4 scroll-mt-24 border border-emerald-100 bg-white p-4 shadow-[0_10px_28px_rgba(34,52,40,0.06)]">
      <SectionTitle
        eyebrow="Đánh giá"
        title={summary && summary.total > 0 ? `${summary.average}/5 từ ${summary.total} người chơi` : 'Chưa có đánh giá'}
        icon={Star}
      />
      {notice && <p role="status" data-testid="review-notice" className="mt-3 flex items-center gap-2 rounded-lg bg-emerald-50 p-3 text-sm font-semibold text-emerald-800"><CheckCircle2 className="size-4 shrink-0" />{notice}</p>}
      {summary && summary.total > 0 && (
        <div className="mt-4 grid gap-4 md:grid-cols-[minmax(0,320px)_1fr] md:items-center">
          <Distribution summary={summary} rating={reviewState.rating} onSelect={reviewState.setRating} />
          <div className="flex flex-wrap items-center gap-3">
            <p className="text-4xl font-semibold text-emerald-950" data-testid="review-average">{summary.average.toLocaleString('vi-VN', { minimumFractionDigits: 1, maximumFractionDigits: 2 })}</p>
            <div><StarRow value={Math.round(summary.average)} /><p className="mt-1 text-xs font-medium text-stone-500" data-testid="review-total">{summary.total} đánh giá</p></div>
            {reviewState.rating && <button type="button" data-testid="review-filter-clear" onClick={() => reviewState.setRating(null)} className="text-xs font-semibold text-emerald-700 underline">Xem tất cả đánh giá</button>}
          </div>
        </div>
      )}
      {target && (
        <button type="button" data-testid="review-write" onClick={() => { setNotice(''); setWriting(true) }}
          className="mt-4 inline-flex h-11 items-center gap-2 rounded-lg bg-emerald-700 px-5 text-sm font-semibold text-white hover:bg-emerald-800">
          <PenLine className="size-4" />Viết đánh giá
        </button>
      )}
      <div className="mt-4 grid gap-2.5 md:grid-cols-2" data-testid="review-list">
        {reviewState.status === 'loading' && <p className="text-sm font-medium text-stone-500 md:col-span-2">Đang tải đánh giá...</p>}
        {reviewState.status === 'error' && <p className="text-sm font-medium text-red-600 md:col-span-2" role="alert">Không tải được đánh giá của sân này.</p>}
        {reviewState.status === 'empty' && (
          <p className="text-sm font-medium text-stone-500 md:col-span-2" data-testid="review-empty">
            {reviewState.rating ? `Chưa có đánh giá ${reviewState.rating} sao nào.` : 'Sân này chưa có đánh giá nào.'}
          </p>
        )}
        {reviewState.reviews.map((review) => (
          <article key={review.id} data-testid={`venue-review-${review.id}`} data-mine={review.mine} data-rating={review.rating} className={classNames('p-3.5 ring-1', review.mine ? 'bg-emerald-50/50 ring-emerald-300' : 'bg-[#fbfdf8] ring-emerald-100')}>
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="text-sm font-semibold text-emerald-950">{review.name}{review.mine && <span className="ml-2 rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-semibold text-emerald-800">Của bạn</span>}</p>
                <p className="mt-1 text-sm font-medium text-emerald-700/75">{formatReviewDate(review.createdAt)}{review.edited && <span data-testid={`review-edited-${review.id}`}> · Đã chỉnh sửa</span>}</p>
              </div>
              <span className="inline-flex items-center gap-1 text-sm font-semibold text-amber-600">
                <Star className="size-4 fill-amber-400 text-amber-400" />
                {review.rating}
              </span>
            </div>
            {review.text && <p className="mt-2.5 text-xs font-medium leading-5 text-stone-600">{review.text}</p>}
            {review.reply && (
              <div className="mt-3 rounded-lg border-l-4 border-emerald-500 bg-emerald-50/70 p-3" data-testid={`review-reply-${review.id}`}>
                <p className="flex items-center gap-1.5 text-xs font-semibold text-emerald-800"><MessageSquareReply className="size-3.5" />Phản hồi từ chủ sân · {formatReviewDate(review.reply.repliedAt)}{review.reply.edited && ' · Đã chỉnh sửa'}</p>
                <p className="mt-1.5 whitespace-pre-line text-xs font-medium leading-5 text-stone-700">{review.reply.content}</p>
              </div>
            )}
            {review.mine && (
              <div className="mt-3 flex gap-2">
                <button type="button" data-testid={`venue-review-edit-${review.id}`} onClick={() => { setNotice(''); setEditing(review) }} className="inline-flex h-9 items-center gap-1 rounded-lg border border-emerald-200 bg-white px-3 text-xs font-semibold text-emerald-800"><Pencil className="size-3.5" />Sửa</button>
                <button type="button" data-testid={`venue-review-delete-${review.id}`} onClick={() => { setNotice(''); setDeleting(review) }} className="inline-flex h-9 items-center gap-1 rounded-lg border border-red-200 bg-white px-3 text-xs font-semibold text-red-700"><Trash2 className="size-3.5" />Xóa</button>
              </div>
            )}
          </article>
        ))}
      </div>
      {reviewState.hasMore && (
        <div className="mt-4 text-center">
          <button type="button" data-testid="review-more" disabled={reviewState.loadingMore} onClick={reviewState.loadMore} className="inline-flex h-10 items-center gap-2 rounded-lg border border-emerald-200 bg-white px-5 text-sm font-semibold text-emerald-800 disabled:opacity-60">
            {reviewState.loadingMore && <LoaderCircle className="size-4 animate-spin" />}Xem thêm đánh giá
          </button>
        </div>
      )}
      {writing && target && (
        <VenueReviewDialog
          venueName={venue.name}
          onClose={() => setWriting(false)}
          onSubmit={async (payload) => {
            await createVenueReview(target.id, payload)
            setWriting(false)
            refresh('Cảm ơn bạn! Đánh giá của bạn đã được đăng.')
          }}
        />
      )}
      {editing && (
        <VenueReviewDialog
          venueName={venue.name}
          initial={{ rating: editing.rating, comment: editing.text }}
          onClose={() => setEditing(null)}
          onSubmit={async (payload) => {
            await updateVenueReview(editing.id, payload)
            setEditing(null)
            refresh('Đã cập nhật đánh giá của bạn.')
          }}
        />
      )}
      {deleting && (
        <ConfirmDeleteDialog
          venueName={venue.name}
          onClose={() => setDeleting(null)}
          onConfirm={async () => {
            await deleteVenueReview(deleting.id)
            setDeleting(null)
            refresh('Đã xóa đánh giá của bạn.')
          }}
        />
      )}
    </section>
  )
}
