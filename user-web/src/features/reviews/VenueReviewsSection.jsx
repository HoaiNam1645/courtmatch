import { CheckCircle2, PenLine, Star } from 'lucide-react'
import { useState } from 'react'
import { createVenueReview } from '../../api/venueReviews'
import { useReviewableBookings } from '../../hooks/useReviewableBookings'
import { useVenueReviews } from '../../hooks/useVenueReviews'
import { SectionTitle } from '../venues/HomeVenueComponents'
import { formatReviewDate } from './reviewFormat'
import { VenueReviewDialog } from './VenueReviewDialog'

/** Mục đánh giá ở trang chi tiết sân (#danh-gia): danh sách đánh giá và nút viết đánh giá (2.3.77). */
export function VenueReviewsSection({ venue, user }) {
  const reviewState = useVenueReviews(venue?.slug ?? venue?.id)
  const reviewable = useReviewableBookings(venue?.id, Boolean(user))
  const [writing, setWriting] = useState(false)
  const [notice, setNotice] = useState('')
  const target = reviewable.bookings[0] ?? null

  return (
    <section id="danh-gia" className="mt-4 scroll-mt-24 border border-emerald-100 bg-white p-4 shadow-[0_10px_28px_rgba(34,52,40,0.06)]">
      <SectionTitle
        eyebrow="Đánh giá"
        title={reviewState.totalElements > 0
          ? `${venue.rating}/5 từ ${reviewState.totalElements} người chơi`
          : 'Chưa có đánh giá'}
        icon={Star}
      />
      {notice && <p role="status" data-testid="review-notice" className="mt-3 flex items-center gap-2 rounded-lg bg-emerald-50 p-3 text-sm font-semibold text-emerald-800"><CheckCircle2 className="size-4 shrink-0" />{notice}</p>}
      {target && (
        <button type="button" data-testid="review-write" onClick={() => { setNotice(''); setWriting(true) }}
          className="mt-4 inline-flex h-11 items-center gap-2 rounded-lg bg-emerald-700 px-5 text-sm font-semibold text-white hover:bg-emerald-800">
          <PenLine className="size-4" />Viết đánh giá
        </button>
      )}
      <div className="mt-4 grid gap-2.5 md:grid-cols-2">
        {reviewState.status === 'empty' && (
          <p className="text-sm font-medium text-stone-500 md:col-span-2">
            Sân này chưa có đánh giá nào.
          </p>
        )}
        {reviewState.reviews.map((review) => (
          <article key={review.id} data-testid={`venue-review-${review.id}`} className="bg-[#fbfdf8] p-3.5 ring-1 ring-emerald-100">
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="text-sm font-semibold text-emerald-950">{review.name}</p>
                <p className="mt-1 text-sm font-medium text-emerald-700/75">{formatReviewDate(review.createdAt)}</p>
              </div>
              <span className="inline-flex items-center gap-1 text-sm font-semibold text-amber-600">
                <Star className="size-4 fill-amber-400 text-amber-400" />
                {review.rating}
              </span>
            </div>
            <p className="mt-2.5 text-xs font-medium leading-5 text-stone-600">{review.text}</p>
          </article>
        ))}
      </div>
      {writing && target && (
        <VenueReviewDialog
          venueName={venue.name}
          onClose={() => setWriting(false)}
          onSubmit={async (payload) => {
            await createVenueReview(target.id, payload)
            setWriting(false)
            setNotice('Cảm ơn bạn! Đánh giá của bạn đã được đăng.')
            reviewState.reload()
            reviewable.reload()
          }}
        />
      )}
    </section>
  )
}
